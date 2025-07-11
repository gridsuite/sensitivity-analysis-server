/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.NumberExpertRule;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.gridsuite.sensitivityanalysis.server.dto.FilterEquipments;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityFactorsIdsByGroup;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@SpringBootTest
class FilterServiceTest {

    private static final int DATA_BUFFER_LIMIT = 256 * 1024; // AbstractJackson2Decoder.maxInMemorySize

    private static final String NETWORK_UUID = "7928181c-7977-4592-ba19-88027e4254e4";

    private static final UUID TEST_NETWORK_ID = UUID.randomUUID();

    private static final UUID NOT_FOUND_NETWORK_ID = UUID.randomUUID();

    private static final String VARIANT_ID = "variant_id";

    private static final UUID LIST_UUID = UUID.randomUUID();

    private static final SensitivityFactorsIdsByGroup IDENTIFIABLES_UUID = SensitivityFactorsIdsByGroup.builder().ids(Map.of("0", List.of(LIST_UUID), "1", List.of(LIST_UUID), "2", List.of(LIST_UUID))).build();

    private static final UUID VERY_LARGE_LIST_UUID = UUID.randomUUID();

    private static final IdentifiableAttributes IDENTIFIABLE = new IdentifiableAttributes("gen1", IdentifiableType.GENERATOR, null);

    private static final IdentifiableAttributes IDENTIFIABLE_VARIANT = new IdentifiableAttributes("load_variant", IdentifiableType.LOAD, 10.);
    private static final Object TEST_FILTERS = List.of(createTestExpertFilter());

    private static ExpertFilter createTestExpertFilter() {
        AbstractExpertRule simpleRule = NumberExpertRule.builder()
                .value(220.0)
                .field(FieldType.NOMINAL_VOLTAGE)
                .operator(OperatorType.EQUALS)
                .build();
        return new ExpertFilter(FilterServiceTest.LIST_UUID, new Date(), EquipmentType.GENERATOR, simpleRule);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Network network;

    @Mock
    private VariantManager variantManager;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private FilterService filterService;

    @BeforeEach
    void setUp(final MockWebServer mockWebServer) throws Exception {
        filterService = new FilterService(networkStoreService, initMockWebServer(mockWebServer));
        when(networkStoreService.getNetwork(eq(NOT_FOUND_NETWORK_ID), any(PreloadingStrategy.class))).thenThrow(new PowsyblException());
        doNothing().when(variantManager).setWorkingVariant(anyString());
        when(networkStoreService.getNetwork(eq(TEST_NETWORK_ID), any(PreloadingStrategy.class))).then((Answer<Network>) invocation -> network);
    }

    private String initMockWebServer(final MockWebServer server) throws IOException {
        String jsonExpected = objectMapper.writeValueAsString(createFromIdentifiableList(LIST_UUID, List.of(IDENTIFIABLE)));
        String veryLargeJsonExpected = objectMapper.writeValueAsString(createFromIdentifiableList(VERY_LARGE_LIST_UUID, createVeryLargeList()));
        String jsonLargeFilterEquipment = objectMapper.writeValueAsString(createFilterEquipments());
        String jsonVariantExpected = objectMapper.writeValueAsString(createFromIdentifiableList(LIST_UUID, List.of(IDENTIFIABLE_VARIANT)));
        String jsonIdentifiablesExpected = objectMapper.writeValueAsString(countResultMap());
        String jsonFiltersExpected = objectMapper.writeValueAsString(TEST_FILTERS);
        final Dispatcher dispatcher = new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String requestPath = Objects.requireNonNull(request.getPath());
                if (requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s&variantId=%s", LIST_UUID, NETWORK_UUID, VARIANT_ID))) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonVariantExpected);
                } else if (requestPath.equals(String.format("/v1/filters/export?ids=%s&ids=%s&networkUuid=%s", VERY_LARGE_LIST_UUID, LIST_UUID, NETWORK_UUID))) {
                        return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonLargeFilterEquipment);
                } else if (requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s", LIST_UUID, NETWORK_UUID))) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonExpected);
                } else if (requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s&variantId=%s", VERY_LARGE_LIST_UUID, NETWORK_UUID, VARIANT_ID))
                           || requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s", VERY_LARGE_LIST_UUID, NETWORK_UUID))) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), veryLargeJsonExpected);
                } else if (requestPath.matches(String.format("/v1/filters/identifiables-count\\?networkUuid=%s&variantId=%s", NETWORK_UUID, VARIANT_ID) + "\\&ids.*")
                        || requestPath.matches(String.format("/v1/filters/identifiables-count\\?networkUuid=%s", NETWORK_UUID) + "\\&ids.*")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonIdentifiablesExpected);
                } else if (requestPath.matches("/v1/filters/metadata\\?ids=.*")) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonFiltersExpected);
                } else {
                    return new MockResponse.Builder().code(HttpStatus.NOT_FOUND.value()).body("Path not supported: " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        return baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
    }

    @NotNull
    private static Map<String, Integer> countResultMap() {
        return Map.of("0", 6, "1", 6, "2", 6);
    }

    private static List<IdentifiableAttributes> createVeryLargeList() {
        return IntStream.range(0, DATA_BUFFER_LIMIT).mapToObj(i -> new IdentifiableAttributes("l" + i, IdentifiableType.GENERATOR, null)).collect(Collectors.toList());
    }

    private static List<FilterEquipments> createFromIdentifiableList(UUID uuid, List<IdentifiableAttributes> identifiableAttributes) {
        return List.of(FilterEquipments.builder().identifiableAttributes(identifiableAttributes).filterId(uuid).build());
    }

    private static List<FilterEquipments> createFilterEquipments() {
        return List.of(
            FilterEquipments.builder()
                .identifiableAttributes(createVeryLargeList())
                .filterId(VERY_LARGE_LIST_UUID)
                .build(),
            FilterEquipments.builder()
                .identifiableAttributes(List.of(IDENTIFIABLE))
                .filterId(LIST_UUID)
                .build()
        );
    }

    @Test
    void test() throws Exception {
        List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilters(List.of(LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(List.of(IDENTIFIABLE)), objectMapper.writeValueAsString(list));
        list = filterService.getIdentifiablesFromFilters(List.of(LIST_UUID), UUID.fromString(NETWORK_UUID), VARIANT_ID);
        assertEquals(objectMapper.writeValueAsString(List.of(IDENTIFIABLE_VARIANT)), objectMapper.writeValueAsString(list));
    }

    @Test
    void testVeryLargeList() throws Exception {
        // DataBufferLimitException should not be thrown with this message : "Exceeded limit on max bytes to buffer : DATA_BUFFER_LIMIT"
        List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilters(List.of(VERY_LARGE_LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(createVeryLargeList()), objectMapper.writeValueAsString(list));
        list = filterService.getIdentifiablesFromFilters(List.of(VERY_LARGE_LIST_UUID), UUID.fromString(NETWORK_UUID), VARIANT_ID);
        assertEquals(objectMapper.writeValueAsString(createVeryLargeList()), objectMapper.writeValueAsString(list));
    }

    @Test
    void testGetMultipleLists() throws Exception {
        List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilters(List.of(VERY_LARGE_LIST_UUID, LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        List<IdentifiableAttributes> expectedList = new ArrayList<>(createVeryLargeList());
        expectedList.add(IDENTIFIABLE);
        assertEquals(objectMapper.writeValueAsString(expectedList), objectMapper.writeValueAsString(list));
    }

    @Test
    void testGetFactorsCount() throws Exception {
        Map<String, Long> list = filterService.getIdentifiablesCount(IDENTIFIABLES_UUID, UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(countResultMap()), objectMapper.writeValueAsString(list));
        list = filterService.getIdentifiablesCount(IDENTIFIABLES_UUID, UUID.fromString(NETWORK_UUID), VARIANT_ID);
        assertEquals(objectMapper.writeValueAsString(countResultMap()), objectMapper.writeValueAsString(list));
    }

    @Test
    void testListOfUuids() throws Exception {
        // DataBufferLimitException should not be thrown with this message : "Exceeded limit on max bytes to buffer : DATA_BUFFER_LIMIT"
        List<FilterEquipments> list = filterService.getFilterEquipments(List.of(VERY_LARGE_LIST_UUID, LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(createFilterEquipments()), objectMapper.writeValueAsString(list));
    }

    @Test
    void testGetResourceFiltersWithAllFilters() {
        // Test case with all types of filters
        GlobalFilter globalFilter = GlobalFilter.builder()
                .genericFilter(List.of(LIST_UUID))
                .nominalV(List.of("220.0", "400.0"))
                .countryCode(List.of(Country.FR, Country.DE))
                .substationProperty(Map.of("prop1", List.of("value1", "value2")))
                .build();

        when(network.getVariantManager()).thenReturn(variantManager);
        when(networkStoreService.getNetwork(any(UUID.class), any(PreloadingStrategy.class))).thenReturn(network);

        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(
                UUID.fromString(NETWORK_UUID),
                VARIANT_ID,
                globalFilter
        );

        assertNotNull(result);
        if (result.isPresent()) {
            ResourceFilterDTO dto = result.get();
            assertEquals(ResourceFilterDTO.DataType.TEXT, dto.dataType());
            assertEquals(ResourceFilterDTO.Type.IN, dto.type());
            assertEquals(SensitivityResultEntity.Fields.functionId, dto.column());
        }
    }

    @Test
    void testGetResourceFiltersEmptyResult() {
        // Test case when no filters match
        GlobalFilter emptyGlobalFilter = GlobalFilter.builder()
                .genericFilter(List.of())
                .build();

        when(network.getVariantManager()).thenReturn(variantManager);
        when(networkStoreService.getNetwork(any(), any(PreloadingStrategy.class))).thenReturn(network);

        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(
                UUID.fromString(NETWORK_UUID),
                VARIANT_ID,
                emptyGlobalFilter
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetResourceFiltersWithGenericFilters() {
        // Test case with generic filters
        GlobalFilter globalFilter = GlobalFilter.builder()
                .genericFilter(List.of(LIST_UUID))
                .nominalV(List.of("220.0"))
                .countryCode(List.of(Country.FR))
                .build();

        when(network.getVariantManager()).thenReturn(variantManager);
        when(networkStoreService.getNetwork(any(UUID.class), any(PreloadingStrategy.class))).thenReturn(network);

        Optional<ResourceFilterDTO> result = filterService.getResourceFilter(
                UUID.fromString(NETWORK_UUID),
                VARIANT_ID,
                globalFilter
        );

        assertNotNull(result);
    }

    @Test
    void testGetFilters() {
        List<AbstractFilter> result = filterService.getFilters(List.of());
        assertTrue(result.isEmpty());

        List<UUID> filterUuids = List.of(LIST_UUID);
        List<AbstractFilter> filters = filterService.getFilters(filterUuids);
        assertNotNull(filters);
    }

    @Test
    void testGetIdentifiablesFromFilter() {
        List<IdentifiableAttributes> result = filterService.getIdentifiablesFromFilter(LIST_UUID, UUID.fromString(NETWORK_UUID), null);
        assertEquals(1, result.size());
        assertEquals(IDENTIFIABLE.getId(), result.getFirst().getId());
    }
}

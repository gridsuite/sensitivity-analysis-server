/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
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
import org.gridsuite.filter.expertfilter.expertrule.PropertiesExpertRule;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.gridsuite.sensitivityanalysis.server.dto.FilterEquipments;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityFactorsIdsByGroup;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@SpringBootTest
class FilterServiceTest {

    private static final int DATA_BUFFER_LIMIT = 256 * 1024; // AbstractJackson2Decoder.maxInMemorySize

    private static final String NETWORK_UUID = "7928181c-7977-4592-ba19-88027e4254e4";

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

    @Autowired
    private NetworkStoreService networkStoreService;

    @Autowired
    private FilterService filterService;

    @BeforeEach
    void setUp(final MockWebServer mockWebServer) throws Exception {
        filterService = new FilterService(networkStoreService, initMockWebServer(mockWebServer));

        //when(networkStoreService.getNetwork(UUID.fromString(eq(network.getId())), any(PreloadingStrategy.class))).thenReturn(network);
        //when(network.getVariantManager()).thenReturn(variantManager);
        doNothing().when(variantManager).setWorkingVariant(anyString());
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
        List<FilterEquipments> list = filterService.getFilterEquipements(List.of(VERY_LARGE_LIST_UUID, LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(createFilterEquipments()), objectMapper.writeValueAsString(list));
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

    @Test
    void testCreateNumberExpertRules() throws Exception {

        Method createNumberExpertRulesMethod = FilterService.class.getDeclaredMethod("createNumberExpertRules", List.class, FieldType.class);
        createNumberExpertRulesMethod.setAccessible(true);

        List<AbstractExpertRule> result = (List<AbstractExpertRule>) createNumberExpertRulesMethod.invoke(filterService, null, FieldType.NOMINAL_VOLTAGE);
        assertTrue(result.isEmpty());

        List<String> values = List.of("220.0", "400.0");
        result = (List<AbstractExpertRule>) createNumberExpertRulesMethod.invoke(filterService, values, FieldType.NOMINAL_VOLTAGE);
        assertEquals(2, result.size());
    }

    @Test
    void testCreateEnumExpertRules() throws Exception {
        Method createEnumExpertRulesMethod = FilterService.class.getDeclaredMethod(
                "createEnumExpertRules", List.class, FieldType.class);
        createEnumExpertRulesMethod.setAccessible(true);

        List<AbstractExpertRule> result = (List<AbstractExpertRule>) createEnumExpertRulesMethod.invoke(filterService, null, FieldType.COUNTRY);
        assertTrue(result.isEmpty());

        List<Country> countries = List.of(Country.FR, Country.DE);
        result = (List<AbstractExpertRule>) createEnumExpertRulesMethod.invoke(filterService, countries, FieldType.COUNTRY);
        assertEquals(2, result.size());
    }

    @Test
    void testCreatePropertiesRule() throws Exception {
        Method createPropertiesRuleMethod = FilterService.class.getDeclaredMethod("createPropertiesRule", String.class, List.class, FieldType.class);
        createPropertiesRuleMethod.setAccessible(true);

        String property = "testProperty";
        List<String> values = List.of("value1", "value2");

        AbstractExpertRule result = (AbstractExpertRule) createPropertiesRuleMethod.invoke(filterService, property, values, FieldType.SUBSTATION_PROPERTIES);

        assertNotNull(result);
        assertInstanceOf(PropertiesExpertRule.class, result);
    }

    @Test
    void testGetNominalVoltageFieldType() throws Exception {
        Method getNominalVoltageFieldTypeMethod = FilterService.class.getDeclaredMethod("getNominalVoltageFieldType", EquipmentType.class);
        getNominalVoltageFieldTypeMethod.setAccessible(true);

        List<FieldType> result = (List<FieldType>) getNominalVoltageFieldTypeMethod.invoke(filterService, EquipmentType.LINE);
        assertEquals(2, result.size());
        assertTrue(result.contains(FieldType.NOMINAL_VOLTAGE_1));
        assertTrue(result.contains(FieldType.NOMINAL_VOLTAGE_2));

        result = (List<FieldType>) getNominalVoltageFieldTypeMethod.invoke(filterService, EquipmentType.VOLTAGE_LEVEL);
        assertEquals(1, result.size());
        assertTrue(result.contains(FieldType.NOMINAL_VOLTAGE));

        result = (List<FieldType>) getNominalVoltageFieldTypeMethod.invoke(filterService, EquipmentType.GENERATOR);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCountryCodeFieldType() throws Exception {
        Method getCountryCodeFieldTypeMethod = FilterService.class.getDeclaredMethod(
                "getCountryCodeFieldType", EquipmentType.class);
        getCountryCodeFieldTypeMethod.setAccessible(true);

        List<FieldType> result = (List<FieldType>) getCountryCodeFieldTypeMethod.invoke(filterService, EquipmentType.LINE);
        assertEquals(2, result.size());
        assertTrue(result.contains(FieldType.COUNTRY_1));
        assertTrue(result.contains(FieldType.COUNTRY_2));

        result = (List<FieldType>) getCountryCodeFieldTypeMethod.invoke(filterService, EquipmentType.VOLTAGE_LEVEL);
        assertEquals(1, result.size());
        assertTrue(result.contains(FieldType.COUNTRY));
    }

    @Test
    void testGetSubstationPropertiesFieldTypes() throws Exception {
        Method getSubstationPropertiesFieldTypesMethod = FilterService.class.getDeclaredMethod("getSubstationPropertiesFieldTypes", EquipmentType.class);
        getSubstationPropertiesFieldTypesMethod.setAccessible(true);

        List<FieldType> result = (List<FieldType>) getSubstationPropertiesFieldTypesMethod.invoke(filterService, EquipmentType.LINE);
        assertEquals(2, result.size());
        assertTrue(result.contains(FieldType.SUBSTATION_PROPERTIES_1));
        assertTrue(result.contains(FieldType.SUBSTATION_PROPERTIES_2));

        result = (List<FieldType>) getSubstationPropertiesFieldTypesMethod.invoke(filterService, EquipmentType.GENERATOR);
        assertEquals(1, result.size());
        assertTrue(result.contains(FieldType.SUBSTATION_PROPERTIES));
    }

    @Test
    void testApplyIntersection() throws Exception {
        Method applyIntersectionMethod = FilterService.class.getDeclaredMethod("applyIntersection", List.class);
        applyIntersectionMethod.setAccessible(true);

        List<String> result = (List<String>) applyIntersectionMethod.invoke(filterService, List.of());
        assertTrue(result.isEmpty());

        List<List<String>> filterResults = List.of(
                List.of("id1", "id2", "id3"),
                List.of("id2", "id3", "id4"),
                List.of("id2", "id5")
        );
        result = (List<String>) applyIntersectionMethod.invoke(filterService, filterResults);
        assertEquals(1, result.size());
        assertEquals("id2", result.getFirst());

        List<List<String>> noIntersectionResults = List.of(
                List.of("id1", "id2"),
                List.of("id3", "id4")
        );
        result = (List<String>) applyIntersectionMethod.invoke(filterService, noIntersectionResults);
        assertTrue(result.isEmpty());
    }

    private GlobalFilter createTestGlobalFilter() {
        return GlobalFilter.builder()
                .genericFilter(List.of(LIST_UUID))
                .nominalV(List.of("220.0", "400.0"))
                .countryCode(List.of(Country.FR, Country.DE))
                .substationProperty(Map.of("prop1", List.of("value1", "value2")))
                .build();
    }

    @Test
    void testBuildExpertFilter() throws Exception {
        Method buildExpertFilterMethod = FilterService.class.getDeclaredMethod(
                "buildExpertFilter", GlobalFilter.class, EquipmentType.class);
        buildExpertFilterMethod.setAccessible(true);

        GlobalFilter globalFilter = createTestGlobalFilter();

        ExpertFilter result = (ExpertFilter) buildExpertFilterMethod.invoke(filterService, globalFilter, EquipmentType.LINE);

        assertNotNull(result);
        assertEquals(EquipmentType.LINE, result.getEquipmentType());
        assertNotNull(result.getRules());
    }

    @Test
    void testBuildExpertFilterWithEmptyGlobalFilter() throws Exception {
        Method buildExpertFilterMethod = FilterService.class.getDeclaredMethod("buildExpertFilter", GlobalFilter.class, EquipmentType.class);
        buildExpertFilterMethod.setAccessible(true);

        GlobalFilter emptyGlobalFilter = GlobalFilter.builder().build();

        ExpertFilter result = (ExpertFilter) buildExpertFilterMethod.invoke(filterService, emptyGlobalFilter, EquipmentType.LINE);

        assertNull(result);
    }

    @Test
    void testBuildExpertFilterWithVoltageLevelIdsCriteria() throws Exception {
        Method buildExpertFilterWithVoltageLevelIdsCriteriaMethod = FilterService.class.getDeclaredMethod("buildExpertFilterWithVoltageLevelIdsCriteria", UUID.class, EquipmentType.class);
        buildExpertFilterWithVoltageLevelIdsCriteriaMethod.setAccessible(true);

        ExpertFilter result = (ExpertFilter) buildExpertFilterWithVoltageLevelIdsCriteriaMethod.invoke(filterService, LIST_UUID, EquipmentType.LINE);

        assertNotNull(result);
        assertEquals(EquipmentType.LINE, result.getEquipmentType());
        assertNotNull(result.getRules());
    }
}

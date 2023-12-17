/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.IdentifiableType;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.sensitivityanalysis.server.dto.FilterEquipments;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
public class FilterServiceTest {

    private static final int DATA_BUFFER_LIMIT = 256 * 1024; // AbstractJackson2Decoder.maxInMemorySize

    private static final String NETWORK_UUID = "7928181c-7977-4592-ba19-88027e4254e4";

    private static final String VARIANT_ID = "variant_id";

    private static final UUID LIST_UUID = UUID.randomUUID();

    private static final Map<String, List<UUID>> IDENTIFIABLES_UUID = Map.of("0", List.of(LIST_UUID), "1", List.of(LIST_UUID), "2", List.of(LIST_UUID));

    private static final UUID VERY_LARGE_LIST_UUID = UUID.randomUUID();

    private static final IdentifiableAttributes IDENTIFIABLE = new IdentifiableAttributes("gen1", IdentifiableType.GENERATOR, null);

    private static final IdentifiableAttributes IDENTIFIABLE_VARIANT = new IdentifiableAttributes("load_variant", IdentifiableType.LOAD, 10.);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockWebServer server;

    @Autowired
    private FilterService filterService;

    @Before
    public void setUp() throws IOException {
        filterService = new FilterService(initMockWebServer());
    }

    @After
    public void tearDown() {
        try {
            server.shutdown();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    private String initMockWebServer() throws IOException {
        server = new MockWebServer();
        server.start();

        String jsonExpected = objectMapper.writeValueAsString(createFromIdentifiableList(LIST_UUID, List.of(IDENTIFIABLE)));
        String veryLargeJsonExpected = objectMapper.writeValueAsString(createFromIdentifiableList(VERY_LARGE_LIST_UUID, createVeryLargeList()));
        String jsonLargeFilterEquipement = objectMapper.writeValueAsString(createFilterEquipments());
        String jsonVariantExpected = objectMapper.writeValueAsString(createFromIdentifiableList(LIST_UUID, List.of(IDENTIFIABLE_VARIANT)));
        String jsonIdentifiablesExpected = objectMapper.writeValueAsString(countResultMap());
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String requestPath = Objects.requireNonNull(request.getPath());
                if (requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s&variantId=%s", LIST_UUID, NETWORK_UUID, VARIANT_ID))) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                            .setBody(jsonVariantExpected)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (requestPath.equals(String.format("/v1/filters/export?ids=%s&ids=%s&networkUuid=%s", VERY_LARGE_LIST_UUID, LIST_UUID, NETWORK_UUID))) {
                        return new MockResponse().setResponseCode(HttpStatus.OK.value())
                                .setBody(jsonLargeFilterEquipement)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s", LIST_UUID, NETWORK_UUID))) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                        .setBody(jsonExpected)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s&variantId=%s", VERY_LARGE_LIST_UUID, NETWORK_UUID, VARIANT_ID))
                           || requestPath.equals(String.format("/v1/filters/export?ids=%s&networkUuid=%s", VERY_LARGE_LIST_UUID, NETWORK_UUID))) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                            .setBody(veryLargeJsonExpected)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (requestPath.equals(String.format("/v1/filters/identifiables-count?networkUuid=%s&variantId=%s", NETWORK_UUID, VARIANT_ID))
                        || requestPath.equals(String.format("/v1/filters/identifiables-count?networkUuid=%s", NETWORK_UUID))) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value())
                            .setBody(jsonIdentifiablesExpected)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value()).setBody("Path not supported: " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        return baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
    }

    @NotNull
    private static Map<String, List<Integer>> countResultMap() {
        return Map.of("0", List.of(6), "1", List.of(6), "2", List.of(6));
    }

    private List<IdentifiableAttributes> createVeryLargeList() {
        return IntStream.range(0, DATA_BUFFER_LIMIT).mapToObj(i -> new IdentifiableAttributes("l" + i, IdentifiableType.GENERATOR, null)).collect(Collectors.toList());
    }

    private List<FilterEquipments> createFromIdentifiableList(UUID uuid, List<IdentifiableAttributes> identifiableAttributes) {
        return List.of(
                FilterEquipments.builder()
                        .identifiableAttributes(identifiableAttributes)
                        .filterId(uuid)
                        .build()
        );
    }

    private List<FilterEquipments> createFilterEquipments() {
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

    @SneakyThrows
    @Test
    public void test() {
        List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilters(List.of(LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(List.of(IDENTIFIABLE)), objectMapper.writeValueAsString(list));
        list = filterService.getIdentifiablesFromFilters(List.of(LIST_UUID), UUID.fromString(NETWORK_UUID), VARIANT_ID);
        assertEquals(objectMapper.writeValueAsString(List.of(IDENTIFIABLE_VARIANT)), objectMapper.writeValueAsString(list));
    }

    @SneakyThrows
    @Test
    public void testVeryLargeList() {
        // DataBufferLimitException should not be thrown with this message : "Exceeded limit on max bytes to buffer : DATA_BUFFER_LIMIT"
        List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilters(List.of(VERY_LARGE_LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(createVeryLargeList()), objectMapper.writeValueAsString(list));
        list = filterService.getIdentifiablesFromFilters(List.of(VERY_LARGE_LIST_UUID), UUID.fromString(NETWORK_UUID), VARIANT_ID);
        assertEquals(objectMapper.writeValueAsString(createVeryLargeList()), objectMapper.writeValueAsString(list));
    }

    @SneakyThrows
    @Test
    public void testGetMultipleLists() {
        List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilters(List.of(VERY_LARGE_LIST_UUID, LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        List<IdentifiableAttributes> expectedList = new ArrayList<>();
        expectedList.addAll(createVeryLargeList());
        expectedList.addAll(List.of(IDENTIFIABLE));
        assertEquals(objectMapper.writeValueAsString(expectedList), objectMapper.writeValueAsString(list));
    }

    @SneakyThrows
    @Test
    public void testGetFactorsCount() {
        Map<String, List<Long>> list = filterService.getIdentifiablesCount(IDENTIFIABLES_UUID, UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(countResultMap()), objectMapper.writeValueAsString(list));
        list = filterService.getIdentifiablesCount(IDENTIFIABLES_UUID, UUID.fromString(NETWORK_UUID), VARIANT_ID);
        assertEquals(objectMapper.writeValueAsString(countResultMap()), objectMapper.writeValueAsString(list));
    }

    @SneakyThrows
    @Test
    public void testListOfUuids() {
        // DataBufferLimitException should not be thrown with this message : "Exceeded limit on max bytes to buffer : DATA_BUFFER_LIMIT"
        List<FilterEquipments> list = filterService.getFilterEquipements(List.of(VERY_LARGE_LIST_UUID, LIST_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(objectMapper.writeValueAsString(createFilterEquipments()), objectMapper.writeValueAsString(list));
    }
}

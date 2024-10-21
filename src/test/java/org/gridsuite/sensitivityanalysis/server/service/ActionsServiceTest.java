/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.sensitivityanalysis.server.configuration.RestTemplateConfig;
import org.gridsuite.sensitivityanalysis.server.dto.ContingencyListExportResult;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@SpringBootTest
class ActionsServiceTest {

    private static final int DATA_BUFFER_LIMIT = 256 * 1024; // AbstractJackson2Decoder.maxInMemorySize

    private static final String NETWORK_UUID = "7928181c-7977-4592-ba19-88027e4254e4";

    private static final String VARIANT_ID = "variant_id";

    private static final UUID LIST_UUID = UUID.randomUUID();
    private static final UUID LIST2_UUID = UUID.randomUUID();
    private static final String CONTINGENCY_COUNT = "7";

    private static final UUID VERY_LARGE_LIST_UUID = UUID.randomUUID();

    private static final Contingency CONTINGENCY = new Contingency("c1", new BranchContingency("b1"));

    private static final Contingency CONTINGENCY_VARIANT = new Contingency("c2", new BranchContingency("b2"));

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper objectMapper = restTemplateConfig.objectMapper();

    @Autowired
    private ActionsService actionsService;

    @BeforeEach
    void setUp(final MockWebServer mockWebServer) throws Exception {
        actionsService.setActionsServerBaseUri(initMockWebServer(mockWebServer));
    }

    private String initMockWebServer(final MockWebServer server) throws IOException {
        String jsonExpected = objectMapper.writeValueAsString(new ContingencyListExportResult(List.of(CONTINGENCY), null));
        String veryLargeJsonExpected = objectMapper.writeValueAsString(new ContingencyListExportResult(createVeryLargeList(), null));
        String jsonVariantExpected = objectMapper.writeValueAsString(new ContingencyListExportResult(List.of(CONTINGENCY_VARIANT), null));

        final Dispatcher dispatcher = new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String requestPath = Objects.requireNonNull(request.getPath());
                if (requestPath.equals(String.format("/v1/contingency-lists/export?networkUuid=%s&variantId=%s&contingencyListIds=%s", NETWORK_UUID, VARIANT_ID, LIST_UUID))) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonVariantExpected);
                } else if (requestPath.equals(String.format("/v1/contingency-lists/export?networkUuid=%s&contingencyListIds=%s", NETWORK_UUID, LIST_UUID))) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), jsonExpected);
                } else if (requestPath.equals(String.format("/v1/contingency-lists/export?networkUuid=%s&variantId=%s&contingencyListIds=%s", NETWORK_UUID, VARIANT_ID, VERY_LARGE_LIST_UUID))
                           || requestPath.equals(String.format("/v1/contingency-lists/export?networkUuid=%s&contingencyListIds=%s", NETWORK_UUID, VERY_LARGE_LIST_UUID))) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), veryLargeJsonExpected);
                } else if (requestPath.equals(String.format("/v1/contingency-lists/count?ids=%s&ids=%s&networkUuid=%s&variantId=%s", LIST_UUID, LIST2_UUID, NETWORK_UUID, VARIANT_ID))
                        || requestPath.equals(String.format("/v1/contingency-lists/count?ids=%s&ids=%s&networkUuid=%s", LIST_UUID, LIST2_UUID, NETWORK_UUID))) {
                    return new MockResponse(HttpStatus.OK.value(), Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), CONTINGENCY_COUNT);
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

    private static List<Contingency> createVeryLargeList() {
        return IntStream.range(0, DATA_BUFFER_LIMIT).mapToObj(i -> new Contingency("l" + i, new BranchContingency("l" + i))).collect(Collectors.toList());
    }

    @Test
    void testGetContingencyList() {
        List<Contingency> list = actionsService.getContingencyList(List.of(LIST_UUID), UUID.fromString(NETWORK_UUID), null).getContingenciesFound();
        assertEquals(List.of(CONTINGENCY), list);
        list = actionsService.getContingencyList(List.of(LIST_UUID), UUID.fromString(NETWORK_UUID), VARIANT_ID).getContingenciesFound();
        assertEquals(List.of(CONTINGENCY_VARIANT), list);
    }

    @Test
    void testVeryLargeList() {
        // DataBufferLimitException should not be thrown with this message : "Exceeded limit on max bytes to buffer : DATA_BUFFER_LIMIT"
        List<Contingency> list = actionsService.getContingencyList(List.of(VERY_LARGE_LIST_UUID), UUID.fromString(NETWORK_UUID), null).getContingenciesFound();
        assertEquals(createVeryLargeList(), list);
        list = actionsService.getContingencyList(List.of(VERY_LARGE_LIST_UUID), UUID.fromString(NETWORK_UUID), VARIANT_ID).getContingenciesFound();
        assertEquals(createVeryLargeList(), list);
    }

    @Test
    void testGetContingencyCount() {
        Integer count = actionsService.getContingencyCount(List.of(LIST_UUID, LIST2_UUID), UUID.fromString(NETWORK_UUID), null);
        assertEquals(CONTINGENCY_COUNT, count.toString());
        count = actionsService.getContingencyCount(List.of(LIST_UUID, LIST2_UUID), UUID.fromString(NETWORK_UUID), VARIANT_ID);
        assertEquals(CONTINGENCY_COUNT, count.toString());
    }
}

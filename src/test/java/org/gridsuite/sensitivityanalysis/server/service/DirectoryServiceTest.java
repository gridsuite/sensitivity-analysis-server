/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.sensitivityanalysis.server.dto.ElementAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import static org.gridsuite.computation.service.NotificationService.HEADER_USER_ID;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@AutoConfigureMockMvc
@SpringBootTest
class DirectoryServiceTest {

    private WireMockServer wireMockServer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DirectoryService directoryService;

    private static final String USER_ID = "userId";
    private static final UUID EQUIPMENTS_ID_1 = UUID.fromString("3f7c9e2a-8b41-4d6a-a1f3-9c5b72e8d4af");
    private static final String EQUIPMENTS_NAME_1 = "equipments1";
    private static final UUID EQUIPMENTS_ID_2 = UUID.fromString("b8a4f2c1-6d3e-4a9b-92f7-1e5c8d7a3b60");
    private static final String EQUIPMENTS_NAME_2 = "equipments2";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        ReflectionTestUtils.setField(directoryService, "baseUri", wireMockServer.baseUrl());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void getElementNamesWithNullElementUuidsTest() {
        assertThrows(NullPointerException.class,
                () -> directoryService.getElementNames(null, USER_ID));
    }

    @Test
    void getElementNamesWithEmptyElementUuidsTest() {
        assertEquals(Map.of(), directoryService.getElementNames(List.of(), USER_ID));
    }

    @Test
    void getElementNamesTest() throws JsonProcessingException {
        List<ElementAttributes> elementAttributes = List.of(
                new ElementAttributes(EQUIPMENTS_ID_1, EQUIPMENTS_NAME_1),
                new ElementAttributes(EQUIPMENTS_ID_2, EQUIPMENTS_NAME_2)
        );
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/elements"))
                .withQueryParam("ids", WireMock.equalTo(EQUIPMENTS_ID_1.toString()))
                .withQueryParam("ids", WireMock.equalTo(EQUIPMENTS_ID_2.toString()))
                .withQueryParam("strictMode", WireMock.equalTo("false"))
                .withHeader(HEADER_USER_ID, WireMock.equalTo(USER_ID))
                .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(objectMapper.writeValueAsString(elementAttributes))));

        Map<UUID, String> elementNames = Map.of(
                EQUIPMENTS_ID_1, EQUIPMENTS_NAME_1,
                EQUIPMENTS_ID_2, EQUIPMENTS_NAME_2
        );
        assertEquals(elementNames, directoryService.getElementNames(List.of(EQUIPMENTS_ID_1, EQUIPMENTS_ID_2), USER_ID));
    }
}

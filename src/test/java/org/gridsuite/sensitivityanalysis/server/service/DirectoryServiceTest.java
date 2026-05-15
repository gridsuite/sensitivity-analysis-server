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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.sensitivityanalysis.server.service.DirectoryService.DIRECTORY_API_GET_ELEMENTS_NAMES_ENDPOINT;
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

    private static final UUID ELEMENT_UUID_1 = UUID.fromString("3f7c9e2a-8b41-4d6a-a1f3-9c5b72e8d4af");
    private static final String ELEMENT_NAME_1 = "name1";
    private static final UUID ELEMENT_UUID_2 = UUID.fromString("b8a4f2c1-6d3e-4a9b-92f7-1e5c8d7a3b60");
    private static final String ELEMENT_NAME_2 = "name2";

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
                () -> directoryService.getElementNames(null));
    }

    @Test
    void getElementNamesWithEmptyElementUuidsTest() {
        assertEquals(Map.of(), directoryService.getElementNames(Set.of()));
    }

    @Test
    void getElementNamesTest() throws JsonProcessingException {
        Map<UUID, String> elementNameByUuid = Map.of(
                ELEMENT_UUID_1, ELEMENT_NAME_1,
                ELEMENT_UUID_2, ELEMENT_NAME_2
        );
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(DIRECTORY_API_GET_ELEMENTS_NAMES_ENDPOINT))
                .withQueryParam("ids", WireMock.equalTo(ELEMENT_UUID_1.toString()))
                .withQueryParam("ids", WireMock.equalTo(ELEMENT_UUID_2.toString()))
                .withQueryParam("strictMode", WireMock.equalTo("false"))
                .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(objectMapper.writeValueAsString(elementNameByUuid))));

        assertEquals(elementNameByUuid, directoryService.getElementNames(Set.of(ELEMENT_UUID_1, ELEMENT_UUID_2)));
    }
}

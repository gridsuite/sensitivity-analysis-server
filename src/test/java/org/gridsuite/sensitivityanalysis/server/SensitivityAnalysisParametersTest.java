/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import org.assertj.core.api.Assertions;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisParametersRepository;
import org.gridsuite.sensitivityanalysis.server.service.LoadFlowService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisParametersService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.sensitivityanalysis.server.util.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SensitivityAnalysisParametersTest {

    private static final String URI_PARAMETERS_BASE = "/v1/parameters";

    private static final String URI_PARAMETERS_GET_PUT = URI_PARAMETERS_BASE + "/";

    private static final String PROVIDER = "provider";

    @Value("${sensitivity-analysis.default-provider}")
    private String defaultSensitivityAnalysisProvider;

    @Autowired
    MockMvc mockMvc;

    private WireMockServer wireMockServer;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    SensitivityAnalysisParametersService parametersService;

    @Autowired
    SensitivityAnalysisParametersRepository parametersRepository;

    @Autowired
    LoadFlowService loadFlowService;

    @BeforeEach
    public void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        loadFlowService.setLoadFlowServiceBaseUri(wireMockServer.baseUrl());
    }

    @AfterEach
    public void tearOff() {
        parametersRepository.deleteAll();
    }

    @Test
    void testCreate() throws Exception {
        SensitivityAnalysisParametersInfos parametersToCreate = buildParameters();

        UUID parametersUuid = postParameters(parametersToCreate);
        SensitivityAnalysisParametersInfos createdParameters = getParameters(parametersUuid);

        assertThat(createdParameters).recursivelyEquals(parametersToCreate);
    }

    @Test
    void testCreateDefaultValues() throws Exception {

        SensitivityAnalysisParametersInfos defaultParameters = SensitivityAnalysisParametersInfos.builder().provider(defaultSensitivityAnalysisProvider).build();

        UUID parametersUuid = postParameters();

        SensitivityAnalysisParametersInfos createdParameters = getParameters(parametersUuid);

        assertThat(createdParameters).recursivelyEquals(defaultParameters);
    }

    @Test
    void testRead() throws Exception {

        SensitivityAnalysisParametersInfos parametersToRead = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToRead);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid))
            .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        SensitivityAnalysisParametersInfos receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).recursivelyEquals(parametersToRead);
    }

    @Test
    void testUpdate() throws Exception {

        SensitivityAnalysisParametersInfos parametersToUpdate = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToUpdate);

        parametersToUpdate = buildParametersUpdate();

        String parametersToUpdateJson = mapper.writeValueAsString(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).content(parametersToUpdateJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        SensitivityAnalysisParametersInfos updatedParameters = getParameters(parametersUuid);

        assertThat(updatedParameters).recursivelyEquals(parametersToUpdate);

        // reset parameters
        SensitivityAnalysisParametersInfos defaultParameters = SensitivityAnalysisParametersInfos.builder().provider(defaultSensitivityAnalysisProvider).build();

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        updatedParameters = getParameters(parametersUuid);

        assertThat(updatedParameters).recursivelyEquals(defaultParameters);
    }

    @Test
    void testDelete() throws Exception {

        SensitivityAnalysisParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<SensitivityAnalysisParametersEntity> storedParameters = parametersRepository.findAll();

        assertThat(storedParameters).isEmpty();
    }

    @Test
    void testGetAll() throws Exception {
        SensitivityAnalysisParametersInfos parameters1 = buildParameters();

        SensitivityAnalysisParametersInfos parameters2 = buildParametersUpdate();

        saveAndReturnId(parameters1);

        saveAndReturnId(parameters2);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_BASE))
            .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<SensitivityAnalysisParametersInfos> receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        Assertions.assertThat(receivedParameters).hasSize(2);
    }

    @Test
    void testDuplicate() throws Exception {
        SensitivityAnalysisParametersInfos parametersToCreate = buildParameters();
        UUID parametersUuid = postParameters(parametersToCreate);
        SensitivityAnalysisParametersInfos createdParameters = getParameters(parametersUuid);

        mockMvc.perform(post(URI_PARAMETERS_BASE + "/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());

        UUID duplicatedParametersUuid = duplicateParameters(createdParameters.getUuid());

        SensitivityAnalysisParametersInfos duplicatedParameters = getParameters(duplicatedParametersUuid);
        assertThat(duplicatedParameters).recursivelyEquals(createdParameters);
    }

    @Test
    void buildInputDataTest() throws Exception {
        SensitivityAnalysisParametersInfos parametersInfos = buildParameters();

        // load flow parameters mock
        LoadFlowParametersValues loadFlowParametersValues = LoadFlowParametersValues.builder()
            .commonParameters(LoadFlowParameters.load())
            .specificParameters(Map.of("reactiveRangeCheckMode", "TARGET_P", "plausibleActivePowerLimit", "5000.0"))
            .build();
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/v1/parameters/.*/values\\?provider=.*"))
            .willReturn(WireMock.ok().withHeader("Content-Type", "application/json").withBody(mapper.writeValueAsString(loadFlowParametersValues))));

        SensitivityAnalysisInputData inputData = parametersService.buildInputData(parametersInfos, UUID.randomUUID());

        // now we check that each field contains the good value
        SensitivityAnalysisParameters sensitivityAnalysisParameters = inputData.getParameters();
        assertThat(sensitivityAnalysisParameters.getLoadFlowParameters()).recursivelyEquals(loadFlowParametersValues.commonParameters());
        assertThat(sensitivityAnalysisParameters)
            .extracting(
                SensitivityAnalysisParameters::getAngleFlowSensitivityValueThreshold,
                SensitivityAnalysisParameters::getFlowFlowSensitivityValueThreshold,
                SensitivityAnalysisParameters::getFlowVoltageSensitivityValueThreshold)
            .containsExactly(
                parametersInfos.getAngleFlowSensitivityValueThreshold(),
                parametersInfos.getFlowFlowSensitivityValueThreshold(),
                parametersInfos.getFlowVoltageSensitivityValueThreshold());

        assertEquals(inputData.getLoadFlowSpecificParameters(), loadFlowParametersValues.specificParameters());

        assertEquals(inputData.getSensitivityInjections().size(), parametersInfos.getSensitivityInjection().size());
        assertThat(inputData.getSensitivityInjections().get(0)).recursivelyEquals(parametersInfos.getSensitivityInjection().get(0));
        assertEquals(inputData.getSensitivityInjectionsSets().size(), parametersInfos.getSensitivityInjectionsSet().size());
        assertThat(inputData.getSensitivityInjectionsSets().get(0)).recursivelyEquals(parametersInfos.getSensitivityInjectionsSet().get(0));
        assertEquals(inputData.getSensitivityPSTs().size(), parametersInfos.getSensitivityPST().size());
        assertThat(inputData.getSensitivityPSTs().get(0)).recursivelyEquals(parametersInfos.getSensitivityPST().get(0));
        assertEquals(inputData.getSensitivityHVDCs().size(), parametersInfos.getSensitivityHVDC().size());
        assertThat(inputData.getSensitivityHVDCs().get(0)).recursivelyEquals(parametersInfos.getSensitivityHVDC().get(0));
        assertEquals(inputData.getSensitivityNodes().size(), parametersInfos.getSensitivityNodes().size());
        assertThat(inputData.getSensitivityNodes().get(0)).recursivelyEquals(parametersInfos.getSensitivityNodes().get(0));
    }

    private SensitivityAnalysisParametersInfos getParameters(UUID parameterUuid) throws Exception {
        MvcResult mvcGetResult = mockMvc.perform(get(URI_PARAMETERS_BASE + "/{parameterUuid}", parameterUuid))
            .andExpect(status().isOk()).andReturn();

        return mapper.readValue(
            mvcGetResult.getResponse().getContentAsString(),
            SensitivityAnalysisParametersInfos.class);
    }

    private UUID postParameters(SensitivityAnalysisParametersInfos parametersToCreate) throws Exception {
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        MvcResult mvcPostResult = mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk()).andReturn();

        return mapper.readValue(mvcPostResult.getResponse().getContentAsString(), UUID.class);
    }

    private UUID postParameters() throws Exception {
        MvcResult mvcPostResult = mockMvc.perform(post(URI_PARAMETERS_BASE + "/default"))
            .andExpect(status().isOk()).andReturn();

        return mapper.readValue(mvcPostResult.getResponse().getContentAsString(), UUID.class);
    }

    private UUID duplicateParameters(UUID parametersUuid) throws Exception {
        MvcResult mvcPostResult = mockMvc.perform(post(URI_PARAMETERS_BASE + "/" + parametersUuid))
            .andExpect(status().isOk()).andReturn();

        return mapper.readValue(mvcPostResult.getResponse().getContentAsString(), UUID.class);
    }

    /**
     * Save parameters into the repository and return its UUID.
     */
    protected UUID saveAndReturnId(SensitivityAnalysisParametersInfos parametersInfos) {
        return parametersRepository.save(parametersInfos.toEntity()).getId();
    }

    public static SensitivityAnalysisParametersInfos buildParameters() {
        EquipmentsContainer equipments1 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da321"), "identifiable1");
        EquipmentsContainer equipments2 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da322"), "identifiable2");
        EquipmentsContainer equipments3 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da323"), "identifiable3");
        SensitivityInjectionsSet injectionsSet = new SensitivityInjectionsSet(List.of(equipments2), List.of(equipments1), SensitivityAnalysisInputData.DistributionType.PROPORTIONAL, List.of(equipments3), true);
        SensitivityInjection injections = new SensitivityInjection(List.of(equipments1), List.of(equipments2), List.of(equipments3), true);
        SensitivityHVDC hvdc = new SensitivityHVDC(List.of(equipments1), SensitivityAnalysisInputData.SensitivityType.DELTA_MW, List.of(equipments2), List.of(equipments3), true);
        SensitivityPST pst = new SensitivityPST(List.of(equipments2), SensitivityAnalysisInputData.SensitivityType.DELTA_MW, List.of(equipments1), List.of(equipments3), true);
        SensitivityNodes nodes = new SensitivityNodes(List.of(equipments1), List.of(equipments2), List.of(equipments3), true);

        return SensitivityAnalysisParametersInfos.builder()
            .provider(PROVIDER)
            .flowFlowSensitivityValueThreshold(90)
            .angleFlowSensitivityValueThreshold(0.6)
            .flowVoltageSensitivityValueThreshold(0.1)
            .sensitivityInjectionsSet(List.of(injectionsSet))
            .sensitivityInjection(List.of(injections))
            .sensitivityHVDC(List.of(hvdc))
            .sensitivityPST(List.of(pst))
            .sensitivityNodes(List.of(nodes))
            .build();
    }

    protected SensitivityAnalysisParametersInfos buildParametersUpdate() {
        return SensitivityAnalysisParametersInfos.builder()
            .provider(PROVIDER)
            .flowFlowSensitivityValueThreshold(91)
            .angleFlowSensitivityValueThreshold(0.7)
            .flowVoltageSensitivityValueThreshold(0.2)
            .sensitivityInjectionsSet(List.of())
            .sensitivityInjection(List.of())
            .sensitivityHVDC(List.of())
            .sensitivityPST(List.of())
            .sensitivityNodes(List.of())
            .build();
    }
}


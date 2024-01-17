/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import org.assertj.core.api.Assertions;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisParametersRepository;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisParametersService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.sensitivityanalysis.server.util.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SensitivityAnalysisParametersTest {

    private static final String URI_PARAMETERS_BASE = "/v1/parameters";

    private static final String URI_PARAMETERS_GET_PUT = URI_PARAMETERS_BASE + "/";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    SensitivityAnalysisParametersService parametersService;

    @Autowired
    private SensitivityAnalysisParametersRepository parametersRepository;

    @BeforeEach
    public void setup() {
        parametersRepository.deleteAll();
    }

    @AfterEach
    public void tearOff() {
        parametersRepository.deleteAll();
    }

    @Test
    void testCreate() throws Exception {

        SensitivityAnalysisParametersInfos parametersToCreate = buildParameters();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();

        SensitivityAnalysisParametersInfos createdParameters = parametersRepository.findAll().get(0).toInfos();

        assertThat(createdParameters).recursivelyEquals(parametersToCreate);
    }

    @Test
    void testCreateDefaultValues() throws Exception {

        SensitivityAnalysisParametersInfos defaultParameters = SensitivityAnalysisParametersInfos.builder().build();

        mockMvc.perform(post(URI_PARAMETERS_BASE + "/default"))
            .andExpect(status().isOk()).andReturn();

        SensitivityAnalysisParametersInfos createdParameters = parametersRepository.findAll().get(0).toInfos();

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

        SensitivityAnalysisParametersInfos updatedParameters = parametersRepository.findById(parametersUuid).get().toInfos();

        assertThat(updatedParameters).recursivelyEquals(parametersToUpdate);
    }

    @Test
    void testDelete() throws Exception {

        SensitivityAnalysisParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<SensitivityAnalysisParametersEntity> storedParameters = parametersRepository.findAll();

        assertTrue(storedParameters.isEmpty());
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
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();
        SensitivityAnalysisParametersInfos createdParameters = parametersRepository.findAll().get(0).toInfos();

        mockMvc.perform(post(URI_PARAMETERS_BASE)
                .param("duplicateFrom", UUID.randomUUID().toString()))
            .andExpect(status().isNotFound());

        mockMvc.perform(post(URI_PARAMETERS_BASE)
                .param("duplicateFrom", createdParameters.getUuid().toString()))
            .andExpect(status().isOk());

        SensitivityAnalysisParametersInfos duplicatedParameters = parametersRepository.findAll().get(1).toInfos();
        assertThat(duplicatedParameters).recursivelyEquals(createdParameters);
    }

    @Test
    void buildInputDataTest() {
        SensitivityAnalysisParametersInfos parametersInfos = buildParameters();
        UUID parametersUuid = saveAndReturnId(parametersInfos);
        LoadFlowParametersValues loadFlowParametersValues = LoadFlowParametersValues.builder()
            .commonParameters(LoadFlowParameters.load())
            .specificParameters(Map.of("reactiveRangeCheckMode", "TARGET_P", "plausibleActivePowerLimit", "5000.0"))
            .build();

        SensitivityAnalysisInputData inputData = parametersService.buildInputData(parametersUuid, loadFlowParametersValues);

        // now we check that each field contains the good value
        SensitivityAnalysisParameters sensitivityAnalysisParameters = inputData.getParameters();
        assertThat(sensitivityAnalysisParameters.getLoadFlowParameters()).recursivelyEquals(loadFlowParametersValues.getCommonParameters());
        assertThat(sensitivityAnalysisParameters)
            .extracting(
                SensitivityAnalysisParameters::getAngleFlowSensitivityValueThreshold,
                SensitivityAnalysisParameters::getFlowFlowSensitivityValueThreshold,
                SensitivityAnalysisParameters::getFlowVoltageSensitivityValueThreshold)
            .containsExactly(
                parametersInfos.getAngleFlowSensitivityValueThreshold(),
                parametersInfos.getFlowFlowSensitivityValueThreshold(),
                parametersInfos.getFlowVoltageSensitivityValueThreshold());

        assertEquals(inputData.getLoadFlowSpecificParameters(), loadFlowParametersValues.getSpecificParameters());

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


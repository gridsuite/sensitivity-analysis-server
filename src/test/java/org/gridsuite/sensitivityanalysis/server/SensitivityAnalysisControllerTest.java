/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.TwoWindingsTransformerContingency;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.SortKey;
import org.gridsuite.sensitivityanalysis.server.service.ActionsService;
import org.gridsuite.sensitivityanalysis.server.service.FilterService;
import org.gridsuite.sensitivityanalysis.server.service.LoadFlowService;
import org.gridsuite.sensitivityanalysis.server.util.TestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.gridsuite.sensitivityanalysis.server.computation.service.NotificationService.*;
import static org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisWorkerService.COMPUTATION_TYPE;
import static org.gridsuite.sensitivityanalysis.server.util.TestUtils.unzip;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {SensitivityAnalysisApplication.class, TestChannelBinderConfiguration.class})})
class SensitivityAnalysisControllerTest {
    public static final String MONITORED_BRANCHES_KEY = "monitoredBranchs";
    public static final String INJECTIONS_KEY = "injections";
    public static final String CONTINGENCIES_KEY = "contingencies";
    private static final int TIMEOUT = 1000;
    private static final String ERROR_MESSAGE = "Error message test";

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NETWORK_ERROR_UUID = UUID.randomUUID();
    public static UUID PARAMETERS_UUID;
    public static final UUID LOADFLOW_PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();

    public static final IdentifiableAttributes BRANCH1 = new IdentifiableAttributes("NHV1_NHV2_1", IdentifiableType.LINE, null);
    public static final IdentifiableAttributes BRANCH2 = new IdentifiableAttributes("NHV1_NHV2_2", IdentifiableType.LINE, null);
    public static final IdentifiableAttributes GEN1 = new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR, null);
    public static final IdentifiableAttributes GEN2 = new IdentifiableAttributes("GEN2", IdentifiableType.GENERATOR, null);
    public static final Contingency CONTINGENCY1 = new Contingency("contingency1", new TwoWindingsTransformerContingency("NGEN_NHV1"));
    public static final Contingency CONTINGENCY2 = new Contingency("contingency2", new TwoWindingsTransformerContingency("NHV2_NLOAD"));
    public static final UUID BRANCH1_CONTAINER_UUID = UUID.randomUUID();
    public static final UUID BRANCH2_CONTAINER_UUID = UUID.randomUUID();
    public static final UUID GEN1_CONTAINER_UUID = UUID.randomUUID();
    public static final UUID GEN2_CONTAINER_UUID = UUID.randomUUID();
    public static final UUID CONTINGENCY1_CONTAINER_UUID = UUID.randomUUID();
    public static final UUID CONTINGENCY2_CONTAINER_UUID = UUID.randomUUID();

    @Autowired
    private OutputDestination output;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    TestRepository testRepository;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private ActionsService actionsService;

    @MockBean
    private FilterService filterService;

    @MockBean
    private LoadFlowService loadflowService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        Network network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);
        given(networkStoreService.getNetwork(NETWORK_ERROR_UUID, PreloadingStrategy.COLLECTION)).willThrow(new RuntimeException(ERROR_MESSAGE));

        given(actionsService.getContingencyList(eq(CONTINGENCY1_CONTAINER_UUID), any(), any())).willReturn(List.of(CONTINGENCY1));
        given(actionsService.getContingencyList(eq(CONTINGENCY2_CONTAINER_UUID), any(), any())).willReturn(List.of(CONTINGENCY2));
        given(filterService.getIdentifiablesFromFilters(eq(List.of(GEN1_CONTAINER_UUID, GEN2_CONTAINER_UUID)), any(), any())).willReturn(List.of(GEN1, GEN2));
        given(filterService.getIdentifiablesFromFilters(eq(List.of(BRANCH1_CONTAINER_UUID, BRANCH2_CONTAINER_UUID)), any(), any())).willReturn(List.of(BRANCH1, BRANCH2));
        given(filterService.getIdentifiablesFromFilters(eq(List.of(GEN1_CONTAINER_UUID, GEN2_CONTAINER_UUID)), any(), any())).willReturn(List.of(GEN1, GEN2));

        LoadFlowParametersValues loadFlowParametersValues = LoadFlowParametersValues.builder()
            .commonParameters(LoadFlowParameters.load())
            .specificParameters(Map.of())
            .build();
        doReturn(loadFlowParametersValues).when(loadflowService).getLoadFlowParameters(eq(LOADFLOW_PARAMETERS_UUID), any());

        PARAMETERS_UUID = createParameters();
    }

    private UUID createParameters() throws Exception {
        SensitivityAnalysisParametersInfos parameters = SensitivityAnalysisParametersInfos.builder()
            .sensitivityInjection(List.of(
                SensitivityInjection.builder()
                    .monitoredBranches(List.of(
                        new EquipmentsContainer(BRANCH1_CONTAINER_UUID, "branch1"),
                        new EquipmentsContainer(BRANCH2_CONTAINER_UUID, "branch2")
                    ))
                    .injections(List.of(
                        new EquipmentsContainer(GEN1_CONTAINER_UUID, "gen1"),
                        new EquipmentsContainer(GEN2_CONTAINER_UUID, "gen2")
                    ))
                    .contingencies(List.of(
                        new EquipmentsContainer(CONTINGENCY1_CONTAINER_UUID, "contingency1"),
                        new EquipmentsContainer(CONTINGENCY2_CONTAINER_UUID, "contingency2")
                    ))
                    .activated(true)
                    .build()
            ))
            .build();

        MvcResult result = mockMvc.perform(
                post("/" + VERSION + "/parameters")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_USER_ID, "testUserId")
                    .content(mapper.writeValueAsString(parameters)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), UUID.class);
    }

    // added for testStatus can return null, after runTest
    @AfterEach
    void tearDown() throws Exception {
        mockMvc.perform(delete("/" + VERSION + "/results")).andExpect(status().isOk());
    }

    @Test
    void runTest() throws Exception {
        SensitivityAnalysisResult result = runInMemory();
        assertEquals(12, result.getFactors().size());
        assertEquals(12, result.getValues().size());
        assertEquals(2, result.getContingencyStatuses().size());
        assertTrue(result.getContingencyStatuses().stream().allMatch(cs -> cs.getStatus() == SensitivityAnalysisResult.Status.SUCCESS));
    }

    @Test
    void filterOptionsTest() throws Exception {
        UUID resultUuid = run();
        checkComputationSucceeded(resultUuid);

        // test filter options
        ResultsSelector nkFilter = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .build();
        SensitivityResultFilterOptions nkFilterResult = queryFilterOptions(resultUuid, nkFilter);
        assertEquals(2, nkFilterResult.getAllContingencyIds().size());
        assertEquals(2, nkFilterResult.getAllFunctionIds().size());
        assertEquals(2, nkFilterResult.getAllVariableIds().size());

        ResultsSelector nWithTypeFilter = ResultsSelector.builder()
            .tabSelection(ResultTab.N)
            .functionType(SensitivityFunctionType.BRANCH_CURRENT_1)
            .build();
        SensitivityResultFilterOptions nWithTypeFilterResult = queryFilterOptions(resultUuid, nWithTypeFilter);
        assertNull(nWithTypeFilterResult.getAllContingencyIds());
        assertEquals(0, nWithTypeFilterResult.getAllFunctionIds().size());
        assertEquals(0, nWithTypeFilterResult.getAllVariableIds().size());
    }

    @Test
    void queryResultTest() throws Exception {
        UUID resultUuid = run();
        checkComputationSucceeded(resultUuid);

        // check results can be retrieved for the without contingencies side
        // and that they can be filtered by function IDs, variable IDs
        // and sorted according to multiple criteria
        ResultsSelector selectorN = ResultsSelector.builder()
            .tabSelection(ResultTab.N)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .functionIds(Stream.of(BRANCH1, BRANCH2).map(IdentifiableAttributes::getId).toList())
            .variableIds(Stream.of(GEN1, GEN2).map(IdentifiableAttributes::getId).toList())
            .sortKeysWithWeightAndDirection(Map.of(
                SortKey.SENSITIVITY, -1,
                SortKey.REFERENCE, 2,
                SortKey.VARIABLE, 3,
                SortKey.FUNCTION, 4))
            .build();
        SensitivityRunQueryResult resN = queryResult(resultUuid, selectorN);
        assertEquals(4, (long) resN.getTotalSensitivitiesCount());

        // check results can be retrieved for the with contingencies side
        // filtered and sorted by multiple criteria too
        ResultsSelector selectorNK = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .contingencyIds(Stream.of(CONTINGENCY1, CONTINGENCY2).map(Contingency::getId).toList())
            .functionIds(Stream.of(BRANCH1, BRANCH2).map(IdentifiableAttributes::getId).toList())
            .variableIds(Stream.of(GEN1, GEN2).map(IdentifiableAttributes::getId).toList())
            .sortKeysWithWeightAndDirection(Map.of(
                SortKey.POST_SENSITIVITY, -1,
                SortKey.POST_REFERENCE, -2,
                SortKey.SENSITIVITY, -3,
                SortKey.VARIABLE, 4,
                SortKey.FUNCTION, 5,
                SortKey.REFERENCE, 6,
                SortKey.CONTINGENCY, 7))
            .pageSize(10)
            .pageNumber(0)
            .build();
        SensitivityRunQueryResult resNK = queryResult(resultUuid, selectorNK);
        assertEquals(8, (long) resNK.getTotalSensitivitiesCount());
        assertEquals(8, resNK.getSensitivities().size());

        // check that a request for not present contingency does not crash and just brings nothing
        ResultsSelector selectorNKz1 = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .contingencyIds(List.of("undefined"))
            .build();
        SensitivityRunQueryResult resNKz1 = queryResult(resultUuid, selectorNKz1);
        assertEquals(0, (long) resNKz1.getTotalSensitivitiesCount());

        // check that a request for not present function does not crash and just brings nothing
        ResultsSelector selectorNKz2 = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .functionIds(List.of("undefined"))
            .build();
        SensitivityRunQueryResult resNKz2 = queryResult(resultUuid, selectorNKz2);
        assertEquals(0, (long) resNKz2.getTotalSensitivitiesCount());

        // check that a request for not present variable does not crash and just brings nothing
        ResultsSelector selectorNKz3 = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .variableIds(List.of("undefined"))
            .build();
        SensitivityRunQueryResult resNKz3 = queryResult(resultUuid, selectorNKz3);
        assertEquals(0, (long) resNKz3.getTotalSensitivitiesCount());

        // check that a request for another function type does not crash and just brings nothing
        ResultsSelector selectorNKz4 = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2)
            .build();
        SensitivityRunQueryResult resNKz4 = queryResult(resultUuid, selectorNKz4);
        assertEquals(0, (long) resNKz4.getTotalSensitivitiesCount());

        // check that a request with a bogus selector json does not crash and raises 4xx status
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID).param("selector", "bogusSelector"))
            .andExpect(status().is4xxClientError())
            .andReturn();

        // should return not found if result does not exist
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void testDeterministicResult() throws Exception {
        UUID resultUuid = run();
        checkComputationSucceeded(resultUuid);

        // check that the results is deterministic while the sort by contingency id is not (there are 2 results with the same id)
        //so the results should be sorted by
        ResultsSelector selectorNK = ResultsSelector.builder()
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .tabSelection(ResultTab.N_K)
            .sortKeysWithWeightAndDirection(Map.of(SortKey.CONTINGENCY, 1))
            .pageSize(10)
            .pageNumber(0)
            .build();
        SensitivityRunQueryResult resNK = queryResult(resultUuid, selectorNK);
        assertEquals(8, (long) resNK.getTotalSensitivitiesCount());
        assertEquals(8, resNK.getSensitivities().size());

        List<? extends SensitivityOfTo> sortedSensitivityList = testRepository.createSortedSensitivityList();
        // Sorted list does not reconcile N and N-K values for the results, so we just ignore values for the comparison
        // We just want to know if the factors are correctly ordered
        assertThat(resNK.getSensitivities())
            .usingRecursiveComparison()
            .ignoringFields("value", "functionReference", "valueAfter", "functionReferenceAfter")
            .isEqualTo(sortedSensitivityList);
    }

    @Test
    void csvExportTest() throws Exception {
        UUID resultUuid = run();
        checkComputationSucceeded(resultUuid);

        // export results as csv
        SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos = SensitivityAnalysisCsvFileInfos.builder()
            .sensitivityFunctionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .resultTab(ResultTab.N)
            .csvHeaders(List.of("functionId", "variableId", "functionReference", "value"))
            .build();

        // Not found with random UUID
        exportCsvFails(UUID.randomUUID(), sensitivityAnalysisCsvFileInfos, status().isNotFound());
        // Bad request with empty CSV file infos
        exportCsvFails(resultUuid, SensitivityAnalysisCsvFileInfos.builder().build(), status().isBadRequest());

        byte[] zipFile = exportCsv(resultUuid, sensitivityAnalysisCsvFileInfos);
        byte[] csvFile = unzip(zipFile);
        String csvFileAsString = new String(csvFile, StandardCharsets.UTF_8);
        List<String> actualCsvLines = Arrays.asList(csvFileAsString.split("\n"));

        // Including "\uFEFF" indicates the UTF-8 BOM at the start.
        List<String> expectedCsvLines = new ArrayList<>(List.of("\uFEFFfunctionId,variableId,functionReference,value",
            "NHV1_NHV2_1,GEN,302.46836311715884,4.8631143771583115E-5",
            "NHV1_NHV2_1,GEN2,302.46836311715884,4.8631143771583115E-5",
            "NHV1_NHV2_2,GEN,302.46836311715884,4.8631143771583115E-5",
            "NHV1_NHV2_2,GEN2,302.46836311715884,4.8631143771583115E-5"));

        actualCsvLines.sort(String::compareTo);
        expectedCsvLines.sort(String::compareTo);
        assertEquals(expectedCsvLines, actualCsvLines);
    }

    @Test
    void deleteResultTest() throws Exception {
        UUID resultUuid = run();
        checkComputationSucceeded(resultUuid);

        mockMvc.perform(delete("/" + VERSION + "/results/{resultUuid}", resultUuid))
            .andExpect(status().isOk());
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteResultsTest() throws Exception {
        UUID resultUuid = run();
        checkComputationSucceeded(resultUuid);

        mockMvc.perform(delete("/" + VERSION + "/results", resultUuid))
            .andExpect(status().isOk());
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    @Test
    void testStatus() throws Exception {
        // TODO: shouldn't it be not found ?
        MvcResult result = mockMvc.perform(get(
                "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals("", result.getResponse().getContentAsString());

        mockMvc.perform(put("/" + VERSION + "/results/invalidate-status?resultUuid=" + RESULT_UUID))
            .andExpect(status().isOk());

        result = mockMvc.perform(get(
                "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(SensitivityAnalysisStatus.NOT_DONE.name(), result.getResponse().getContentAsString());
    }

    @Test
    void testGetFactorsCount() throws Exception {
        // Need an extra mock for this case
        SensitivityFactorsIdsByGroup factorsIdsByGroupWithoutContingencies = SensitivityFactorsIdsByGroup.builder()
            .ids(Map.of(
                MONITORED_BRANCHES_KEY, List.of(BRANCH1_CONTAINER_UUID, BRANCH2_CONTAINER_UUID),
                INJECTIONS_KEY, List.of(GEN1_CONTAINER_UUID, GEN2_CONTAINER_UUID))
            )
            .build();
        given(filterService.getIdentifiablesCount(eq(factorsIdsByGroupWithoutContingencies), any(), any())).willReturn(Map.of(MONITORED_BRANCHES_KEY, (long) 2, INJECTIONS_KEY, (long) 2));

        MockHttpServletRequestBuilder requestBuilder = get("/" + VERSION + "/networks/{networkUuid}/factors-count", NETWORK_UUID);

        SensitivityFactorsIdsByGroup factorsIdsByGroup = SensitivityFactorsIdsByGroup.builder()
            .ids(Map.of(
                MONITORED_BRANCHES_KEY, List.of(BRANCH1_CONTAINER_UUID, BRANCH2_CONTAINER_UUID),
                INJECTIONS_KEY, List.of(GEN1_CONTAINER_UUID, GEN2_CONTAINER_UUID),
                CONTINGENCIES_KEY, List.of(CONTINGENCY1_CONTAINER_UUID, CONTINGENCY2_CONTAINER_UUID))
            )
            .build();

        factorsIdsByGroup.getIds().forEach((key, list) -> requestBuilder.param(String.format("ids[%s]", key), list.stream().map(UUID::toString).toArray(String[]::new)));
        MvcResult result = mockMvc.perform(requestBuilder)
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals("8", result.getResponse().getContentAsString());
    }

    @Test
    void stopTest() throws Exception {
        UUID resultUuid = run();
        mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop", resultUuid).param("receiver", "me"));
        checkComputationFailed(resultUuid, "sensitivityanalysis.stopped", getCancelMessage(COMPUTATION_TYPE));
        queryResultFails(resultUuid, status().isNotFound());
    }

    @Test
    void runTestWithError() throws Exception {
        UUID resultUuid = run(NETWORK_ERROR_UUID);
        checkComputationFailed(resultUuid, "sensitivityanalysis.failed", getFailedMessage(COMPUTATION_TYPE) + " : " + ERROR_MESSAGE);
        queryResultFails(resultUuid, status().isNotFound());
    }

    @Test
    void getProvidersTest() throws Exception {
        mockMvc.perform(get("/" + VERSION + "/providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string("[\"OpenLoadFlow\"]"))
            .andReturn();
    }

    @Test
    void getDefaultProviderTest() throws Exception {
        mockMvc.perform(get("/" + VERSION + "/default-provider"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8)))
            .andExpect(content().string("OpenLoadFlow"))
            .andReturn();
    }

    private SensitivityAnalysisResult runInMemory() throws Exception {
        MockHttpServletRequestBuilder req = post("/" + VERSION + "/networks/{networkUuid}/run", NETWORK_UUID)
            .param("reportType", "SensitivityAnalysis")
            .param("parametersUuid", PARAMETERS_UUID.toString())
            .param("loadFlowParametersUuid", LOADFLOW_PARAMETERS_UUID.toString());
        MvcResult result = mockMvc.perform(req.contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), SensitivityAnalysisResult.class);
    }

    private UUID run() throws Exception {
        return run(NETWORK_UUID);
    }

    private UUID run(UUID networkUuid) throws Exception {
        MockHttpServletRequestBuilder req = post("/" + VERSION + "/networks/{networkUuid}/run-and-save", networkUuid)
            .param("reportType", "SensitivityAnalysis")
            .param("receiver", "me")
            .param("parametersUuid", PARAMETERS_UUID.toString())
            .param("loadFlowParametersUuid", LOADFLOW_PARAMETERS_UUID.toString());
        MvcResult result = mockMvc.perform(req.contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), UUID.class);
    }

    private void checkComputationSucceeded(UUID resultUuid) {
        Message<byte[]> resultMessage = output.receive(TIMEOUT, "sensitivityanalysis.result");
        assertEquals(resultUuid.toString(), resultMessage.getHeaders().get("resultUuid"));
        assertEquals("me", resultMessage.getHeaders().get("receiver"));
    }

    private void checkComputationFailed(UUID resultUuid, String bindingName, String messageStr) {
        Message<byte[]> message = output.receive(TIMEOUT, bindingName);
        assertEquals(resultUuid.toString(), message.getHeaders().get("resultUuid"));
        assertEquals("me", message.getHeaders().get("receiver"));
        assertEquals(messageStr, message.getHeaders().get("message"));
    }

    private SensitivityRunQueryResult queryResult(UUID resultUuid, ResultsSelector resultsSelector) throws Exception {
        MvcResult result = mockMvc
            .perform(get("/" + VERSION + "/results/{resultUuid}", resultUuid)
                .param("selector", mapper.writeValueAsString(resultsSelector)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), SensitivityRunQueryResult.class);
    }

    private void queryResultFails(UUID resultUuid, ResultMatcher resultMatcher) throws Exception {
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", resultUuid)).andExpect(resultMatcher);
    }

    private SensitivityResultFilterOptions queryFilterOptions(UUID resultUuid, ResultsSelector resultsSelector) throws Exception {
        MvcResult result = mockMvc
            .perform(get("/" + VERSION + "/results/{resultUuid}/filter-options", resultUuid)
                .param("selector", mapper.writeValueAsString(resultsSelector)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), SensitivityResultFilterOptions.class);
    }

    private byte[] exportCsv(UUID resultUuid, SensitivityAnalysisCsvFileInfos csvFileInfos) throws Exception {
        MvcResult result = mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", resultUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(csvFileInfos)))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private void exportCsvFails(UUID resultUuid, SensitivityAnalysisCsvFileInfos csvFileInfos, ResultMatcher resultMatcher) throws Exception {
        mockMvc.perform(post("/" + VERSION + "/results/{resultUuid}/csv", resultUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(csvFileInfos)))
            .andExpect(resultMatcher)
            .andReturn();
    }
}

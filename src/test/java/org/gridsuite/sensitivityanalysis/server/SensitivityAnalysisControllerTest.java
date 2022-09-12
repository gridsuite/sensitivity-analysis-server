/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.BusbarSectionContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.DanglingLineContingency;
import com.powsybl.contingency.GeneratorContingency;
import com.powsybl.contingency.HvdcLineContingency;
import com.powsybl.contingency.LineContingency;
import com.powsybl.contingency.ShuntCompensatorContingency;
import com.powsybl.contingency.StaticVarCompensatorContingency;
import com.powsybl.contingency.ThreeWindingsTransformerContingency;
import com.powsybl.contingency.TwoWindingsTransformerContingency;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;

import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.SensitivityVariableType;
import lombok.SneakyThrows;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.service.ActionsService;
import org.gridsuite.sensitivityanalysis.server.service.FilterService;
import org.gridsuite.sensitivityanalysis.server.service.ReportService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisWorkerService;
import org.gridsuite.sensitivityanalysis.server.service.UuidGeneratorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.FAIL_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {SensitivityAnalysisApplication.class, TestChannelBinderConfiguration.class})})
public class SensitivityAnalysisControllerTest {

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID NETWORK_STOP_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID REPORT_UUID = UUID.fromString("0c4de370-3e6a-4d72-b292-d355a97e0d53");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final UUID NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("11111111-7977-4592-ba19-88027e4254e4");
    private static final UUID OTHER_NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("22222222-7977-4592-ba19-88027e4254e4");

    private static final UUID CONTINGENCY_LIST_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST2_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST_ERROR_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCY_LIST_UUID_VARIANT = UUID.randomUUID();

    private static final List<Contingency> CONTINGENCIES = List.of(
        new Contingency("l1", new BranchContingency("l1")),
        new Contingency("l2", new GeneratorContingency("l2")),
        new Contingency("l3", new BusbarSectionContingency("l3")),
        new Contingency("l4", new LineContingency("l4")),
        new Contingency("l6", new HvdcLineContingency("l6")),
        new Contingency("l7", new DanglingLineContingency("l7")),
        new Contingency("l8", new ShuntCompensatorContingency("l8")),
        new Contingency("l9", new TwoWindingsTransformerContingency("l9")),
        new Contingency("la", new ThreeWindingsTransformerContingency("l0")), // Contingencies are reordered by id
        new Contingency("lb", new StaticVarCompensatorContingency("la"))
    );
    private static final List<Contingency> CONTINGENCIES_VARIANT = List.of(
        new Contingency("l3", new BusbarSectionContingency("l3")),
        new Contingency("l4", new LineContingency("l4"))
    );

    private static final UUID VARIABLES_LIST_UUID = UUID.randomUUID();
    private static final UUID VARIABLES_LIST_UUID_VARIANT = UUID.randomUUID();
    private static final UUID VARIABLES_LIST_UUID_MERGING_VIEW = UUID.randomUUID();

    private static final List<IdentifiableAttributes> VARIABLES = List.of(
        new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR),
        new IdentifiableAttributes("GEN2", IdentifiableType.GENERATOR),
        new IdentifiableAttributes("LOAD", IdentifiableType.LOAD),
        new IdentifiableAttributes("2WT", IdentifiableType.TWO_WINDINGS_TRANSFORMER),
        new IdentifiableAttributes("3WT", IdentifiableType.THREE_WINDINGS_TRANSFORMER),
        new IdentifiableAttributes("HVDC", IdentifiableType.HVDC_LINE)
    );
    private static final List<IdentifiableAttributes> VARIABLES_VARIANT = List.of(
        new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR),
        new IdentifiableAttributes("LOAD", IdentifiableType.LOAD)
    );
    private static final List<IdentifiableAttributes> VARIABLES_MERGING_VIEW = Collections.emptyList();

    private static final UUID BRANCHES_LIST_UUID = UUID.randomUUID();
    private static final UUID BRANCHES_LIST_UUID_VARIANT = UUID.randomUUID();
    private static final UUID BRANCHES_LIST_UUID_MERGING_VIEW = UUID.randomUUID();

    private static final List<IdentifiableAttributes> BRANCHES = List.of(
        new IdentifiableAttributes("v1", IdentifiableType.LINE),
        new IdentifiableAttributes("v2", IdentifiableType.LINE),
        new IdentifiableAttributes("v3", IdentifiableType.TWO_WINDINGS_TRANSFORMER),
        new IdentifiableAttributes("v4", IdentifiableType.LINE)
    );
    private static final List<IdentifiableAttributes> BRANCHES_VARIANT = List.of(
        new IdentifiableAttributes("v1", IdentifiableType.TWO_WINDINGS_TRANSFORMER),
        new IdentifiableAttributes("v2", IdentifiableType.LINE)
    );
    private static final List<IdentifiableAttributes> BRANCHES_MERGING_VIEW = Collections.emptyList();

    private static final List<SensitivityFactor> SENSITIVITY_FACTORS = List.of(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "l",
        SensitivityVariableType.INJECTION_ACTIVE_POWER, "g",
        false, ContingencyContext.all()));
    private static final List<SensitivityFactor> SENSITIVITY_FACTORS_VARIANT = List.of(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "l2",
        SensitivityVariableType.INJECTION_ACTIVE_POWER, "g2",
        false, ContingencyContext.none()));

    private static final List<SensitivityValue> SENSITIVITY_VALUES = List.of(new SensitivityValue(0, 0, 1d, 2d));
    private static final List<SensitivityValue> SENSITIVITY_VALUES_VARIANT = List.of(new SensitivityValue(0, 0, 3d, 4d));

    private static final SensitivityAnalysisResult RESULT = new SensitivityAnalysisResult(SENSITIVITY_FACTORS,
        CONTINGENCIES,
        SENSITIVITY_VALUES);

    private static final SensitivityAnalysisResult RESULT_VARIANT = new SensitivityAnalysisResult(SENSITIVITY_FACTORS_VARIANT, CONTINGENCIES_VARIANT, SENSITIVITY_VALUES_VARIANT);

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";

    private static final int TIMEOUT = 1000;

    private static final String ERROR_MESSAGE = "Error message test";

    @Autowired
    private OutputDestination output;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private ActionsService actionsService;

    @MockBean
    private FilterService filterService;

    @MockBean
    private ReportService reportService;

    @MockBean
    private UuidGeneratorService uuidGeneratorService;

    @SpyBean
    private SensitivityAnalysisWorkerService workerService;

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper mapper = restTemplateConfig.objectMapper();

    private Network network;
    private Network network1;
    private Network networkForMergingView;
    private Network otherNetworkForMergingView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);

        networkForMergingView = new NetworkFactoryImpl().createNetwork("mergingView", "test");
        given(networkStoreService.getNetwork(NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.COLLECTION)).willReturn(networkForMergingView);

        otherNetworkForMergingView = new NetworkFactoryImpl().createNetwork("other", "test 2");
        given(networkStoreService.getNetwork(OTHER_NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.COLLECTION)).willReturn(otherNetworkForMergingView);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        when(networkStoreService.getNetwork(NETWORK_STOP_UUID, PreloadingStrategy.COLLECTION)).thenAnswer((Answer<?>) invocation -> {
            //Needed so the stop call doesn't arrive too late
            Thread.sleep(3000);
            return network1;
        });

        // action service mocking
        given(actionsService.getContingencyList(CONTINGENCY_LIST_UUID, NETWORK_UUID, VARIANT_1_ID))
                .willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCY_LIST_UUID_VARIANT, NETWORK_UUID, VARIANT_3_ID))
            .willReturn(CONTINGENCIES_VARIANT);
        given(actionsService.getContingencyList(CONTINGENCY_LIST_UUID, NETWORK_UUID, VARIANT_2_ID))
            .willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCY_LIST_UUID, NETWORK_UUID, null))
            .willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCY_LIST2_UUID, NETWORK_UUID, VARIANT_1_ID))
                .willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCY_LIST_UUID, NETWORK_STOP_UUID, VARIANT_2_ID))
                .willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCY_LIST2_UUID, NETWORK_STOP_UUID, VARIANT_2_ID))
                .willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCY_LIST_ERROR_UUID, NETWORK_UUID, VARIANT_1_ID))
                .willThrow(new RuntimeException(ERROR_MESSAGE));
        given(actionsService.getContingencyList(CONTINGENCY_LIST_UUID, NETWORK_FOR_MERGING_VIEW_UUID, null))
            .willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCY_LIST_UUID, OTHER_NETWORK_FOR_MERGING_VIEW_UUID, null))
            .willReturn(CONTINGENCIES);

        // filter service mocking for variables
        given(filterService.getIdentifiablesFromFilter(VARIABLES_LIST_UUID, NETWORK_UUID, VARIANT_1_ID))
                .willReturn(VARIABLES);
        given(filterService.getIdentifiablesFromFilter(VARIABLES_LIST_UUID_VARIANT, NETWORK_UUID, VARIANT_3_ID))
            .willReturn(VARIABLES_VARIANT);
        given(filterService.getIdentifiablesFromFilter(VARIABLES_LIST_UUID, NETWORK_UUID, VARIANT_2_ID))
            .willReturn(VARIABLES);
        given(filterService.getIdentifiablesFromFilter(VARIABLES_LIST_UUID, NETWORK_UUID, null))
            .willReturn(VARIABLES);
        given(filterService.getIdentifiablesFromFilter(VARIABLES_LIST_UUID, NETWORK_STOP_UUID, VARIANT_2_ID))
            .willReturn(VARIABLES);
        given(filterService.getIdentifiablesFromFilter(VARIABLES_LIST_UUID_MERGING_VIEW, NETWORK_UUID, null))
            .willReturn(VARIABLES_MERGING_VIEW);

        // filter service mocking for branch
        given(filterService.getIdentifiablesFromFilter(BRANCHES_LIST_UUID, NETWORK_UUID, VARIANT_1_ID))
            .willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(BRANCHES_LIST_UUID_VARIANT, NETWORK_UUID, VARIANT_3_ID))
            .willReturn(BRANCHES_VARIANT);
        given(filterService.getIdentifiablesFromFilter(BRANCHES_LIST_UUID, NETWORK_UUID, VARIANT_2_ID))
            .willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(BRANCHES_LIST_UUID, NETWORK_UUID, null))
            .willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(BRANCHES_LIST_UUID, NETWORK_STOP_UUID, VARIANT_2_ID))
            .willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(BRANCHES_LIST_UUID_MERGING_VIEW, NETWORK_UUID, null))
            .willReturn(BRANCHES_MERGING_VIEW);

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

        // mock the powsybl sensitivity analysis runner
        SensitivityAnalysis.Runner runner = mock(SensitivityAnalysis.Runner.class);
        given(runner.runAsync(eq(network), eq(VariantManagerConstants.INITIAL_VARIANT_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
        given(runner.runAsync(eq(network), eq(VARIANT_1_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
        given(runner.runAsync(eq(network), eq(VARIANT_2_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
        given(runner.runAsync(eq(network), eq(VARIANT_3_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT_VARIANT));
        given(runner.runAsync(eq(network1), eq(VARIANT_2_ID), anyList(), anyList(), anyList(), any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class))).willReturn(CompletableFuture.completedFuture(RESULT));
        workerService.setSensitivityAnalysisFactorySupplier(provider -> runner);

        // purge messages
        while (output.receive(1000, "sensitivityanalysis.result") != null) {
        }
        // purge messages
        while (output.receive(1000, "sensitivityanalysis.run") != null) {
        }
        while (output.receive(1000, "sensitivityanalysis.cancel") != null) {
        }
        while (output.receive(1000, "sensitivityanalysis.stopped") != null) {
        }
        while (output.receive(1000, "sensitivityanalysis.failed") != null) {
        }
    }

    // added for testStatus can return null, after runTest
    @SneakyThrows
    @After
    public void tearDown() {
        mockMvc.perform(delete("/" + VERSION + "/results"))
            .andExpect(status().isOk());
    }

    @Test
    public void runTest() throws Exception {
        // run with specific variant
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run?contingencyListUuid=" + CONTINGENCY_LIST_UUID_VARIANT +
                    "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID_VARIANT + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID_VARIANT +
                    "&variantId=" + VARIANT_3_ID, NETWORK_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(mapper.writeValueAsString(RESULT_VARIANT), result.getResponse().getContentAsString());

        // run with implicit initial variant
        result = mockMvc.perform(post(
            "/" + VERSION + "/networks/{networkUuid}/run?contingencyListUuid=" + CONTINGENCY_LIST_UUID +
                    "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID, NETWORK_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(mapper.writeValueAsString(RESULT), result.getResponse().getContentAsString());
    }

    @Test
    public void runAndSaveTest() throws Exception {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run-and-save?contingencyListUuid=" + CONTINGENCY_LIST_UUID +
                    "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID
                        + "&receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        Message<byte[]> resultMessage = output.receive(TIMEOUT, "sensitivityanalysis.result");
        assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
        assertEquals("me", resultMessage.getHeaders().get("receiver"));

        result = mockMvc.perform(get(
                "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(mapper.writeValueAsString(RESULT), result.getResponse().getContentAsString());

        // should throw not found if result does not exist
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", OTHER_RESULT_UUID))
            .andExpect(status().isNotFound());

        // test one result deletion
        mockMvc.perform(delete("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isOk());

        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    public void deleteResultsTest() {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run-and-save?contingencyListUuid=" + CONTINGENCY_LIST_UUID +
                    "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID, NETWORK_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        output.receive(TIMEOUT, "sensitivityanalysis.result");

        mockMvc.perform(delete("/" + VERSION + "/results"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    public void mergingViewTest() {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run?contingencyListUuid=" + CONTINGENCY_LIST_UUID +
                    "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID_MERGING_VIEW + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID_MERGING_VIEW +
                    "&networkUuid=" + OTHER_NETWORK_FOR_MERGING_VIEW_UUID, NETWORK_FOR_MERGING_VIEW_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals("", result.getResponse().getContentAsString());
    }

    @SneakyThrows
    @Test
    public void testStatus() {
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
    public void stopTest() throws Exception {
        mockMvc.perform(post(
            "/" + VERSION + "/networks/{networkUuid}/run-and-save?contingencyListUuid=" + CONTINGENCY_LIST_UUID +
                "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID +
                "&receiver=me&variantId=" + VARIANT_2_ID, NETWORK_STOP_UUID))
            .andExpect(status().isOk());

        // stop sensitivity analysis
        assertNotNull(output.receive(TIMEOUT, "sensitivityanalysis.run"));
        mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID))
            .andExpect(status().isOk());
        assertNotNull(output.receive(TIMEOUT, "sensitivityanalysis.cancel"));

        Message<byte[]> message = output.receive(TIMEOUT, "sensitivityanalysis.stopped");
        assertNotNull(message);
        assertEquals(RESULT_UUID.toString(), message.getHeaders().get("resultUuid"));
        assertEquals("me", message.getHeaders().get("receiver"));
        assertEquals(CANCEL_MESSAGE, message.getHeaders().get("message"));
    }

    @SneakyThrows
    @Test
    public void runTestWithError() {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run-and-save?contingencyListUuid=" + CONTINGENCY_LIST_ERROR_UUID +
                    "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID +
                    "&receiver=me&variantId=" + VARIANT_1_ID, NETWORK_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        // Message stopped has been sent
        Message<byte[]> cancelMessage = output.receive(TIMEOUT, "sensitivityanalysis.failed");
        assertEquals(RESULT_UUID.toString(), cancelMessage.getHeaders().get("resultUuid"));
        assertEquals("me", cancelMessage.getHeaders().get("receiver"));
        assertEquals(FAIL_MESSAGE + " : " + ERROR_MESSAGE, cancelMessage.getHeaders().get("message"));

        // No result
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    public void runWithReportTest() {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run?contingencyListUuid=" + CONTINGENCY_LIST_UUID +
                    "&variablesFiltersListUuid=" + VARIABLES_LIST_UUID + "&branchFiltersListUuid=" + BRANCHES_LIST_UUID +
                    "&reportUuid=" + REPORT_UUID, NETWORK_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(mapper.writeValueAsString(RESULT), result.getResponse().getContentAsString());
    }
}

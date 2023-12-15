/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.BusbarSectionContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
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
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.SortKey;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
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
import org.springframework.beans.factory.annotation.Value;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.FAIL_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NETWORK_STOP_UUID = UUID.randomUUID();
    private static final UUID NETWORK_ERROR_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID REPORT_UUID = UUID.randomUUID();
    private static final UUID OTHER_RESULT_UUID = UUID.randomUUID();
    private static final UUID MONITORED_BRANCHES_FILTERS_INJECTIONS_SET_UUID = UUID.randomUUID();
    private static final UUID GENERATORS_FILTERS_INJECTIONS_SET_UUID = UUID.randomUUID();
    private static final UUID LOADS_FILTERS_INJECTIONS_SET_UUID = UUID.randomUUID();
    private static final UUID LOADS_FILTERS_INJECTIONS_SET_WITH_BAD_DISTRIBUTION_TYPE_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCIES_INJECTIONS_SET_UUID = UUID.randomUUID();
    private static final UUID MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID = UUID.randomUUID();
    private static final UUID GENERATORS_FILTERS_INJECTIONS_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCIES_INJECTIONS_UUID1 = UUID.randomUUID();
    private static final UUID CONTINGENCIES_INJECTIONS_UUID2 = UUID.randomUUID();
    private static final UUID MONITORED_BRANCHES_FILTERS_HVDC_UUID = UUID.randomUUID();
    private static final UUID HVDC_FILTERS_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCIES_HVDCS_UUID = UUID.randomUUID();
    private static final UUID MONITORED_BRANCHES_FILTERS_PST_UUID = UUID.randomUUID();
    private static final UUID PST_FILTERS_UUID1 = UUID.randomUUID();
    private static final UUID PST_FILTERS_UUID2 = UUID.randomUUID();
    private static final UUID CONTINGENCIES_PSTS_UUID = UUID.randomUUID();
    private static final UUID MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID = UUID.randomUUID();
    private static final UUID EQUIPMENTS_IN_VOLTAGE_REGULATION_FILTERS_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCIES_NODES_UUID = UUID.randomUUID();
    private static final List<UUID> MONITORED_BRANCHES_FILTERS_UUID = List.of(UUID.randomUUID());
    private static final List<UUID> INJECTIONS_FILTERS_UUID = List.of(UUID.randomUUID());
    private static final List<UUID> CONTINGENCIES_FILTERS_UUID = List.of(UUID.randomUUID());
    public static final String MONITORED_BRANCHS_KEY = "monitoredBranchs";
    public static final String INJECTIONS_KEY = "injections";
    public static final String CONTINGENCIES_KEY = "contingencies";
    private static final Map<String, List<UUID>> IDS = Map.of(MONITORED_BRANCHS_KEY, MONITORED_BRANCHES_FILTERS_UUID, INJECTIONS_KEY, INJECTIONS_FILTERS_UUID, CONTINGENCIES_KEY, CONTINGENCIES_FILTERS_UUID);
    private static final Map<String, List<UUID>> IDS_1 = Map.of(MONITORED_BRANCHS_KEY, MONITORED_BRANCHES_FILTERS_UUID, INJECTIONS_KEY, INJECTIONS_FILTERS_UUID);

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
    private static final List<SensitivityAnalysisResult.SensitivityContingencyStatus> CONTINGENCIES_STATUSES = CONTINGENCIES.stream()
            .map(c -> new SensitivityAnalysisResult.SensitivityContingencyStatus(c.getId(), SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

    private static final List<Contingency> CONTINGENCIES_VARIANT = List.of(
        new Contingency("l3", new BusbarSectionContingency("l3")),
        new Contingency("l4", new LineContingency("l4"))
    );
    private static final List<SensitivityAnalysisResult.SensitivityContingencyStatus> CONTINGENCIES_VARIANT_STATUSES = CONTINGENCIES_VARIANT.stream()
            .map(c -> new SensitivityAnalysisResult.SensitivityContingencyStatus(c.getId(), SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

    private static final List<IdentifiableAttributes> GENERATORS = List.of(
        new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("GEN2", IdentifiableType.GENERATOR, null)
    );

    private static final List<IdentifiableAttributes> GENERATORS_VARIANT = List.of(
        new IdentifiableAttributes("GEN2", IdentifiableType.GENERATOR, null)
    );

    private static final List<IdentifiableAttributes> LOADS = List.of(
        new IdentifiableAttributes("LOAD", IdentifiableType.LOAD, null)
    );

    private static final List<IdentifiableAttributes> LOADS_VARIANT = List.of(
        new IdentifiableAttributes("LOAD", IdentifiableType.LOAD, null)
    );

    private static final List<IdentifiableAttributes> BRANCHES = List.of(
        new IdentifiableAttributes("l1", IdentifiableType.LINE, null),
        new IdentifiableAttributes("l2", IdentifiableType.LINE, null),
        new IdentifiableAttributes("l3", IdentifiableType.TWO_WINDINGS_TRANSFORMER, null),
        new IdentifiableAttributes("l4", IdentifiableType.LINE, null)
    );
    private static final List<IdentifiableAttributes> BRANCHES_VARIANT = List.of(
        new IdentifiableAttributes("l1", IdentifiableType.TWO_WINDINGS_TRANSFORMER, null),
        new IdentifiableAttributes("l2", IdentifiableType.LINE, null)
    );

    private static final List<IdentifiableAttributes> HVDCS = List.of(
        new IdentifiableAttributes("hvdc1", IdentifiableType.HVDC_LINE, null)
    );
    private static final List<IdentifiableAttributes> HVDCS_VARIANT = List.of(
        new IdentifiableAttributes("hvdc2", IdentifiableType.HVDC_LINE, null)
    );

    private static final List<IdentifiableAttributes> PSTS = List.of(
        new IdentifiableAttributes("t1", IdentifiableType.TWO_WINDINGS_TRANSFORMER, null)
    );
    private static final List<IdentifiableAttributes> PSTS_VARIANT = List.of(
        new IdentifiableAttributes("t2", IdentifiableType.TWO_WINDINGS_TRANSFORMER, null)
    );

    private static final List<IdentifiableAttributes> VOLTAGE_LEVELS = List.of(
        new IdentifiableAttributes("VLGEN", IdentifiableType.VOLTAGE_LEVEL, null)
    );
    private static final List<IdentifiableAttributes> VOLTAGE_LEVELS_VARIANT = List.of(
        new IdentifiableAttributes("VLHV1", IdentifiableType.VOLTAGE_LEVEL, null),
        new IdentifiableAttributes("VLHV2", IdentifiableType.VOLTAGE_LEVEL, null)
    );

    private static final List<IdentifiableAttributes> EQUIPMENTS_IN_VOLTAGE_REGULATION = List.of(
        new IdentifiableAttributes("e1", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("e2", IdentifiableType.GENERATOR, null)
    );
    private static final List<IdentifiableAttributes> EQUIPMENTS_IN_VOLTAGE_REGULATION_VARIANT = List.of(
        new IdentifiableAttributes("e3", IdentifiableType.GENERATOR, null)
    );

    private static final List<SensitivityFactor> SENSITIVITY_FACTORS = List.of(
        new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "l1",
            SensitivityVariableType.INJECTION_ACTIVE_POWER, "GEN", false, ContingencyContext.all()),
        new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "l2",
            SensitivityVariableType.INJECTION_ACTIVE_POWER, "GEN", false, ContingencyContext.create("l1", ContingencyContextType.SPECIFIC)),
        new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "l3",
            SensitivityVariableType.INJECTION_ACTIVE_POWER, "LOAD", false, ContingencyContext.create("l3", ContingencyContextType.SPECIFIC))
    );
    private static final List<SensitivityFactor> SENSITIVITY_FACTORS_VARIANT = List.of(
        new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "l2",
            SensitivityVariableType.INJECTION_ACTIVE_POWER, "GEN2", false, ContingencyContext.none()));

    private static final List<SensitivityValue> SENSITIVITY_VALUES = List.of(
        new SensitivityValue(0, -1, 500.1, 2.9),
        new SensitivityValue(1, -1, 500.2, 2.8),
        new SensitivityValue(0, 0, 500.3, 2.7),
        new SensitivityValue(0, 1, 500.4, 2.6),
        new SensitivityValue(0, 2, 500.5, 2.5),
        new SensitivityValue(1, 1, 500.6, 2.4),
        new SensitivityValue(2, 0, 500.7, 2.3),
        new SensitivityValue(1, 2, 500.8, 2.2)
    );
    private static final List<SensitivityValue> SENSITIVITY_VALUES_VARIANT = List.of(new SensitivityValue(0, 0, 3d, 4d));

    private static final SensitivityAnalysisResult RESULT = new SensitivityAnalysisResult(SENSITIVITY_FACTORS,
        CONTINGENCIES_STATUSES,
        SENSITIVITY_VALUES);

    private static final SensitivityAnalysisResult RESULT_VARIANT = new SensitivityAnalysisResult(SENSITIVITY_FACTORS_VARIANT, CONTINGENCIES_VARIANT_STATUSES, SENSITIVITY_VALUES_VARIANT);

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";

    private static final int TIMEOUT = 1000;

    private static final String ERROR_MESSAGE = "Error message test";

    private static String SENSITIVITY_INPUT_1;
    private static String SENSITIVITY_INPUT_2;
    private static String SENSITIVITY_INPUT_3;
    private static String SENSITIVITY_INPUT_4;
    private static String SENSITIVITY_INPUT_5;
    private static String SENSITIVITY_INPUT_6;
    private static String SENSITIVITY_INPUT_HVDC_DELTA_A;
    private static String SENSITIVITY_INPUT_LOAD_PROPORTIONAL_MAXP;
    private static String SENSITIVITY_INPUT_VENTILATION;

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

    @Value("${sensitivity-analysis.default-provider}")
    String defaultSensitivityAnalysisProvider;

    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    private final ObjectMapper mapper = restTemplateConfig.objectMapper();

    private Network network;
    private Network network1;

    @Before
    @SneakyThrows
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getGenerator("GEN").getTerminal().setP(10.);
        network.getGenerator("GEN2").getTerminal().setP(100.);
        network.getLoad("LOAD").getTerminal().setP(50.);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);
        given(networkStoreService.getNetwork(NETWORK_ERROR_UUID, PreloadingStrategy.COLLECTION)).willThrow(new RuntimeException(ERROR_MESSAGE));

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        when(networkStoreService.getNetwork(NETWORK_STOP_UUID, PreloadingStrategy.COLLECTION)).thenAnswer((Answer<?>) invocation -> {
            //Needed so the stop call doesn't arrive too late
            Thread.sleep(3000);
            return network1;
        });

        SensitivityAnalysisInputData sensitivityAnalysisInputData1 = SensitivityAnalysisInputData.builder()
            .sensitivityInjectionsSets(List.of(SensitivityInjectionsSet.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(MONITORED_BRANCHES_FILTERS_INJECTIONS_SET_UUID, "name1")))
                .injections(List.of(new EquipmentsContainer(GENERATORS_FILTERS_INJECTIONS_SET_UUID, "name2"), new EquipmentsContainer(LOADS_FILTERS_INJECTIONS_SET_UUID, "name3")))
                .distributionType(SensitivityAnalysisInputData.DistributionType.REGULAR)
                .contingencies(List.of(new EquipmentsContainer(CONTINGENCIES_INJECTIONS_SET_UUID, "name4"))).build()))
            .sensitivityInjections(List.of(SensitivityInjection.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, "name5")))
                .injections(List.of(new EquipmentsContainer(GENERATORS_FILTERS_INJECTIONS_UUID, "name6")))
                .contingencies(List.of(new EquipmentsContainer(CONTINGENCIES_INJECTIONS_UUID1, "name7"), new EquipmentsContainer(CONTINGENCIES_INJECTIONS_UUID2, "name8"))).build()))
            .sensitivityHVDCs(List.of(SensitivityHVDC.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(MONITORED_BRANCHES_FILTERS_HVDC_UUID, "name9")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_MW)
                .hvdcs(List.of(new EquipmentsContainer(HVDC_FILTERS_UUID, "name10")))
                .contingencies(List.of(new EquipmentsContainer(CONTINGENCIES_HVDCS_UUID, "name11"))).build()))
            .sensitivityPSTs(List.of(SensitivityPST.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(MONITORED_BRANCHES_FILTERS_PST_UUID, "name12")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A)
                .psts(List.of(new EquipmentsContainer(PST_FILTERS_UUID1, "name13"), new EquipmentsContainer(PST_FILTERS_UUID2, "name14")))
                .contingencies(List.of(new EquipmentsContainer(CONTINGENCIES_PSTS_UUID, "name15"))).build()))
            .sensitivityNodes(List.of(SensitivityNodes.builder()
                .monitoredVoltageLevels(List.of(new EquipmentsContainer(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID, "name16")))
                .equipmentsInVoltageRegulation(List.of(new EquipmentsContainer(EQUIPMENTS_IN_VOLTAGE_REGULATION_FILTERS_UUID, "name17")))
                .contingencies(List.of(new EquipmentsContainer(CONTINGENCIES_NODES_UUID, "name18"))).build()))
            .parameters(SensitivityAnalysisParameters.load())
            .build();
        SENSITIVITY_INPUT_1 = mapper.writeValueAsString(sensitivityAnalysisInputData1);

        SensitivityAnalysisInputData sensitivityAnalysisInputData2 = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputData2.getSensitivityInjectionsSets().get(0).setDistributionType(SensitivityAnalysisInputData.DistributionType.PROPORTIONAL);
        SENSITIVITY_INPUT_2 = mapper.writeValueAsString(sensitivityAnalysisInputData2);

        SensitivityAnalysisInputData sensitivityAnalysisInputData3 = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputData3.getSensitivityInjectionsSets().get(0).getInjections().get(0).setContainerId(HVDC_FILTERS_UUID);
        SENSITIVITY_INPUT_3 = mapper.writeValueAsString(sensitivityAnalysisInputData3);

        SensitivityAnalysisInputData sensitivityAnalysisInputData4 = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputData4.getSensitivityInjectionsSets().get(0).getMonitoredBranches().get(0).setContainerId(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID);
        SENSITIVITY_INPUT_4 = mapper.writeValueAsString(sensitivityAnalysisInputData4);

        SensitivityAnalysisInputData sensitivityAnalysisInputData5 = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputData5.getSensitivityInjections().get(0).getMonitoredBranches().get(0).setContainerId(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID);
        SENSITIVITY_INPUT_5 = mapper.writeValueAsString(sensitivityAnalysisInputData5);

        SensitivityAnalysisInputData sensitivityAnalysisInputData6 = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputData6.getSensitivityPSTs().get(0).getPsts().get(0).setContainerId(GENERATORS_FILTERS_INJECTIONS_UUID);
        SENSITIVITY_INPUT_6 = mapper.writeValueAsString(sensitivityAnalysisInputData6);

        SensitivityAnalysisInputData sensitivityAnalysisInputDataHvdcWithDeltaA = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputDataHvdcWithDeltaA.getSensitivityHVDCs().get(0).setSensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A);
        SENSITIVITY_INPUT_HVDC_DELTA_A = mapper.writeValueAsString(sensitivityAnalysisInputDataHvdcWithDeltaA);

        SensitivityAnalysisInputData sensitivityAnalysisInputDataLoadWithProportionalMaxP = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputDataLoadWithProportionalMaxP.getSensitivityInjectionsSets().get(0).setDistributionType(SensitivityAnalysisInputData.DistributionType.PROPORTIONAL_MAXP);
        sensitivityAnalysisInputDataLoadWithProportionalMaxP.getSensitivityInjectionsSets().get(0).getInjections().get(1).setContainerId(LOADS_FILTERS_INJECTIONS_SET_WITH_BAD_DISTRIBUTION_TYPE_UUID);
        SENSITIVITY_INPUT_LOAD_PROPORTIONAL_MAXP = mapper.writeValueAsString(sensitivityAnalysisInputDataLoadWithProportionalMaxP);

        SensitivityAnalysisInputData sensitivityAnalysisInputDataVentilation = mapper.convertValue(sensitivityAnalysisInputData1, SensitivityAnalysisInputData.class);
        sensitivityAnalysisInputDataVentilation.getSensitivityInjectionsSets().get(0).setDistributionType(SensitivityAnalysisInputData.DistributionType.VENTILATION);
        SENSITIVITY_INPUT_VENTILATION = mapper.writeValueAsString(sensitivityAnalysisInputDataVentilation);

        // action service mocking
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(CONTINGENCIES_VARIANT);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_UUID1, NETWORK_UUID, VARIANT_1_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_UUID1, NETWORK_UUID, VARIANT_3_ID)).willReturn(CONTINGENCIES_VARIANT);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_UUID1, NETWORK_UUID, VARIANT_2_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_UUID2, NETWORK_UUID, VARIANT_1_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_UUID2, NETWORK_UUID, VARIANT_3_ID)).willReturn(CONTINGENCIES_VARIANT);
        given(actionsService.getContingencyList(CONTINGENCIES_INJECTIONS_UUID2, NETWORK_UUID, VARIANT_2_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_HVDCS_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_HVDCS_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(CONTINGENCIES_VARIANT);
        given(actionsService.getContingencyList(CONTINGENCIES_HVDCS_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_PSTS_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_PSTS_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(CONTINGENCIES_VARIANT);
        given(actionsService.getContingencyList(CONTINGENCIES_PSTS_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_NODES_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_NODES_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(CONTINGENCIES_VARIANT);
        given(actionsService.getContingencyList(CONTINGENCIES_NODES_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(CONTINGENCIES);
        given(actionsService.getContingencyList(CONTINGENCIES_FILTERS_UUID.get(0), NETWORK_UUID, VARIANT_1_ID)).willReturn(CONTINGENCIES_VARIANT);

        // filter service mocking
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(BRANCHES_VARIANT);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, null)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_SET_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(GENERATORS_VARIANT);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, null)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_SET_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(LOADS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(LOADS);
        given(filterService.getIdentifiablesFromFilter(LOADS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(LOADS_VARIANT);
        given(filterService.getIdentifiablesFromFilter(LOADS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(LOADS);
        given(filterService.getIdentifiablesFromFilter(LOADS_FILTERS_INJECTIONS_SET_UUID, NETWORK_UUID, null)).willReturn(LOADS);
        given(filterService.getIdentifiablesFromFilter(LOADS_FILTERS_INJECTIONS_SET_WITH_BAD_DISTRIBUTION_TYPE_UUID, NETWORK_UUID, null)).willReturn(LOADS);
        given(filterService.getIdentifiablesFromFilter(LOADS_FILTERS_INJECTIONS_SET_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(LOADS);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(BRANCHES_VARIANT);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, null)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(BRANCHES_VARIANT);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_UUID, null)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_INJECTIONS_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(GENERATORS_VARIANT);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_UUID, NETWORK_UUID, null)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(GENERATORS_FILTERS_INJECTIONS_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(GENERATORS);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_HVDC_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_HVDC_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(BRANCHES_VARIANT);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_HVDC_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_HVDC_UUID, NETWORK_UUID, null)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(MONITORED_BRANCHES_FILTERS_HVDC_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(BRANCHES);
        given(filterService.getIdentifiablesFromFilter(HVDC_FILTERS_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(HVDCS);
        given(filterService.getIdentifiablesFromFilter(HVDC_FILTERS_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(HVDCS_VARIANT);
        given(filterService.getIdentifiablesFromFilter(HVDC_FILTERS_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(HVDCS);
        given(filterService.getIdentifiablesFromFilter(HVDC_FILTERS_UUID, NETWORK_UUID, null)).willReturn(HVDCS);
        given(filterService.getIdentifiablesFromFilter(HVDC_FILTERS_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(HVDCS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID1, NETWORK_UUID, VARIANT_1_ID)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID1, NETWORK_UUID, VARIANT_3_ID)).willReturn(PSTS_VARIANT);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID1, NETWORK_UUID, VARIANT_2_ID)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID1, NETWORK_UUID, null)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID1, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID2, NETWORK_UUID, VARIANT_1_ID)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID2, NETWORK_UUID, VARIANT_3_ID)).willReturn(PSTS_VARIANT);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID2, NETWORK_UUID, VARIANT_2_ID)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID2, NETWORK_UUID, null)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(PST_FILTERS_UUID2, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(PSTS);
        given(filterService.getIdentifiablesFromFilter(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(VOLTAGE_LEVELS);
        given(filterService.getIdentifiablesFromFilter(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(VOLTAGE_LEVELS_VARIANT);
        given(filterService.getIdentifiablesFromFilter(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(VOLTAGE_LEVELS);
        given(filterService.getIdentifiablesFromFilter(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID, NETWORK_UUID, null)).willReturn(VOLTAGE_LEVELS);
        given(filterService.getIdentifiablesFromFilter(MONITORED_VOLTAGE_LEVELS_FILTERS_NODES_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(VOLTAGE_LEVELS);
        given(filterService.getIdentifiablesFromFilter(EQUIPMENTS_IN_VOLTAGE_REGULATION_FILTERS_UUID, NETWORK_UUID, VARIANT_1_ID)).willReturn(EQUIPMENTS_IN_VOLTAGE_REGULATION);
        given(filterService.getIdentifiablesFromFilter(EQUIPMENTS_IN_VOLTAGE_REGULATION_FILTERS_UUID, NETWORK_UUID, VARIANT_3_ID)).willReturn(EQUIPMENTS_IN_VOLTAGE_REGULATION_VARIANT);
        given(filterService.getIdentifiablesFromFilter(EQUIPMENTS_IN_VOLTAGE_REGULATION_FILTERS_UUID, NETWORK_UUID, VARIANT_2_ID)).willReturn(EQUIPMENTS_IN_VOLTAGE_REGULATION);
        given(filterService.getIdentifiablesFromFilter(EQUIPMENTS_IN_VOLTAGE_REGULATION_FILTERS_UUID, NETWORK_UUID, null)).willReturn(EQUIPMENTS_IN_VOLTAGE_REGULATION);
        given(filterService.getIdentifiablesFromFilter(EQUIPMENTS_IN_VOLTAGE_REGULATION_FILTERS_UUID, NETWORK_STOP_UUID, VARIANT_2_ID)).willReturn(EQUIPMENTS_IN_VOLTAGE_REGULATION);
        given(filterService.getIdentifiablesCount(IDS_1, NETWORK_UUID, null)).willReturn(Map.<String, List<Long>>of(MONITORED_BRANCHS_KEY, List.<Long>of(6L), INJECTIONS_KEY, List.<Long>of(6L)));
        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

        // mock the powsybl sensitivity analysis runner
        SensitivityAnalysis.Runner runner = mock(SensitivityAnalysis.Runner.class);
        given(runner.getName()).willReturn(defaultSensitivityAnalysisProvider);
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
                "/" + VERSION + "/networks/{networkUuid}/run?reportType=SensitivityAnalysis&variantId=" + VARIANT_3_ID, NETWORK_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(SENSITIVITY_INPUT_1))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(mapper.writeValueAsString(RESULT_VARIANT), result.getResponse().getContentAsString());

        // run with implicit initial variant
        for (String sensitivityInput : List.of(SENSITIVITY_INPUT_1, SENSITIVITY_INPUT_2, SENSITIVITY_INPUT_3, SENSITIVITY_INPUT_4, SENSITIVITY_INPUT_5, SENSITIVITY_INPUT_6, SENSITIVITY_INPUT_LOAD_PROPORTIONAL_MAXP, SENSITIVITY_INPUT_VENTILATION)) {
            result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run?reportType=SensitivityAnalysis", NETWORK_UUID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sensitivityInput))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
            assertEquals(mapper.writeValueAsString(RESULT), result.getResponse().getContentAsString());
        }

        // run with OpenLoadFlow provider and sensitivityType DELTA_A for HVDC
        result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run?reportType=SensitivityAnalysis&provider=OpenLoadFlow", NETWORK_UUID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SENSITIVITY_INPUT_HVDC_DELTA_A))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(mapper.writeValueAsString(RESULT), result.getResponse().getContentAsString());
    }

    @Test
    public void runAndSaveTest() throws Exception {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=SensitivityAnalysis&receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(SENSITIVITY_INPUT_1))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        Message<byte[]> resultMessage = output.receive(TIMEOUT, "sensitivityanalysis.result");
        assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
        assertEquals("me", resultMessage.getHeaders().get("receiver"));

        // check results can be retrieved for the without contingencies side
        // and that they can be filtered by function IDs, variable IDs
        // and sorted according to multiple criteria
        ResultsSelector selectorN = ResultsSelector.builder()
            .tabSelection(ResultTab.N)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .functionIds(BRANCHES.stream().map(IdentifiableAttributes::getId).collect(Collectors.toList()))
            .variableIds(Stream.concat(GENERATORS.stream(), LOADS.stream())
                .map(IdentifiableAttributes::getId).collect(Collectors.toList()))
            .sortKeysWithWeightAndDirection(Map.of(
                SortKey.SENSITIVITY, -1,
                SortKey.REFERENCE, 2,
                SortKey.VARIABLE, 3,
                SortKey.FUNCTION, 4))
            .build();
        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", RESULT_UUID,
                mapper.writeValueAsString(selectorN)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        SensitivityRunQueryResult resN = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(2, (long) resN.getTotalSensitivitiesCount());

        // check results can be retrieved for the with contingencies side
        // filtered and sorted by multiple criteria too
        ResultsSelector selectorNK = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .contingencyIds(CONTINGENCIES_VARIANT.stream().map(Contingency::getId).collect(Collectors.toList()))
            .functionIds(BRANCHES_VARIANT.stream().map(IdentifiableAttributes::getId).collect(Collectors.toList()))
            .variableIds(GENERATORS.stream().map(IdentifiableAttributes::getId).collect(Collectors.toList()))
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

        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", RESULT_UUID,
                mapper.writeValueAsString(selectorNK)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        String bodyText = result.getResponse().getContentAsString();
        SensitivityRunQueryResult resNK = mapper.readValue(bodyText, new TypeReference<>() { });
        assertEquals(6, (long) resNK.getTotalSensitivitiesCount());
        assertEquals(2, resNK.getSensitivities().size());

        ResultsSelector filterOptionsSelector = ResultsSelector.builder().tabSelection(ResultTab.N_K)
                .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1).build();
        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}/filter-options?selector={selector}", RESULT_UUID,
                        mapper.writeValueAsString(filterOptionsSelector)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        SensitivityResultFilterOptions filterOptions = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(3, filterOptions.getAllContingencyIds().size());
        assertEquals(3, filterOptions.getAllFunctionIds().size());
        assertEquals(2, filterOptions.getAllVariableIds().size());

        ResultsSelector filterOptionsSelector2 = ResultsSelector.builder().tabSelection(ResultTab.N)
                .functionType(SensitivityFunctionType.BRANCH_CURRENT_1).build();
        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}/filter-options?selector={selector}", RESULT_UUID,
                        mapper.writeValueAsString(filterOptionsSelector2)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        SensitivityResultFilterOptions filterOptions2 = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertNull(filterOptions2.getAllContingencyIds());
        assertEquals(0, filterOptions2.getAllFunctionIds().size());
        assertEquals(0, filterOptions2.getAllVariableIds().size());

        // check that a request for not present contingency does not crash and just brings nothing
        ResultsSelector selectorNKz1 = ResultsSelector.builder().tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1).contingencyIds(List.of("unfoundable")).build();
        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", RESULT_UUID,
                mapper.writeValueAsString(selectorNKz1)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        SensitivityRunQueryResult resNKz1 = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(0, (long) resNKz1.getTotalSensitivitiesCount());

        // check that a request for not present function does not crash and just brings nothing
        ResultsSelector selectorNKz2 = ResultsSelector.builder().tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1).functionIds(List.of("unfoundable")).build();
        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", RESULT_UUID,
                mapper.writeValueAsString(selectorNKz2)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        SensitivityRunQueryResult resNKz2 = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(0, (long) resNKz2.getTotalSensitivitiesCount());

        // check that a request for not present variable does not crash and just brings nothing
        ResultsSelector selectorNKz3 = ResultsSelector.builder().tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1).variableIds(List.of("unfoundable")).build();
        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", RESULT_UUID,
                mapper.writeValueAsString(selectorNKz3)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        SensitivityRunQueryResult resNKz3 = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(0, (long) resNKz3.getTotalSensitivitiesCount());

        // check that a request for another function type does not crash and just brings nothing
        ResultsSelector selectorNKz4 = ResultsSelector.builder().tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2).build();
        result = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", RESULT_UUID,
                mapper.writeValueAsString(selectorNKz4)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        SensitivityRunQueryResult resNKz4 = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(0, (long) resNKz4.getTotalSensitivitiesCount());

        // check that a request with a bogus selector json does not crash and raises 4xx status
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", RESULT_UUID,
                "bogusJSON"))
            .andExpect(status().is4xxClientError())
            .andReturn();

        // check that a request with a bogus uuid does not crash raises and raises 404 status
        //noinspection UnnecessaryLocalVariable
        final UUID bogusUuid = REPORT_UUID;
        mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}?selector={selector}", bogusUuid,
                mapper.writeValueAsString(selectorN)))
            .andExpect(status().isNotFound())
            .andReturn();

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
                "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=SensitivityAnalysis", NETWORK_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(SENSITIVITY_INPUT_1))
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
    public void testGetFactorsCount() throws Exception {
        MvcResult result = mockMvc.perform(post("/" + VERSION + "/networks/{networkUuid}/factors-count?variantId={variantId}", NETWORK_UUID, VARIANT_1_ID)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(IDS)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("72", result.getResponse().getContentAsString());
    }

    @Test
    public void stopTest() throws Exception {
        mockMvc.perform(post(
            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=SensitivityAnalysis&receiver=me&variantId=" + VARIANT_2_ID, NETWORK_STOP_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(SENSITIVITY_INPUT_1))
            .andExpect(status().isOk());

        // stop sensitivity analysis
        mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID))
            .andExpect(status().isOk());

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
                "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=SensitivityAnalysis&receiver=me&variantId=" + VARIANT_1_ID, NETWORK_ERROR_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(SENSITIVITY_INPUT_1))
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
                "/" + VERSION + "/networks/{networkUuid}/run?reportType=SensitivityAnalysis&reportUuid=" + REPORT_UUID + "&reporterId=" + UUID.randomUUID(), NETWORK_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(SENSITIVITY_INPUT_1))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(mapper.writeValueAsString(RESULT), result.getResponse().getContentAsString());
    }

    @Test
    public void getProvidersTest() throws Exception {
        mockMvc.perform(get("/" + VERSION + "/providers"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[\"OpenLoadFlow\",\"Hades2\"]"))
                .andReturn();
    }

    @Test
    public void getDefaultProviderTest() throws Exception {
        mockMvc.perform(get("/" + VERSION + "/default-provider"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8)))
                .andExpect(content().string("OpenLoadFlow"))
                .andReturn();
    }
}

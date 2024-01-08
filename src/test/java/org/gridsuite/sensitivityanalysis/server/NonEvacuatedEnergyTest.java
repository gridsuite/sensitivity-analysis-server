/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import lombok.SneakyThrows;
import org.gridsuite.sensitivityanalysis.server.dto.EquipmentsContainer;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyContingencies;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsCappingsByType;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsCappings;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyMonitoredBranches;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStageDefinition;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStagesSelection;
import org.gridsuite.sensitivityanalysis.server.service.ActionsService;
import org.gridsuite.sensitivityanalysis.server.service.FilterService;
import org.gridsuite.sensitivityanalysis.server.service.ReportService;
import org.gridsuite.sensitivityanalysis.server.service.UuidGeneratorService;
import org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy.NonEvacuatedEnergyWorkerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.FAIL_MESSAGE;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.HEADER_USER_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {SensitivityAnalysisApplication.class, TestChannelBinderConfiguration.class})})
public class NonEvacuatedEnergyTest {

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NETWORK_ERROR_UUID = UUID.randomUUID();
    private static final UUID NETWORK_ERROR_PERMANENT_LIMIT_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID OTHER_RESULT_UUID = UUID.randomUUID();

    private static final UUID GENERATORS_WIND_FILTER_UUID = UUID.randomUUID();
    private static final UUID GENERATORS_SOLAR_FILTER_UUID = UUID.randomUUID();
    private static final UUID GENERATORS_HYDRO_FILTER_UUID = UUID.randomUUID();
    private static final UUID CAPPING_GENERATORS_WIND_FILTER_UUID = UUID.randomUUID();
    private static final UUID CAPPING_GENERATORS_SOLAR_FILTER_UUID = UUID.randomUUID();
    private static final UUID CAPPING_GENERATORS_HYDRO_FILTER_UUID = UUID.randomUUID();
    private static final UUID MONITORED_BRANCHES_1_FILTER_UUID = UUID.randomUUID();
    private static final UUID MONITORED_BRANCHES_2_FILTER_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCIES_1_UUID = UUID.randomUUID();
    private static final UUID CONTINGENCIES_2_UUID = UUID.randomUUID();

    private static final List<Contingency> CONTINGENCIES_1 = List.of(
        new Contingency("c1", new BranchContingency("LINE_S2S3"))
    );
    private static final List<Contingency> CONTINGENCIES_2 = List.of(
        new Contingency("c2", new BranchContingency("line2"))
    );

    private static final List<SensitivityAnalysisResult.SensitivityContingencyStatus> CONTINGENCIES_STATUSES =
        Stream.concat(CONTINGENCIES_1.stream(), CONTINGENCIES_2.stream())
            .map(c -> new SensitivityAnalysisResult.SensitivityContingencyStatus(c.getId(), SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

    private static final List<IdentifiableAttributes> GENERATORS_WIND = List.of(
        new IdentifiableAttributes("GROUP1", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("GROUP2", IdentifiableType.GENERATOR, null)
    );
    private static final List<IdentifiableAttributes> GENERATORS_SOLAR = List.of(
        new IdentifiableAttributes("GTH1", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("GTH2", IdentifiableType.GENERATOR, null)
    );
    private static final List<IdentifiableAttributes> GENERATORS_HYDRO = List.of(
        new IdentifiableAttributes("ABC", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("GH1", IdentifiableType.GENERATOR, null)
    );

    private static final List<IdentifiableAttributes> CAPPING_GENERATORS_WIND = List.of(
        new IdentifiableAttributes("GROUP3", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("newGroup2", IdentifiableType.GENERATOR, null)
    );
    private static final List<IdentifiableAttributes> CAPPING_GENERATORS_SOLAR = List.of(
        new IdentifiableAttributes("TEST1", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("newGroup1", IdentifiableType.GENERATOR, null)
    );
    private static final List<IdentifiableAttributes> CAPPING_GENERATORS_HYDRO = List.of(
        new IdentifiableAttributes("GH2", IdentifiableType.GENERATOR, null),
        new IdentifiableAttributes("GH3", IdentifiableType.GENERATOR, null)
    );

    private static final List<IdentifiableAttributes> BRANCHES_1 = List.of(
        new IdentifiableAttributes("line3", IdentifiableType.LINE, null),
        new IdentifiableAttributes("line4", IdentifiableType.LINE, null)
    );
    private static final List<IdentifiableAttributes> BRANCHES_2 = List.of(
        new IdentifiableAttributes("line2", IdentifiableType.LINE, null),
        new IdentifiableAttributes("newLine", IdentifiableType.LINE, null)
    );

    private static final String VARIANT_ID = "variant_1";

    private static final int TIMEOUT = 1000;

    private static final String ERROR_MESSAGE = "Error message test";

    private static String INPUT;
    private static String INPUT_WITH_TEMPORARY_LIMIT_NOT_FOUND;

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
    private NonEvacuatedEnergyWorkerService nonEvacuatedEnergyWorkerService;

    @Value("${sensitivity-analysis.default-provider}")
    String defaultSensitivityAnalysisProvider;

    private final ObjectMapper mapper = RestTemplateConfig.objectMapper();

    private List<NonEvacuatedEnergyStageDefinition> buildStagesDefinition() {
        return List.of(NonEvacuatedEnergyStageDefinition.builder()
                .generators(List.of(new EquipmentsContainer(GENERATORS_WIND_FILTER_UUID, "generators_wind")))
                .energySource(EnergySource.WIND)
                .pMaxPercents(List.of(100F, 70F)).build(),
            NonEvacuatedEnergyStageDefinition.builder()
                .generators(List.of(new EquipmentsContainer(GENERATORS_SOLAR_FILTER_UUID, "generators_solar")))
                .energySource(EnergySource.SOLAR)
                .pMaxPercents(List.of(70F, 50F)).build(),
            NonEvacuatedEnergyStageDefinition.builder()
                .generators(List.of(new EquipmentsContainer(GENERATORS_HYDRO_FILTER_UUID, "generators_hydro")))
                .energySource(EnergySource.HYDRO)
                .pMaxPercents(List.of(50F, 30F)).build());
    }

    private List<NonEvacuatedEnergyStagesSelection> buildStagesSelection() {
        return List.of(NonEvacuatedEnergyStagesSelection.builder()
                .name("EOL_100-PV_70-HYDRO_50")
                .activated(true)
                .stagesDefinitonIndex(List.of(0, 1, 2))
                .pMaxPercentsIndex(List.of(0, 0, 0))
                .build(),
            NonEvacuatedEnergyStagesSelection.builder()
                .name("EOL_70-PV_50-HYDRO_30")
                .activated(true)
                .stagesDefinitonIndex(List.of(0, 1, 2))
                .pMaxPercentsIndex(List.of(1, 1, 1))
                .build());
    }

    private NonEvacuatedEnergyGeneratorsCappings buildGeneratorsCappings() {
        return NonEvacuatedEnergyGeneratorsCappings.builder()
            .sensitivityThreshold(0.01)
            .generators(List.of(NonEvacuatedEnergyGeneratorsCappingsByType.builder()
                    .energySource(EnergySource.WIND)
                    .activated(true)
                    .generators(List.of(new EquipmentsContainer(CAPPING_GENERATORS_WIND_FILTER_UUID, "capping_generators_wind")))
                    .build(),
                NonEvacuatedEnergyGeneratorsCappingsByType.builder()
                    .energySource(EnergySource.SOLAR)
                    .activated(true)
                    .generators(List.of(new EquipmentsContainer(CAPPING_GENERATORS_SOLAR_FILTER_UUID, "capping_generators_solar")))
                    .build(),
                NonEvacuatedEnergyGeneratorsCappingsByType.builder()
                    .energySource(EnergySource.HYDRO)
                    .activated(true)
                    .generators(List.of(new EquipmentsContainer(CAPPING_GENERATORS_HYDRO_FILTER_UUID, "capping_generators_hydro")))
                    .build()))
            .build();
    }

    private List<NonEvacuatedEnergyMonitoredBranches> buildMonitoredBranches() {
        return List.of(NonEvacuatedEnergyMonitoredBranches.builder()
                .branches(List.of(new EquipmentsContainer(MONITORED_BRANCHES_1_FILTER_UUID, "branches_1")))
                .activated(true)
                .istN(true)
                .limitNameN(null)
                .nCoefficient(100)
                .istNm1(false)
                .limitNameNm1("IT10")
                .nm1Coefficient(90)
                .build(),
            NonEvacuatedEnergyMonitoredBranches.builder()
                .branches(List.of(new EquipmentsContainer(MONITORED_BRANCHES_2_FILTER_UUID, "branches_2")))
                .activated(true)
                .istN(false)
                .limitNameN("IT5")
                .nCoefficient(90)
                .istNm1(true)
                .limitNameNm1(null)
                .nm1Coefficient(70)
                .build(),
            NonEvacuatedEnergyMonitoredBranches.builder()
                .branches(List.of(new EquipmentsContainer(MONITORED_BRANCHES_2_FILTER_UUID, "branches_2")))
                .activated(true)
                .istN(false)
                .limitNameN("IT20")
                .nCoefficient(70)
                .istNm1(true)
                .limitNameNm1(null)
                .nm1Coefficient(80)
                .build()
        );
    }

    private List<NonEvacuatedEnergyContingencies> buildContingencies() {
        return List.of(NonEvacuatedEnergyContingencies.builder()
                .activated(true)
                .contingencies(List.of(new EquipmentsContainer(CONTINGENCIES_1_UUID, "contingency_1")))
                .build(),
            NonEvacuatedEnergyContingencies.builder()
                .activated(true)
                .contingencies(List.of(new EquipmentsContainer(CONTINGENCIES_2_UUID, "contingency_2")))
                .build());
    }

    private List<SensitivityFactor> buildSensitivityFactorsResults() {
        // sensitivity variable set for each energy source (WIND, SOLAR, HYDRO)
        SensitivityVariableSet sensitivityVariableSet1 = new SensitivityVariableSet(CAPPING_GENERATORS_WIND_FILTER_UUID.toString(),
            CAPPING_GENERATORS_WIND.stream().map(g -> new WeightedSensitivityVariable(g.getId(), 1.)).toList());
        SensitivityVariableSet sensitivityVariableSet2 = new SensitivityVariableSet(CAPPING_GENERATORS_SOLAR_FILTER_UUID.toString(),
            CAPPING_GENERATORS_SOLAR.stream().map(g -> new WeightedSensitivityVariable(g.getId(), 1.)).toList());
        SensitivityVariableSet sensitivityVariableSet3 = new SensitivityVariableSet(CAPPING_GENERATORS_HYDRO_FILTER_UUID.toString(),
            CAPPING_GENERATORS_HYDRO.stream().map(g -> new WeightedSensitivityVariable(g.getId(), 1.)).toList());

        // line3 (both sides with and without contingency)
        List<SensitivityFactor> sensitivityFactorsLine3Side1WithoutContingency = List.of(
            // for each variable id (capping generator)
            new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "GROUP3", false, ContingencyContext.none()),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "newGroup2", false, ContingencyContext.none()),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "TEST1", false, ContingencyContext.none()),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "newGroup1", false, ContingencyContext.none()),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "GH2", false, ContingencyContext.none()),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "GH3", false, ContingencyContext.none()),
            // for each variable set (capping generator by energy source)
            new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, sensitivityVariableSet1.getId(), true, ContingencyContext.none()),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, sensitivityVariableSet2.getId(), true, ContingencyContext.none()),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "line3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, sensitivityVariableSet3.getId(), true, ContingencyContext.none())
        );

        List<SensitivityFactor> sensitivityFactorsLine3Side2WithoutContingency = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> {
                if (f.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, f.getFunctionId(), f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), f.getContingencyContext());
                } else {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, f.getFunctionId(), f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), f.getContingencyContext());
                }
            }).toList();

        List<SensitivityFactor> sensitivityFactorsLine3Side1WithContingency1 = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> new SensitivityFactor(f.getFunctionType(), f.getFunctionId(), f.getVariableType(),
                f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c1"))).toList();
        List<SensitivityFactor> sensitivityFactorsLine3Side1WithContingency2 = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> new SensitivityFactor(f.getFunctionType(), f.getFunctionId(), f.getVariableType(),
                f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c2"))).toList();

        List<SensitivityFactor> sensitivityFactorsLine3Side2WithContingency1 = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> {
                if (f.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, f.getFunctionId(), f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c1"));
                } else {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, f.getFunctionId(), f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c1"));
                }
            }).toList();
        List<SensitivityFactor> sensitivityFactorsLine3Side2WithContingency2 = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> {
                if (f.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, f.getFunctionId(), f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c2"));
                } else {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, f.getFunctionId(), f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c2"));
                }
            }).toList();

        // line2 (side2 with and without contingency)
        List<SensitivityFactor> sensitivityFactorsLine2Side2WithoutContingency = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> {
                if (f.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, "line2", f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), f.getContingencyContext());
                } else {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, "line2", f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), f.getContingencyContext());
                }
            }).toList();

        List<SensitivityFactor> sensitivityFactorsLine2Side2WithContingency1 = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> {
                if (f.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, "line2", f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c1"));
                } else {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, "line2", f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c1"));
                }
            }).toList();
        List<SensitivityFactor> sensitivityFactorsLine2Side2WithContingency2 = sensitivityFactorsLine3Side1WithoutContingency
            .stream().map(f -> {
                if (f.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT_2, "line2", f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c2"));
                } else {
                    return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, "line2", f.getVariableType(),
                        f.getVariableId(), f.isVariableSet(), ContingencyContext.specificContingency("c2"));
                }
            }).toList();

        return Stream.of(
                sensitivityFactorsLine3Side1WithoutContingency,
                sensitivityFactorsLine3Side2WithoutContingency,
                sensitivityFactorsLine3Side1WithContingency1,
                sensitivityFactorsLine3Side1WithContingency2,
                sensitivityFactorsLine3Side2WithContingency1,
                sensitivityFactorsLine3Side2WithContingency2,
                sensitivityFactorsLine2Side2WithoutContingency,
                sensitivityFactorsLine2Side2WithContingency1,
                sensitivityFactorsLine2Side2WithContingency2)
            .flatMap(java.util.Collection::stream)
            .collect(Collectors.toList());
    }

    private List<List<SensitivityValue>> buildSensitivityValuesResults() {
        // stage 1 result 1 : at least, one limit violation detected
        List<SensitivityValue> sensitivityValues1 = List.of(
            // line3
            //
            new SensitivityValue(0, -1, 0.02, 130.),  // line3, side1, GROUP3, N
            new SensitivityValue(1, -1, 0.02, 230.),   // line3, side1, newGroup2, N    ----> limit violation should be detected here
            new SensitivityValue(2, -1, 0.02, 150.),   // line3, side1, TEST1, N
            new SensitivityValue(3, -1, 0.02, 160.),   // line3, side1, newGroup1, N
            new SensitivityValue(4, -1, 0.02, 210.),   // line3, side1, GH2, N       ----> limit violation should be detected here
            new SensitivityValue(5, -1, 0.02, 170.),   // line3, side1, GH3, N
            new SensitivityValue(6, -1, 0.02, 80.),   // line3, side1, (GROUP3, newGroup2), N
            new SensitivityValue(7, -1, 0.02, 110.),   // line3, side1, (TEST1, newGroup1), N
            new SensitivityValue(8, -1, 0.02, 140.), // line3, side1, (GH2, GH3), N

            new SensitivityValue(9, -1, 0.02, 160.),  // line3, side2, GROUP3, N
            new SensitivityValue(10, -1, 0.02, 200.),   // line3, side2, newGroup2, N  ----> limit violation should be detected here
            new SensitivityValue(11, -1, 0.02, 130.),   // line3, side2, TEST1, N
            new SensitivityValue(12, -1, 0.02, 190.),   // line3, side2, newGroup1, N  ----> limit violation should be detected here
            new SensitivityValue(13, -1, 0.02, 120.),   // line3, side2, GH2, N
            new SensitivityValue(14, -1, 0.02, 150.),   // line3, side2, GH3, N
            new SensitivityValue(15, -1, 0.02, 70.),   // line3, side2, (GROUP3, newGroup2), N
            new SensitivityValue(16, -1, 0.02, 100.),   // line3, side2, (TEST1, newGroup1), N
            new SensitivityValue(17, -1, 0.02, 160.), // line3, side2, (GH2, GH3), N

            new SensitivityValue(18, 0, 0.02, 120.),  // line3, side1, GROUP3, c1
            new SensitivityValue(19, 0, 0.02, 260.),   // line3, side1, newGroup2, c1    ----> limit violation should be detected here
            new SensitivityValue(20, 0, 0.02, 130.),   // line3, side1, TEST1, c1
            new SensitivityValue(21, 0, 0.02, 110.),   // line3, side1, newGroup1, c1
            new SensitivityValue(22, 0, 0.02, 80.),   // line3, side1, GH2, c1
            new SensitivityValue(23, 0, 0.02, 250.),   // line3, side1, GH3, c1    ----> limit violation should be detected here
            new SensitivityValue(24, 0, 0.02, 90.),   // line3, side1, (GROUP3, newGroup2), c1
            new SensitivityValue(25, 0, 0.02, 100.),   // line3, side1, (TEST1, newGroup1), c1
            new SensitivityValue(26, 0, 0.02, 210.), // line3, side1, (GH2, GH3), c1

            new SensitivityValue(27, 1, 0.02, 100.),  // line3, side1, GROUP3, c2
            new SensitivityValue(28, 1, 0.02, 120.),   // line3, side1, newGroup2, c2
            new SensitivityValue(36, 0, 0.02, 100.),  // line3, side2, GROUP3, c1
            new SensitivityValue(37, 0, 0.02, 120.),   // line3, side2, newGroup2, c1
            new SensitivityValue(45, 1, 0.02, 100.),  // line3, side2, GROUP3, c2
            new SensitivityValue(46, 1, 0.02, 120.),   // line3, side2, newGroup2, c2

            // line2
            //
            new SensitivityValue(54, -1, 0.02, 170.),  // line2, side2, GROUP3, N
            new SensitivityValue(55, -1, 0.02, 190.),   // line2, side2, newGroup2, N    ----> limit violation should be detected here
            new SensitivityValue(56, -1, 0.02, 120.),   // line2, side2, TEST1, N
            new SensitivityValue(57, -1, 0.02, 130.),   // line2, side2, newGroup1, N
            new SensitivityValue(58, -1, 0.02, 240.),   // line2, side2, GH2, N    ----> limit violation should be detected here
            new SensitivityValue(59, -1, 0.02, 100.),   // line2, side2, GH3, N
            new SensitivityValue(60, -1, 0.02, 80.),   // line2, side2, (GROUP3, newGroup2), N
            new SensitivityValue(61, -1, 0.02, 100.),   // line2, side2, (TEST1, newGroup1), N
            new SensitivityValue(62, -1, 0.02, 160.), // line2, side2, (GH2, GH3), N

            new SensitivityValue(63, 0, 0.02, 130.),  // line2, side2, GROUP3, c1   ----> limit violation should be detected here
            new SensitivityValue(64, 0, 0.02, 90.),   // line2, side2, newGroup2, c1
            new SensitivityValue(65, 0, 0.02, 70.),   // line2, side2, TEST1, c1
            new SensitivityValue(66, 0, 0.02, 110.),   // line2, side2, newGroup1, c1
            new SensitivityValue(67, 0, 0.02, 80.),   // line2, side2, GH2, c1
            new SensitivityValue(68, 0, 0.02, 140.),   // line2, side2, GH3, c1     ----> limit violation should be detected here
            new SensitivityValue(69, 0, 0.02, 80.),   // line2, side2, (GROUP3, newGroup2), c1
            new SensitivityValue(70, 0, 0.02, 100.),   // line2, side2, (TEST1, newGroup1), c1
            new SensitivityValue(71, 0, 0.02, 70.), // line2, side2, (GH2, GH3), c1

            new SensitivityValue(72, 0, 0.02, 100.),  // line2, side2, GROUP3, c2
            new SensitivityValue(73, 0, 0.02, 120.),   // line2, side2, newGroup2, c2  ----> limit violation should be detected here
            new SensitivityValue(74, 0, 0.02, 70.),   // line2, side2, TEST1, c2
            new SensitivityValue(75, 0, 0.02, 110.),   // line2, side2, newGroup1, c2
            new SensitivityValue(76, 0, 0.02, 115.),   // line2, side2, GH2, c2    ----> limit violation should be detected here
            new SensitivityValue(77, 0, 0.02, 100.),   // line2, side2, GH3, c2
            new SensitivityValue(78, 0, 0.02, 80.),   // line2, side2, (GROUP3, newGroup2), c2
            new SensitivityValue(79, 0, 0.02, 100.),   // line2, side2, (TEST1, newGroup1), c2
            new SensitivityValue(80, 0, 0.02, 60.)  // line2, side2, (GH2, GH3), c2
        );

        // stage 1 result 2 : no limit violation should be detected
        List<SensitivityValue> sensitivityValues2 = List.of(
            // line3
            //
            new SensitivityValue(0, -1, 0.02, 130.),  // line3, side1, GROUP3, N
            new SensitivityValue(1, -1, 0.02, 140.),   // line3, side1, newGroup2, N
            new SensitivityValue(2, -1, 0.02, 150.),   // line3, side1, TEST1, N
            new SensitivityValue(3, -1, 0.02, 160.),   // line3, side1, newGroup1, N
            new SensitivityValue(9, -1, 0.02, 160.),  // line3, side2, GROUP3, N
            new SensitivityValue(13, -1, 0.02, 120.),   // line3, side2, GH2, N
            new SensitivityValue(14, -1, 0.02, 150.),   // line3, side2, GH3, N
            new SensitivityValue(20, 0, 0.02, 130.),   // line3, side1, TEST1, c1
            new SensitivityValue(21, 0, 0.02, 110.),   // line3, side1, newGroup1, c1
            new SensitivityValue(27, 1, 0.02, 100.),  // line3, side1, GROUP3, c2
            new SensitivityValue(28, 1, 0.02, 120.),   // line3, side1, newGroup2, c2
            new SensitivityValue(36, 0, 0.02, 100.),  // line3, side2, GROUP3, c1
            new SensitivityValue(37, 0, 0.02, 120.),   // line3, side2, newGroup2, c1
            new SensitivityValue(45, 1, 0.02, 100.),  // line3, side2, GROUP3, c2
            new SensitivityValue(46, 1, 0.02, 120.),   // line3, side2, newGroup2, c2

            // line2
            //
            new SensitivityValue(54, -1, 0.02, 100.),  // line2, side2, GROUP3, N
            new SensitivityValue(56, -1, 0.02, 90.),   // line2, side2, TEST1, N
            new SensitivityValue(57, -1, 0.02, 105),   // line2, side2, newGroup1, N
            new SensitivityValue(64, 0, 0.02, 90.),   // line2, side2, newGroup2, c1
            new SensitivityValue(65, 0, 0.02, 70.),   // line2, side2, TEST1, c1
            new SensitivityValue(72, 0, 0.02, 100.),  // line2, side2, GROUP3, c2
            new SensitivityValue(77, 0, 0.02, 70.)   // line2, side2, GH3, c2
        );

        // stage 2 result 1 : at least, one limit violation detected
        List<SensitivityValue> sensitivityValues3 = List.of(
            // line3
            //
            new SensitivityValue(0, -1, 0.02, 130.),  // line3, side1, GROUP3, N
            new SensitivityValue(1, -1, 0.02, 140.),   // line3, side1, newGroup2, N
            new SensitivityValue(2, -1, 0.02, 240.),   // line3, side1, TEST1, N        ----> limit violation should be detected here
            new SensitivityValue(3, -1, 0.02, 160.),   // line3, side1, newGroup1, N
            new SensitivityValue(4, -1, 0.02, 130.),   // line3, side1, GH2, N
            new SensitivityValue(5, -1, 0.02, 220.),   // line3, side1, GH3, N       ----> limit violation should be detected here
            new SensitivityValue(6, -1, 0.02, 80.),   // line3, side1, (GROUP3, newGroup2), N
            new SensitivityValue(7, -1, 0.02, 110.),   // line3, side1, (TEST1, newGroup1), N
            new SensitivityValue(8, -1, 0.02, 230.), // line3, side1, (GH2, GH3), N
            new SensitivityValue(9, -1, 0.02, 270.),  // line3, side2, GROUP3, N        ----> limit violation should be detected here
            new SensitivityValue(10, -1, 0.02, 160.),   // line3, side2, newGroup2, N
            new SensitivityValue(11, -1, 0.02, 130.),   // line3, side2, TEST1, N
            new SensitivityValue(12, -1, 0.02, 150.),   // line3, side2, newGroup1, N
            new SensitivityValue(13, -1, 0.02, 120.),   // line3, side2, GH2, N
            new SensitivityValue(14, -1, 0.02, 200.),   // line3, side2, GH3, N      ----> limit violation should be detected here
            new SensitivityValue(15, -1, 0.02, 70.),   // line3, side2, (GROUP3, newGroup2), N
            new SensitivityValue(16, -1, 0.02, 100.),   // line3, side2, (TEST1, newGroup1), N
            new SensitivityValue(17, -1, 0.02, 160.), // line3, side2, (GH2, GH3), N
            new SensitivityValue(18, 0, 0.02, 120.),  // line3, side1, GROUP3, c1
            new SensitivityValue(19, 0, 0.02, 110.),   // line3, side1, newGroup2, c1
            new SensitivityValue(20, 0, 0.02, 130.),   // line3, side1, TEST1, c1
            new SensitivityValue(21, 0, 0.02, 260.),   // line3, side1, newGroup1, c1   ----> limit violation should be detected here
            new SensitivityValue(22, 0, 0.02, 250.),   // line3, side1, GH2, c1          ----> limit violation should be detected here
            new SensitivityValue(23, 0, 0.02, 90.),   // line3, side1, GH3, c1
            new SensitivityValue(24, 0, 0.02, 90.),   // line3, side1, (GROUP3, newGroup2), c1
            new SensitivityValue(25, 0, 0.02, 100.),   // line3, side1, (TEST1, newGroup1), c1
            new SensitivityValue(26, 0, 0.02, 210.), // line3, side1, (GH2, GH3), c1
            new SensitivityValue(27, 1, 0.02, 100.),  // line3, side1, GROUP3, c2
            new SensitivityValue(28, 1, 0.02, 120.),   // line3, side1, newGroup2, c2
            new SensitivityValue(36, 0, 0.02, 100.),  // line3, side2, GROUP3, c1
            new SensitivityValue(37, 0, 0.02, 120.),   // line3, side2, newGroup2, c1
            new SensitivityValue(45, 1, 0.02, 100.),  // line3, side2, GROUP3, c2
            new SensitivityValue(46, 1, 0.02, 120.),   // line3, side2, newGroup2, c2

            // line2
            //
            new SensitivityValue(54, -1, 0.02, 170.),  // line2, side2, GROUP3, N
            new SensitivityValue(55, -1, 0.02, 120.),   // line2, side2, newGroup2, N
            new SensitivityValue(56, -1, 0.02, 195.),   // line2, side2, TEST1, N        ----> limit violation should be detected here
            new SensitivityValue(57, -1, 0.02, 245.),   // line2, side2, newGroup1, N    ----> limit violation should be detected here
            new SensitivityValue(58, -1, 0.02, 130.),   // line2, side2, GH2, N
            new SensitivityValue(59, -1, 0.02, 100.),   // line2, side2, GH3, N
            new SensitivityValue(60, -1, 0.02, 80.),   // line2, side2, (GROUP3, newGroup2), N
            new SensitivityValue(61, -1, 0.02, 100.),   // line2, side2, (TEST1, newGroup1), N
            new SensitivityValue(62, -1, 0.02, 160.), // line2, side2, (GH2, GH3), N
            new SensitivityValue(63, 0, 0.02, 70.),  // line2, side2, GROUP3, c1
            new SensitivityValue(64, 0, 0.02, 90.),   // line2, side2, newGroup2, c1
            new SensitivityValue(65, 0, 0.02, 140.),   // line2, side2, TEST1, c1    ----> limit violation should be detected here
            new SensitivityValue(66, 0, 0.02, 110.),   // line2, side2, newGroup1, c1
            new SensitivityValue(67, 0, 0.02, 140.),   // line2, side2, GH2, c1      ----> limit violation should be detected here
            new SensitivityValue(68, 0, 0.02, 80.),   // line2, side2, GH3, c1
            new SensitivityValue(69, 0, 0.02, 80.),   // line2, side2, (GROUP3, newGroup2), c1
            new SensitivityValue(70, 0, 0.02, 100.),   // line2, side2, (TEST1, newGroup1), c1
            new SensitivityValue(71, 0, 0.02, 70.), // line2, side2, (GH2, GH3), c1
            new SensitivityValue(72, 0, 0.02, 130.),  // line2, side2, GROUP3, c2    ----> limit violation should be detected here
            new SensitivityValue(73, 0, 0.02, 90.),   // line2, side2, newGroup2, c2
            new SensitivityValue(74, 0, 0.02, 70.),   // line2, side2, TEST1, c2
            new SensitivityValue(75, 0, 0.02, 110.),   // line2, side2, newGroup1, c2
            new SensitivityValue(76, 0, 0.02, 70.),   // line2, side2, GH2, c2
            new SensitivityValue(77, 0, 0.02, 120.),   // line2, side2, GH3, c2    ----> limit violation should be detected here
            new SensitivityValue(78, 0, 0.02, 80.),   // line2, side2, (GROUP3, newGroup2), c2
            new SensitivityValue(79, 0, 0.02, 100.),   // line2, side2, (TEST1, newGroup1), c2
            new SensitivityValue(80, 0, 0.02, 60.)  // line2, side2, (GH2, GH3), c2
        );

        // stage 2 result 2 : no more limit violation detected
        List<SensitivityValue> sensitivityValues4 = List.of(
            // line3
            //
            new SensitivityValue(0, -1, 0.02, 130.),  // line3, side1, GROUP3, N
            new SensitivityValue(1, -1, 0.02, 140.),   // line3, side1, newGroup2, N
            new SensitivityValue(11, -1, 0.02, 130.),   // line3, side2, TEST1, N
            new SensitivityValue(12, -1, 0.02, 150.),   // line3, side2, newGroup1, N
            new SensitivityValue(17, -1, 0.02, 160.), // line3, side2, (GH2, GH3), N
            new SensitivityValue(18, 0, 0.02, 120.),  // line3, side1, GROUP3, c1
            new SensitivityValue(19, 0, 0.02, 110.),   // line3, side1, newGroup2, c1
            new SensitivityValue(26, 0, 0.02, 210.), // line3, side1, (GH2, GH3), c1
            new SensitivityValue(27, 1, 0.02, 100.),  // line3, side1, GROUP3, c2
            new SensitivityValue(45, 1, 0.02, 100.),  // line3, side2, GROUP3, c2
            new SensitivityValue(46, 1, 0.02, 120.),   // line3, side2, newGroup2, c2

            // line2
            //
            new SensitivityValue(54, -1, 0.02, 95.),  // line2, side2, GROUP3, N
            new SensitivityValue(55, -1, 0.02, 80.),   // line2, side2, newGroup2, N
            new SensitivityValue(59, -1, 0.02, 100.),   // line2, side2, GH3, N
            new SensitivityValue(60, -1, 0.02, 80.),   // line2, side2, (GROUP3, newGroup2), N
            new SensitivityValue(61, -1, 0.02, 100.),   // line2, side2, (TEST1, newGroup1), N
            new SensitivityValue(75, 0, 0.02, 110.),   // line2, side2, newGroup1, c2
            new SensitivityValue(76, 0, 0.02, 70.)   // line2, side2, GH2, c2
        );

        return List.of(sensitivityValues1, sensitivityValues2, sensitivityValues3, sensitivityValues4);
    }

    private void mockActions() {
        given(actionsService.getContingencyList(eq(CONTINGENCIES_1_UUID), any(), eq(VARIANT_ID))).willReturn(CONTINGENCIES_1);
        given(actionsService.getContingencyList(eq(CONTINGENCIES_2_UUID), any(), eq(VARIANT_ID))).willReturn(CONTINGENCIES_2);
    }

    private void mockFilters() {
        given(filterService.getIdentifiablesFromFilter(eq(GENERATORS_WIND_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(GENERATORS_WIND);
        given(filterService.getIdentifiablesFromFilter(eq(GENERATORS_SOLAR_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(GENERATORS_SOLAR);
        given(filterService.getIdentifiablesFromFilter(eq(GENERATORS_HYDRO_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(GENERATORS_HYDRO);
        given(filterService.getIdentifiablesFromFilter(eq(CAPPING_GENERATORS_WIND_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(CAPPING_GENERATORS_WIND);
        given(filterService.getIdentifiablesFromFilter(eq(CAPPING_GENERATORS_SOLAR_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(CAPPING_GENERATORS_SOLAR);
        given(filterService.getIdentifiablesFromFilter(eq(CAPPING_GENERATORS_HYDRO_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(CAPPING_GENERATORS_HYDRO);
        given(filterService.getIdentifiablesFromFilter(eq(MONITORED_BRANCHES_1_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(BRANCHES_1);
        given(filterService.getIdentifiablesFromFilter(eq(MONITORED_BRANCHES_2_FILTER_UUID), any(), eq(VARIANT_ID))).willReturn(BRANCHES_2);
    }

    private String resourceToString(String resource) throws IOException {
        return new String(ByteStreams.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream(resource))), StandardCharsets.UTF_8);
    }

    @Before
    @SneakyThrows
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Network network = Network.read("testForNonEvacuatedEnergy.xiidm", getClass().getResourceAsStream("/testForNonEvacuatedEnergy.xiidm"));
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);

        Network networkWithNoPermamentLimit = Network.read("testForNonEvacuatedEnergyNoPermanentLimit.xiidm", getClass().getResourceAsStream("/testForNonEvacuatedEnergyNoPermanentLimit.xiidm"));
        networkWithNoPermamentLimit.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);

        // network store service mocking
        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);
        given(networkStoreService.getNetwork(NETWORK_ERROR_UUID, PreloadingStrategy.COLLECTION)).willThrow(new RuntimeException(ERROR_MESSAGE));
        given(networkStoreService.getNetwork(NETWORK_ERROR_PERMANENT_LIMIT_UUID, PreloadingStrategy.COLLECTION)).willReturn(networkWithNoPermamentLimit);

        // build non evacuated energy input data
        List<NonEvacuatedEnergyStageDefinition> stagesDefinition = buildStagesDefinition();
        List<NonEvacuatedEnergyStagesSelection> stagesSelection = buildStagesSelection();
        NonEvacuatedEnergyGeneratorsCappings generatorsCappings = buildGeneratorsCappings();
        List<NonEvacuatedEnergyMonitoredBranches> monitoredBranches = buildMonitoredBranches();
        List<NonEvacuatedEnergyContingencies> contingencies = buildContingencies();

        NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData = NonEvacuatedEnergyInputData.builder()
            .nonEvacuatedEnergyStagesDefinition(stagesDefinition)
            .nonEvacuatedEnergyStagesSelection(stagesSelection)
            .nonEvacuatedEnergyGeneratorsCappings(generatorsCappings)
            .nonEvacuatedEnergyMonitoredBranches(monitoredBranches)
            .nonEvacuatedEnergyContingencies(contingencies)
            .parameters(SensitivityAnalysisParameters.load())
            .build();
        INPUT = mapper.writeValueAsString(nonEvacuatedEnergyInputData);

        nonEvacuatedEnergyInputData.getNonEvacuatedEnergyMonitoredBranches().get(0).setIstN(false);
        nonEvacuatedEnergyInputData.getNonEvacuatedEnergyMonitoredBranches().get(0).setLimitNameN("limitNotFound");
        INPUT_WITH_TEMPORARY_LIMIT_NOT_FOUND = mapper.writeValueAsString(nonEvacuatedEnergyInputData);

        // build the successive security analysis results
        // (only some sensitivity factors in results for line3 and line2)
        List<SensitivityFactor> sensitivityFactors = buildSensitivityFactorsResults();
        List<List<SensitivityValue>> sensitivityValues = buildSensitivityValuesResults();

        // 4 sensitivity analysis calls globally (2 for each generation stage)
        SensitivityAnalysisResult result1 = new SensitivityAnalysisResult(sensitivityFactors, CONTINGENCIES_STATUSES, sensitivityValues.get(0));
        SensitivityAnalysisResult result2 = new SensitivityAnalysisResult(sensitivityFactors, CONTINGENCIES_STATUSES, sensitivityValues.get(1));
        SensitivityAnalysisResult result3 = new SensitivityAnalysisResult(sensitivityFactors, CONTINGENCIES_STATUSES, sensitivityValues.get(2));
        SensitivityAnalysisResult result4 = new SensitivityAnalysisResult(sensitivityFactors, CONTINGENCIES_STATUSES, sensitivityValues.get(3));

        // action service mocking
        mockActions();

        // filter service mocking
        mockFilters();

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

        // mock the multiple successive calls to the sensitivity analysis runner :
        // individual sensitivity analysis results are built in order to have 2 sensitivity analysis calls for each stage, so to have 4
        // sensitivity analysis calls globally
        SensitivityAnalysis.Runner runner = mock(SensitivityAnalysis.Runner.class);
        given(runner.getName()).willReturn(defaultSensitivityAnalysisProvider);
        given(runner.runAsync(eq(network), anyString(), anyList(), anyList(), anyList(),
            any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class)))
            .willReturn(CompletableFuture.completedFuture(result1))
            .willReturn(CompletableFuture.completedFuture(result2))
            .willReturn(CompletableFuture.completedFuture(result3))
            .willReturn(CompletableFuture.completedFuture(result4));
        nonEvacuatedEnergyWorkerService.setSensitivityAnalysisFactorySupplier(provider -> runner);

        // purge messages
        while (output.receive(1000, "nonEvacuatedEnergy.result") != null) {
        }
        while (output.receive(1000, "nonEvacuatedEnergy.run") != null) {
        }
        while (output.receive(1000, "nonEvacuatedEnergy.cancel") != null) {
        }
        while (output.receive(1000, "nonEvacuatedEnergy.stopped") != null) {
        }
        while (output.receive(1000, "nonEvacuatedEnergy.failed") != null) {
        }
    }

    @SneakyThrows
    @After
    public void tearDown() {
        mockMvc.perform(delete("/" + VERSION + "/non-evacuated-energy-results"))
            .andExpect(status().isOk());
    }

    @Test
    public void runTest() throws Exception {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/non-evacuated-energy?reportType=NonEvacuatedEnergy&receiver=me&variantId=" + VARIANT_ID, NETWORK_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HEADER_USER_ID, "userId")
            .content(INPUT))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        Message<byte[]> resultMessage = output.receive(30 * TIMEOUT, "nonEvacuatedEnergy.result");
        assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
        assertEquals("me", resultMessage.getHeaders().get("receiver"));

        // get result
        result = mockMvc.perform(get("/" + VERSION + "/non-evacuated-energy-results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        String res = result.getResponse().getContentAsString();
        JSONAssert.assertEquals(resourceToString("/non-evacuated-energy-results.json"), res, JSONCompareMode.LENIENT);

        // should throw not found if result does not exist
        mockMvc.perform(get("/" + VERSION + "/non-evacuated-energy-results/{resultUuid}", OTHER_RESULT_UUID))
            .andExpect(status().isNotFound());

        // test one result deletion
        mockMvc.perform(delete("/" + VERSION + "/non-evacuated-energy-results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isOk());

        mockMvc.perform(get("/" + VERSION + "/non-evacuated-energy-results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    @SneakyThrows
    @Test
    public void testStatus() {
        MvcResult result = mockMvc.perform(get(
                "/" + VERSION + "/non-evacuated-energy-results/{resultUuid}/status", RESULT_UUID))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals("", result.getResponse().getContentAsString());

        mockMvc.perform(put("/" + VERSION + "/non-evacuated-energy-results/invalidate-status?resultUuid=" + RESULT_UUID))
            .andExpect(status().isOk());

        result = mockMvc.perform(get(
                "/" + VERSION + "/non-evacuated-energy-results/{resultUuid}/status", RESULT_UUID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(SensitivityAnalysisStatus.NOT_DONE.name(), result.getResponse().getContentAsString());
    }

    @Test
    public void stopTest() throws Exception {
        mockMvc.perform(post(
            "/" + VERSION + "/networks/{networkUuid}/non-evacuated-energy?reportType=NonEvacuatedEnergy&receiver=me&variantId=" + VARIANT_ID, NETWORK_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HEADER_USER_ID, "userId")
            .content(INPUT))
            .andExpect(status().isOk());

        // stop non evacuated energy analysis
        mockMvc.perform(put("/" + VERSION + "/non-evacuated-energy-results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID))
            .andExpect(status().isOk());

        // message stopped should have been sent
        Message<byte[]> message = output.receive(TIMEOUT, "nonEvacuatedEnergy.stopped");
        assertNotNull(message);
        assertEquals(RESULT_UUID.toString(), message.getHeaders().get("resultUuid"));
        assertEquals("me", message.getHeaders().get("receiver"));
        assertEquals(CANCEL_MESSAGE, message.getHeaders().get("message"));
    }

    @SneakyThrows
    @Test
    public void testWithBadNetworkError() {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/non-evacuated-energy?reportType=NonEvacuatedEnergy&receiver=me&variantId=" + VARIANT_ID, NETWORK_ERROR_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HEADER_USER_ID, "userId")
            .content(INPUT))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        // message failed should have been sent
        Message<byte[]> failMessage = output.receive(TIMEOUT, "nonEvacuatedEnergy.failed");
        assertEquals(RESULT_UUID.toString(), failMessage.getHeaders().get("resultUuid"));
        assertEquals("me", failMessage.getHeaders().get("receiver"));
        assertEquals(FAIL_MESSAGE + " : " + ERROR_MESSAGE, failMessage.getHeaders().get("message"));

        // No result available
        mockMvc.perform(get("/" + VERSION + "/non-evacuated-energy-results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    private void testLimitError(String inputData, String variantId, UUID networkUuid, String messageExpected) throws Exception {
        MvcResult result = mockMvc.perform(post(
                "/" + VERSION + "/networks/{networkUuid}/non-evacuated-energy?reportType=NonEvacuatedEnergy&receiver=me&variantId=" + variantId, networkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_USER_ID, "userId")
                .content(inputData))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

        // message failed should have been sent
        Message<byte[]> failMessage = output.receive(TIMEOUT, "nonEvacuatedEnergy.failed");
        assertEquals(RESULT_UUID.toString(), failMessage.getHeaders().get("resultUuid"));
        assertEquals("me", failMessage.getHeaders().get("receiver"));
        assertTrue(((String) failMessage.getHeaders().get("message")).contains(messageExpected));

        // No result available
        mockMvc.perform(get("/" + VERSION + "/non-evacuated-energy-results/{resultUuid}", RESULT_UUID))
            .andExpect(status().isNotFound());
    }

    @Test
    public void testWithPermanentOrTemporaryLimitNotFound() throws Exception {
        testLimitError(INPUT, VARIANT_ID, NETWORK_ERROR_PERMANENT_LIMIT_UUID, "Branch 'line2' has no current limits !!");

        testLimitError(INPUT_WITH_TEMPORARY_LIMIT_NOT_FOUND, VARIANT_ID, NETWORK_UUID, "Temporary limit 'limitNotFound' not found for branch 'line3' !!");
    }
}

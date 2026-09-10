/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.BatteryNetworkFactory;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import org.gridsuite.sensitivityanalysis.server.service.ActionsService;
import org.gridsuite.sensitivityanalysis.server.service.FilterService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisInputBuilderService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisRunContext;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.sensitivity.SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
import static com.powsybl.sensitivity.SensitivityVariableType.INJECTION_ACTIVE_POWER;
import static org.gridsuite.sensitivityanalysis.server.util.TestUtils.DEFAULT_PROVIDER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SpringBootTest
class SensitivityAnalysisInputDataTest {
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final String VARIANT_ID = VariantManagerConstants.INITIAL_VARIANT_ID;
    private static final Network NETWORK = new NetworkFactoryImpl().createNetwork("ghost network", "absent format");

    @MockitoBean
    private ActionsService actionsService;

    @MockitoBean
    private FilterService filterService;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    @BeforeEach
    void setUp() {
        objectWriter = mapper.writer().withDefaultPrettyPrinter();
    }

    @Test
    void test() throws Exception {
        SensitivityAnalysisInputData sensitivityAnalysisInputData1 = SensitivityAnalysisInputData.builder()
            .sensitivityInjectionsSets(List.of(SensitivityInjectionsSet.builder()
                .monitoredBranches(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .injections(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .distributionType(SensitivityAnalysisInputData.DistributionType.REGULAR)
                .contingencies(List.of(UUID.randomUUID())).build()))
            .sensitivityInjections(List.of(SensitivityInjection.builder()
                .monitoredBranches(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .injections(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .contingencies(List.of(UUID.randomUUID(), UUID.randomUUID())).build()))
            .sensitivityHVDCs(List.of(SensitivityHVDC.builder()
                .monitoredBranches(List.of(UUID.randomUUID()))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_MW)
                .hvdcs(List.of(UUID.randomUUID()))
                .contingencies(List.of(UUID.randomUUID())).build()))
            .sensitivityPSTs(List.of(SensitivityPST.builder()
                .monitoredBranches(List.of(UUID.randomUUID()))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A)
                .psts(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .contingencies(List.of(UUID.randomUUID())).build()))
            .sensitivityNodes(List.of(SensitivityNodes.builder()
                .monitoredVoltageLevels(List.of(UUID.randomUUID()))
                .equipmentsInVoltageRegulation(List.of(UUID.randomUUID()))
                .contingencies(List.of()).build()))
            .parameters(SensitivityAnalysisParameters.load())
            .elementsIdNameMap(Map.of())
            .build();

        String result1 = objectWriter.writeValueAsString(sensitivityAnalysisInputData1);
        SensitivityAnalysisInputData sensitivityAnalysisInputData2 = mapper.readValue(result1, new TypeReference<>() { });
        String result2 = objectWriter.writeValueAsString(sensitivityAnalysisInputData2);
        assertEquals(result1, result2);
    }

    @Test
    void testEmptyInputTranslation() {
        SensitivityAnalysisInputBuilderService inputBuilderService;
        given(filterService.getIdentifiables(any(List.class), any(), any())).willThrow(new RuntimeException("FilterException"));
        given(actionsService.getContingencyList(any(), any(), any())).willThrow(new RuntimeException("ContingencyException"));
        inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        SensitivityAnalysisInputData.SensitivityAnalysisInputDataBuilder<?, ?> inputBuilder = SensitivityAnalysisInputData.builder();
        SensitivityAnalysisInputData inputData = inputBuilder
            .sensitivityInjectionsSets(List.of())
            .sensitivityInjections(List.of())
            .sensitivityHVDCs(List.of())
            .sensitivityPSTs(List.of())
            .sensitivityNodes(List.of())
            .parameters(SensitivityAnalysisParameters.load())
            .elementsIdNameMap(Map.of())
            .build();
        ReportNode reporter = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports")
                .withMessageTemplate("a").build();
        SensitivityAnalysisRunContext context;
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);
        inputBuilderService.build(context, NETWORK, reporter);
        Collection<ReportNode> reports;
        reports = reporter.getChildren();
        assertThat(reports, not(nullValue()));
        assertThat(reports.size(), is(0));
    }

    @Test
    void testFilterPbInputTranslation() {
        SensitivityAnalysisInputBuilderService inputBuilderService;

        UUID u10Id = UUID.randomUUID();
        UUID u11Id = UUID.randomUUID();
        given(filterService.getIdentifiables(any(List.class), any(), any())).willThrow(new RuntimeException("FilterException"));
        given(actionsService.getContingencyList(anyList(), any(), any())).willReturn(new ContingencyListExportResult(null, List.of(u10Id, u11Id)));
        inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        SensitivityAnalysisInputData.SensitivityAnalysisInputDataBuilder<?, ?> inputBuilder = SensitivityAnalysisInputData.builder();
        ReportNode reporter = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports")
                .withMessageTemplate("a").build();
        SensitivityAnalysisRunContext context;

        SensitivityAnalysisInputData inputData = inputBuilder
            .sensitivityInjectionsSets(List.of())
            .sensitivityHVDCs(List.of())
            .sensitivityPSTs(List.of())
            .sensitivityNodes(List.of())
            .parameters(SensitivityAnalysisParameters.load())
            .sensitivityInjections(List.of(SensitivityInjection.builder()
                .monitoredBranches(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .injections(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .contingencies(List.of(u10Id, u11Id))
                .build()))
            .elementsIdNameMap(Map.of())
            .build();
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);
        inputBuilderService.build(context, NETWORK, reporter);
        Collection<ReportNode> reports = reporter.getChildren();
        assertThat(reports, not(nullValue()));
        assertEquals(2, reports.size());
        Set<String> reportKeys = reports.stream().map(ReportNode::getMessageKey).collect(Collectors.toSet());
        assertEquals(1, reportKeys.size());
        assertThat(reportKeys, contains("sensitivity.analysis.server.contingencyTranslationFailure"));
    }

    @Test
    void testFilterWiderPbInputTranslation() {
        SensitivityAnalysisInputBuilderService inputBuilderService;
        inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        SensitivityAnalysisInputData.SensitivityAnalysisInputDataBuilder<?, ?> inputBuilder = SensitivityAnalysisInputData.builder();
        SensitivityAnalysisRunContext context;

        SensitivityAnalysisInputData inputData = inputBuilder
            .build();
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);
        final ReportNode reporter = ReportNode.newRootReportNode()
                .withResourceBundles("i18n.reports")
                .withMessageTemplate("a").build();
        var thrown = assertThrows(NullPointerException.class, () -> inputBuilderService.build(context, NETWORK, reporter));
        assertThat(thrown, Matchers.instanceOf(NullPointerException.class));

        Collection<ReportNode> reports = reporter.getChildren();
        assertThat(reports, not(nullValue()));
        assertThat(reports.size(), is(1));
        Set<String> reportKeys = reports.stream().map(ReportNode::getMessageKey).collect(Collectors.toSet());
        assertThat(reportKeys.size(), is(1));
        assertThat(reportKeys, contains("sensitivity.analysis.server.sensitivityInputParametersTranslationFailure"));
    }

    @Test
    void testInjections() {
        Network network = BatteryNetworkFactory.create();
        UUID idFilterGenerator = UUID.randomUUID();
        UUID idFilterBattery = UUID.randomUUID();
        UUID idFilterLoad = UUID.randomUUID();
        UUID idFilterLine = UUID.randomUUID();
        List<UUID> filterIdsList = List.of(idFilterGenerator, idFilterBattery, idFilterLoad);
        List<UUID> monitoredBranchIdsList = List.of(idFilterLine);
        given(filterService.getIdentifiablesByFilterId(filterIdsList, NETWORK_UUID, VARIANT_ID))
                .willReturn(Map.of(idFilterGenerator, List.of(new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR, 1.0)),
                        idFilterBattery, List.of(new IdentifiableAttributes("BAT", IdentifiableType.BATTERY, 1.0)),
                        idFilterLoad, List.of(new IdentifiableAttributes("LOAD", IdentifiableType.LOAD, 1.0))));
        given(filterService.getIdentifiablesByFilterId(monitoredBranchIdsList, NETWORK_UUID, VARIANT_ID))
                .willReturn(Map.of(idFilterLine, List.of(new IdentifiableAttributes("NHV1_NHV2_1", IdentifiableType.LINE, 1.0))));
        SensitivityAnalysisInputBuilderService inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        List<SensitivityInjection> sensitivityInjections = new ArrayList<>();
        sensitivityInjections.add(new SensitivityInjection(monitoredBranchIdsList, filterIdsList, Collections.emptyList(), true));
        List<SensitivityInjectionsSet> sensitivityInjectionsSets = new ArrayList<>();
        sensitivityInjectionsSets.add(new SensitivityInjectionsSet(monitoredBranchIdsList, filterIdsList,
                SensitivityAnalysisInputData.DistributionType.PROPORTIONAL, Collections.emptyList(), true));
        SensitivityAnalysisInputData inputData = SensitivityAnalysisInputData.builder()
                .sensitivityInjectionsSets(sensitivityInjectionsSets)
                .sensitivityInjections(sensitivityInjections)
                .sensitivityHVDCs(Collections.emptyList())
                .sensitivityPSTs(Collections.emptyList())
                .sensitivityNodes(Collections.emptyList())
                .elementsIdNameMap(Map.of(idFilterBattery, "FilterBattery", idFilterLoad, "FilterLoad", idFilterGenerator, "FilterGenerator"))
                .build();
        SensitivityAnalysisRunContext context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);
        inputBuilderService.build(context, network, ReportNode.NO_OP);
        List<List<SensitivityFactor>> factors = context.getSensitivityAnalysisInputs().getFactors();
        assertEquals(4, factors.size());
        SensitivityFactor sensitivityFactor = factors.getFirst().getFirst();
        assertNotNull(sensitivityFactor);
        assertEquals("NHV1_NHV2_1", sensitivityFactor.getFunctionId());
        assertEquals(BRANCH_ACTIVE_POWER_1, sensitivityFactor.getFunctionType());
        assertTrue(sensitivityFactor.getVariableId().contains("FilterGenerator"));
        assertTrue(sensitivityFactor.getVariableId().contains("FilterLoad"));
        assertTrue(sensitivityFactor.getVariableId().contains("FilterBattery"));
        assertTrue(sensitivityFactor.getVariableId().contains(SensitivityAnalysisInputData.DistributionType.PROPORTIONAL.name()));
        assertEquals(INJECTION_ACTIVE_POWER, sensitivityFactor.getVariableType());

        // test PROPORTIONAL_MAXP
        sensitivityInjectionsSets = new ArrayList<>();
        sensitivityInjectionsSets.add(new SensitivityInjectionsSet(monitoredBranchIdsList, filterIdsList,
                SensitivityAnalysisInputData.DistributionType.PROPORTIONAL_MAXP, Collections.emptyList(), true));
        inputData = SensitivityAnalysisInputData.builder()
                .sensitivityInjectionsSets(sensitivityInjectionsSets)
                .sensitivityInjections(Collections.emptyList())
                .sensitivityHVDCs(Collections.emptyList())
                .sensitivityPSTs(Collections.emptyList())
                .sensitivityNodes(Collections.emptyList())
                .elementsIdNameMap(Map.of(idFilterBattery, "FilterBattery", idFilterLoad, "FilterLoad", idFilterGenerator, "FilterGenerator"))
                .build();
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);
        inputBuilderService.build(context, network, ReportNode.NO_OP);
        assertEquals(1, context.getSensitivityAnalysisInputs().getFactors().size());

        // test REGULAR
        sensitivityInjectionsSets = new ArrayList<>();
        sensitivityInjectionsSets.add(new SensitivityInjectionsSet(monitoredBranchIdsList, filterIdsList,
                SensitivityAnalysisInputData.DistributionType.REGULAR, Collections.emptyList(), true));
        inputData = SensitivityAnalysisInputData.builder()
                .sensitivityInjectionsSets(sensitivityInjectionsSets)
                .sensitivityInjections(Collections.emptyList())
                .sensitivityHVDCs(Collections.emptyList())
                .sensitivityPSTs(Collections.emptyList())
                .sensitivityNodes(Collections.emptyList())
                .elementsIdNameMap(Map.of(idFilterBattery, "FilterBattery", idFilterLoad, "FilterLoad", idFilterGenerator, "FilterGenerator"))
                .build();
        context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);
        inputBuilderService.build(context, network, ReportNode.NO_OP);
        assertEquals(1, context.getSensitivityAnalysisInputs().getFactors().size());
    }

    @Test
    void testErrors() {
        Network network = BatteryNetworkFactory.create();
        UUID idFilterGenerator = UUID.randomUUID();
        UUID idFilterBattery = UUID.randomUUID();
        UUID idFilterLoad = UUID.randomUUID();
        UUID idLine = UUID.randomUUID();
        List<UUID> filterIdsList = List.of(idFilterGenerator, idFilterBattery, idFilterLoad);
        List<UUID> monitoredBranchIdsList = List.of(idLine);

        List<SensitivityInjectionsSet> sensitivityInjectionsSets = new ArrayList<>();
        sensitivityInjectionsSets.add(new SensitivityInjectionsSet(monitoredBranchIdsList, filterIdsList,
                SensitivityAnalysisInputData.DistributionType.PROPORTIONAL, Collections.emptyList(), true));
        SensitivityAnalysisInputData inputData = SensitivityAnalysisInputData.builder()
                .sensitivityInjectionsSets(sensitivityInjectionsSets)
                .sensitivityInjections(Collections.emptyList())
                .sensitivityHVDCs(Collections.emptyList())
                .sensitivityPSTs(Collections.emptyList())
                .sensitivityNodes(Collections.emptyList())
                .elementsIdNameMap(Map.of(idFilterBattery, "FilterBattery", idFilterLoad, "FilterLoad", idFilterGenerator, "FilterGenerator"))
                .build();
        SensitivityAnalysisRunContext context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);

        // test battery not found
        given(filterService.getIdentifiablesByFilterId(filterIdsList, NETWORK_UUID, VARIANT_ID))
                .willReturn(Map.of(idFilterGenerator, List.of(new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR, 1.0)),
                        idFilterBattery, List.of(new IdentifiableAttributes("bat", IdentifiableType.BATTERY, 1.0)),
                        idFilterLoad, List.of(new IdentifiableAttributes("LOAD", IdentifiableType.LOAD, 1.0))));
        SensitivityAnalysisInputBuilderService inputBuilderService = new SensitivityAnalysisInputBuilderService(actionsService, filterService);
        String message = assertThrows(PowsyblException.class, () -> inputBuilderService.build(context, network, ReportNode.NO_OP)).getMessage();
        assertEquals("Battery 'bat' not found !!", message);

        // test VENTILATION with null distribution key for injection
        sensitivityInjectionsSets = new ArrayList<>();
        sensitivityInjectionsSets.add(new SensitivityInjectionsSet(monitoredBranchIdsList, filterIdsList,
                SensitivityAnalysisInputData.DistributionType.VENTILATION, Collections.emptyList(), true));
        inputData = SensitivityAnalysisInputData.builder()
                .sensitivityInjectionsSets(sensitivityInjectionsSets)
                .sensitivityInjections(Collections.emptyList())
                .sensitivityHVDCs(Collections.emptyList())
                .sensitivityPSTs(Collections.emptyList())
                .sensitivityNodes(Collections.emptyList())
                .elementsIdNameMap(Map.of(idFilterBattery, "FilterBattery", idFilterLoad, "FilterLoad", idFilterGenerator, "FilterGenerator"))
                .build();
        SensitivityAnalysisRunContext context2 = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID, null, null, null, DEFAULT_PROVIDER, inputData);
        given(filterService.getIdentifiablesByFilterId(filterIdsList, NETWORK_UUID, VARIANT_ID))
                .willReturn(Map.of(idFilterGenerator, List.of(new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR, 1.0)),
                        idFilterBattery, List.of(new IdentifiableAttributes("BAT", IdentifiableType.BATTERY, null)),
                        idFilterLoad, List.of(new IdentifiableAttributes("LOAD", IdentifiableType.LOAD, 1.0))));
        assertDoesNotThrow(() -> inputBuilderService.build(context2, network, ReportNode.NO_OP));

        // test VENTILATION with null distribution key for load
        given(filterService.getIdentifiablesByFilterId(filterIdsList, NETWORK_UUID, VARIANT_ID))
                .willReturn(Map.of(idFilterGenerator, List.of(new IdentifiableAttributes("GEN", IdentifiableType.GENERATOR, 1.0)),
                        idFilterBattery, List.of(new IdentifiableAttributes("BAT", IdentifiableType.BATTERY, 1.0)),
                        idFilterLoad, List.of(new IdentifiableAttributes("LOAD", IdentifiableType.LOAD, null))));
        assertDoesNotThrow(() -> inputBuilderService.build(context2, network, ReportNode.NO_OP));
    }
}

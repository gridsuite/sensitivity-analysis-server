/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gridsuite.sensitivityanalysis.server.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.SensitivityAnalysisApplication;
import org.gridsuite.sensitivityanalysis.server.dto.FilterIdent;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityInjectionsSet;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({ @ContextConfiguration(classes = { SensitivityAnalysisApplication.class,
    TestChannelBinderConfiguration.class }) })
public class SensitivityAnalysisServiceTest {
    @SpyBean
    private SensitivityAnalysisWorkerService workerService;

    @SpyBean
    SensitivityAnalysisService analysisService;

    @MockBean
    private NetworkStoreService networkStoreService;

    //@MockBean
    //private ActionsService actionsService;
    //
    //@MockBean
    //private FilterService filterService;

    @MockBean
    private ReportService reportService;

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    //private static final UUID REPORT_UUID  = UUID.randomUUID();

    private static SensitivityFactor makeMWFactor(String funcId, String varId, boolean doesAleas) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, funcId,
            SensitivityVariableType.INJECTION_ACTIVE_POWER, varId, false,
            doesAleas ? ContingencyContext.none() : ContingencyContext.all());
    }

    private static List<IdentifiableAttributes> mkIdentAttributesOfType(IdentifiableType type, String... ids) {
        return Arrays.stream(ids).map(id -> new IdentifiableAttributes(id, type, null)).collect(Collectors.toList());
    }

    @MockBean
    private UuidGeneratorService uuidGeneratorService;

    @Value("${sensitivity-analysis.default-provider}")
    String defaultSensitivityAnalysisProvider;

    @Test
    public void test1() {
        final UUID resultUuid = UUID.randomUUID();

        List<IdentifiableAttributes> generators = mkIdentAttributesOfType(IdentifiableType.GENERATOR, "GEN", "GEN2");
        List<IdentifiableAttributes> loads = mkIdentAttributesOfType(IdentifiableType.LOAD, "LOAD");

        List<IdentifiableAttributes> branches = Stream.of(
            mkIdentAttributesOfType(IdentifiableType.LINE, "l1", "l2"),
            mkIdentAttributesOfType(IdentifiableType.TWO_WINDINGS_TRANSFORMER, "l3"),
            mkIdentAttributesOfType(IdentifiableType.LINE, "l4")
        ).flatMap(Collection::stream).collect(Collectors.toList());

        final List<Contingency> contingencies = List.of(
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

        final List<SensitivityAnalysisResult.SensitivityContingencyStatus> contingenciesStatuses = contingencies.stream()
            .map(c -> new SensitivityAnalysisResult.SensitivityContingencyStatus(c.getId(),
                SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

        final List<SensitivityFactor> sensitivityFactors = List.of(
            makeMWFactor("l1", "GEN", false),
            makeMWFactor("l2", "GEN", false),
            makeMWFactor("l3", "LOAD", false),
            new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "l3",
                SensitivityVariableType.INJECTION_ACTIVE_POWER, "LOAD", false,
                ContingencyContext.create("l3", ContingencyContextType.SPECIFIC))
        );

        final List<SensitivityValue> sensitivityValues = List.of(
            new SensitivityValue(0, -1, 500.1, 2.9),
            new SensitivityValue(1, -1, 500.2, 2.8),
            new SensitivityValue(2, -1, 500.9, 2.1),
            new SensitivityValue(0, 0, 500.3, 2.7),
            new SensitivityValue(0, 1, 500.4, 2.6),
            new SensitivityValue(0, 2, 500.5, 2.5),
            new SensitivityValue(1, 1, 500.6, 2.4),
            new SensitivityValue(3, 0, 500.7, 2.3),
            new SensitivityValue(1, 2, 500.8, 2.2)
        );
        final SensitivityAnalysisResult result = new SensitivityAnalysisResult(sensitivityFactors,
            contingenciesStatuses,
            sensitivityValues);

        // network store service mocking
        Network network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        String variantId = "vn1";
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, variantId);

        SensitivityAnalysis.Runner runner = mock(SensitivityAnalysis.Runner.class);
        given(runner.getName()).willReturn(defaultSensitivityAnalysisProvider);
        given(runner.runAsync(eq(network), eq(variantId), anyList(), anyList(), anyList(),
            any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class)))
            .willReturn(CompletableFuture.completedFuture(result));
        workerService.setSensitivityAnalysisFactorySupplier(provider -> runner);

        doAnswer(i -> null).when(reportService).sendReport(any(), any());
        given(uuidGeneratorService.generate()).willReturn(resultUuid);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);

        //UUID injectionsSetAleaUuid = UUID.randomUUID();
        //given(actionsService.getContingencyList(injectionsSetAleaUuid, NETWORK_UUID, variantId)).willReturn(contingencies);

        //UUID monitoredBranchesFilterUuid = UUID.randomUUID();
        //UUID generatorsFiltersInjectionsSetUuid = UUID.randomUUID();
        //UUID loadsFiltersInjectionsSetUuid = UUID.randomUUID();
        //given(filterService.getIdentifiablesFromFilter(monitoredBranchesFilterUuid, NETWORK_UUID, variantId)).willReturn(branches);
        //given(filterService.getIdentifiablesFromFilter(generatorsFiltersInjectionsSetUuid, NETWORK_UUID, variantId)).willReturn(generators);
        //given(filterService.getIdentifiablesFromFilter(loadsFiltersInjectionsSetUuid, NETWORK_UUID, variantId)).willReturn(loads);

        SensitivityAnalysisInputData inputData = SensitivityAnalysisInputData.builder()
            .resultsThreshold(0.10)
            .sensitivityInjectionsSets(List.of())
            .sensitivityInjections(List.of())
            .sensitivityHVDCs(List.of())
            .sensitivityPSTs(List.of())
            .sensitivityNodes(List.of())
            .parameters(SensitivityAnalysisParameters.load())
            .build();

        UUID gottenResultUuid = analysisService.runAndSaveResult(
            new SensitivityAnalysisRunContext(NETWORK_UUID, variantId,
                Collections.emptyList(), inputData,
                null, null, null, null));
        assertThat(gottenResultUuid, not(nullValue()));
        assertThat(gottenResultUuid, is(resultUuid));

        ResultsSelector selectorN = ResultsSelector.builder()
            .isJustBefore(true)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .functionIds(branches.stream().map(IdentifiableAttributes::getId).collect(Collectors.toList()))
            .variableIds(Stream.concat(generators.stream(), loads.stream())
                .map(IdentifiableAttributes::getId).collect(Collectors.toList()))
            .sortKeysWithWeightAndDirection(Map.of(
                ResultsSelector.SortKey.SENSITIVITY, -1,
                ResultsSelector.SortKey.REFERENCE, 2,
                ResultsSelector.SortKey.VARIABLE, 3,
                ResultsSelector.SortKey.FUNCTION, 4))
            .build();

        SensitivityRunQueryResult gottenResult = analysisService.getRunResult(resultUuid, selectorN);
        assertThat(gottenResult, not(nullValue()));
        assertThat(gottenResult.getSensitivities().size(), is(3));
    }
}

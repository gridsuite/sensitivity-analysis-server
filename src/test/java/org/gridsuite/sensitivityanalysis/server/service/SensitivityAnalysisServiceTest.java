/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gridsuite.sensitivityanalysis.server.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.SensitivityAnalysisApplication;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.hamcrest.core.Every;
import org.junit.Before;
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
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
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
import static org.gridsuite.sensitivityanalysis.server.util.OrderMatcher.isOrderedAccordingTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
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

    @MockBean
    private ReportService reportService;

    @MockBean
    private UuidGeneratorService uuidGeneratorService;

    @Value("${sensitivity-analysis.default-provider}")
    String defaultSensitivityAnalysisProvider;

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final String VARIANT_ID = VariantManagerConstants.INITIAL_VARIANT_ID;
    private static final Network NETWORK = new NetworkFactoryImpl().createNetwork("ghost network", "absent format");
    private static final SensitivityAnalysis.Runner RUNNER = mock(SensitivityAnalysis.Runner.class);
    private static final SensitivityFunctionType MW_FUNC_TYPE = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
    private static final SensitivityVariableType MW_VAR_TYPE = SensitivityVariableType.INJECTION_ACTIVE_POWER;

    @Before
    public void setUp() {
        given(RUNNER.getName()).willReturn(defaultSensitivityAnalysisProvider);
        workerService.setSensitivityAnalysisFactorySupplier(provider -> RUNNER);
        doAnswer(i -> null).when(reportService).sendReport(any(), any());
        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(NETWORK);
    }

    private static List<SensitivityFactor> makeMWFactors(String funcId, String varId, List<String> mayContingencies) {
        List<SensitivityFactor> aleaFactors;
        if (mayContingencies == null) {
            aleaFactors = List.of(new SensitivityFactor(MW_FUNC_TYPE, funcId, MW_VAR_TYPE, varId, false, ContingencyContext.all()));
        } else {
            aleaFactors = mayContingencies.stream().map(aleaId ->
                    new SensitivityFactor(MW_FUNC_TYPE, funcId, MW_VAR_TYPE, varId, false,
                        ContingencyContext.create(aleaId, ContingencyContextType.SPECIFIC)))
                .collect(Collectors.toList());
            aleaFactors.add(0, new SensitivityFactor(MW_FUNC_TYPE, funcId, MW_VAR_TYPE, varId, false, ContingencyContext.none()));
        }
        return aleaFactors;
    }

    private static SensitivityAnalysisInputData getDummyInputData() {
        return SensitivityAnalysisInputData.builder()
            .sensitivityInjectionsSets(List.of())
            .sensitivityInjections(List.of())
            .sensitivityHVDCs(List.of())
            .sensitivityPSTs(List.of())
            .sensitivityNodes(List.of())
            .parameters(SensitivityAnalysisParameters.load())
            .build();
    }

    @Test
    public void test0() {
        SensitivityAnalysisRunContext context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID,
                Collections.emptyList(), getDummyInputData(), null, null, null, null);
        testBasic(true, context);
        testBasic(false, context);
    }

    @Test
    public void testWithLFParams() {
        SensitivityAnalysisInputData inputData = SensitivityAnalysisInputData.builder()
                .sensitivityInjectionsSets(List.of())
                .sensitivityInjections(List.of())
                .sensitivityHVDCs(List.of())
                .sensitivityPSTs(List.of())
                .sensitivityNodes(List.of())
                .parameters(null) // null LF params
                .loadFlowSpecificParameters(null)
                .build();
        SensitivityAnalysisRunContext context = new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID,
                Collections.emptyList(), inputData, null, "OpenLoadFlow", null, null);
        testBasic(true, context);

        // with non-null LF params
        inputData.setParameters(SensitivityAnalysisParameters.load());
        testBasic(true, context);

        // with empty specific parameters
        inputData.setLoadFlowSpecificParameters(Map.of());
        testBasic(true, context);

        // with 2 specific parameters
        inputData.setLoadFlowSpecificParameters(Map.of("reactiveRangeCheckMode", "TARGET_P", "plausibleActivePowerLimit", "5000.0"));
        testBasic(true, context);
    }

    private void testBasic(boolean specific, SensitivityAnalysisRunContext context) {
        List<String> aleaIds = List.of("a1", "a2", "a3");
        final List<SensitivityAnalysisResult.SensitivityContingencyStatus> contingenciesStatuses = aleaIds.stream()
            .map(aleaId -> new SensitivityAnalysisResult.SensitivityContingencyStatus(aleaId, SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

        final List<SensitivityFactor> sensitivityFactors = Stream.of(
                makeMWFactors("l1", "GEN", null), // factor index 0      ; for all()
                makeMWFactors("l2", "GEN", List.of()), // factor index 1 ; for none()
                makeMWFactors("l3", "LOAD", aleaIds)   // factor indices 2, 3, 4 & 5
            ).flatMap(Collection::stream).collect(Collectors.toList());

        final List<SensitivityValue> sensitivityValues = new ArrayList<>(List.of(
            // l1 x GEN, before + a1, a2, a3 (implicit)
            new SensitivityValue(0, -1, 500.1, 2.9),
            new SensitivityValue(0, 0, 500.3, 2.7),
            new SensitivityValue(0, 1, 500.4, 2.6),
            new SensitivityValue(0, 2, 500.5, 2.5),
            // l2 x GEN, just before
            new SensitivityValue(1, -1, 500.2, 2.8),
            // l3 x LOAD, before + a1, a2, a3 (explicit)
            new SensitivityValue(2, -1, 500.9, 2.1),
            new SensitivityValue(specific ? 3 : 2, 1, 500.6, 2.4),
            new SensitivityValue(specific ? 4 : 2, 0, 500.7, 2.3),
            new SensitivityValue(specific ? 5 : 2, 2, 500.8, 2.2)
        ));
        Collections.shuffle(sensitivityValues);

        final SensitivityAnalysisResult result = new SensitivityAnalysisResult(sensitivityFactors,
            contingenciesStatuses,
            sensitivityValues);

        final UUID resultUuid = UUID.randomUUID();
        given(uuidGeneratorService.generate()).willReturn(resultUuid);
        given(RUNNER.runAsync(eq(NETWORK), eq(VARIANT_ID), anyList(), anyList(), anyList(),
            any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class)))
            .willReturn(CompletableFuture.completedFuture(result));

        UUID gottenResultUuid = analysisService.runAndSaveResult(context);
        assertThat(gottenResultUuid, not(nullValue()));
        assertThat(gottenResultUuid, is(resultUuid));

        ResultsSelector.ResultsSelectorBuilder builder = ResultsSelector.builder();
        ResultsSelector selectorN = builder
            .isJustBefore(true)
            .functionType(MW_FUNC_TYPE)
            .functionIds(Set.of("l1", "l2", "l3"))
            .variableIds(Set.of("GEN", "LOAD"))
            .sortKeysWithWeightAndDirection(Map.of(
                ResultsSelector.SortKey.SENSITIVITY, -1,
                ResultsSelector.SortKey.REFERENCE, 2,
                ResultsSelector.SortKey.VARIABLE, 3,
                ResultsSelector.SortKey.FUNCTION, 4))
            .build();

        SensitivityRunQueryResult gottenResult;
        gottenResult = analysisService.getRunResult(resultUuid, selectorN);
        assertThat(gottenResult, not(nullValue()));
        List<? extends SensitivityOfTo> sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities, not(nullValue()));
        assertThat(sensitivities.size(), is(3));
        assertThat(sensitivities, Every.everyItem(Matchers.any(SensitivityOfTo.class)));
        List<Double> sensitivityVals = sensitivities.stream().map(SensitivityOfTo::getValue).collect(Collectors.toList());
        assertThat(sensitivityVals, isOrderedAccordingTo(Comparator.<Double>reverseOrder()));
        assertThat(sensitivityVals, IsIterableContainingInOrder.contains(500.9, 500.2, 500.1));

        selectorN = builder.sortKeysWithWeightAndDirection(Map.of(ResultsSelector.SortKey.SENSITIVITY, 1)).build();
        gottenResult = analysisService.getRunResult(resultUuid, selectorN);
        sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities.size(), is(3));
        assertThat(sensitivities.stream().map(SensitivityOfTo::getValue).collect(Collectors.toList()),
            isOrderedAccordingTo(Comparator.<Double>naturalOrder()));

        ResultsSelector selectorNK = builder.isJustBefore(false).build();
        gottenResult = analysisService.getRunResult(resultUuid, selectorNK);
        assertThat(gottenResult, not(nullValue()));
        sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities, not(nullValue()));
        assertThat(sensitivities.size(), is(6));
        assertThat(sensitivities, Every.everyItem(Matchers.instanceOf(SensitivityWithContingency.class)));
        sensitivityVals = sensitivities.stream().map(SensitivityOfTo::getValue).collect(Collectors.toList());
        assertThat(sensitivityVals, not(hasItem(500.2)));
        assertThat(sensitivityVals, isOrderedAccordingTo(Comparator.<Double>naturalOrder()));

        ResultsSelector chunkerSelector = builder.pageNumber(1).pageSize(3).build();
        gottenResult = analysisService.getRunResult(resultUuid, chunkerSelector);
        assertThat(gottenResult, not(nullValue()));
        sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities, not(nullValue()));
        sensitivityVals = sensitivities.stream().map(SensitivityOfTo::getValue).collect(Collectors.toList());
        assertThat(sensitivities.size(), is(3));
        assertThat(sensitivityVals, Every.everyItem(is(500.9)));

        ResultsSelector bogusChunkerSelector = builder.pageSize(3).pageNumber(3).build();
        gottenResult = analysisService.getRunResult(resultUuid, bogusChunkerSelector);
        assertThat(gottenResult, not(nullValue()));
        sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities.size(), is(0));
    }

    @Test
    public void testNoNKStillOK() {
        List<String> aleaIds = List.of("a1", "a2", "a3");
        final List<SensitivityAnalysisResult.SensitivityContingencyStatus> contingenciesStatuses = aleaIds.stream()
            .map(aleaId -> new SensitivityAnalysisResult.SensitivityContingencyStatus(aleaId, SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

        final List<SensitivityFactor> sensitivityFactors = Stream.of(
            makeMWFactors("l1", "GEN", null), // factor index 0      ; for all()
            makeMWFactors("l2", "GEN", List.of()), // factor index 1 ; for none()
            makeMWFactors("l3", "LOAD", aleaIds)   // factor indices 2, 3, 4 & 5
        ).flatMap(Collection::stream).collect(Collectors.toList());

        final List<SensitivityValue> sensitivityValues = new ArrayList<>(List.of(
            new SensitivityValue(0, -1, 500.1, 2.9),
            new SensitivityValue(1, -1, 500.2, 2.8),
            new SensitivityValue(2, -1, 500.9, 2.1)
        ));
        Collections.shuffle(sensitivityValues);

        final SensitivityAnalysisResult result = new SensitivityAnalysisResult(sensitivityFactors,
            contingenciesStatuses,
            sensitivityValues);

        final UUID resultUuid = UUID.randomUUID();
        given(uuidGeneratorService.generate()).willReturn(resultUuid);
        given(RUNNER.runAsync(eq(NETWORK), eq(VARIANT_ID), anyList(), anyList(), anyList(),
            any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class)))
            .willReturn(CompletableFuture.completedFuture(result));

        UUID gottenResultUuid = analysisService.runAndSaveResult(
            new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID,
                Collections.emptyList(), getDummyInputData(), null, null, null, null));
        assertThat(gottenResultUuid, not(nullValue()));
        assertThat(gottenResultUuid, is(resultUuid));

        ResultsSelector.ResultsSelectorBuilder builder = ResultsSelector.builder();
        ResultsSelector selectorN = builder
            .isJustBefore(true)
            .functionType(MW_FUNC_TYPE)
            .functionIds(Set.of("l1", "l2", "l3"))
            .variableIds(Set.of("GEN", "LOAD"))
            .sortKeysWithWeightAndDirection(Map.of(ResultsSelector.SortKey.SENSITIVITY, -1))
            .build();

        SensitivityRunQueryResult gottenResult;
        gottenResult = analysisService.getRunResult(resultUuid, selectorN);
        assertThat(gottenResult, not(nullValue()));
        List<? extends SensitivityOfTo> sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities, not(nullValue()));
        assertThat(sensitivities.size(), is(3));
        assertThat(sensitivities, Every.everyItem(Matchers.any(SensitivityOfTo.class)));
        List<Double> sensitivityVals = sensitivities.stream().map(SensitivityOfTo::getValue).collect(Collectors.toList());
        assertThat(sensitivityVals, isOrderedAccordingTo(Comparator.<Double>reverseOrder()));
        assertThat(sensitivityVals, IsIterableContainingInOrder.contains(500.9, 500.2, 500.1));

        ResultsSelector selectorNK = builder.isJustBefore(false).build();
        gottenResult = analysisService.getRunResult(resultUuid, selectorNK);
        assertThat(gottenResult, not(nullValue()));
        sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities, not(nullValue()));
        assertThat(sensitivities.size(), is(0));
    }

    @Test
    public void testNoNStillOK() {
        testNoN(false);
        testNoN(true);
    }

    private void testNoN(boolean specific) {
        List<String> aleaIds = List.of("a1", "a2", "a3");
        final List<SensitivityAnalysisResult.SensitivityContingencyStatus> contingenciesStatuses = aleaIds.stream()
            .map(aleaId -> new SensitivityAnalysisResult.SensitivityContingencyStatus(aleaId, SensitivityAnalysisResult.Status.SUCCESS))
            .collect(Collectors.toList());

        final List<SensitivityFactor> sensitivityFactors = Stream.of(
            makeMWFactors("l1", "GEN", null), // factor index 0      ; for all()
            makeMWFactors("l2", "GEN", List.of()), // factor index 1 ; for none()
            makeMWFactors("l3", "LOAD", aleaIds)   // factor indices 2, 3, 4 & 5
        ).flatMap(Collection::stream).collect(Collectors.toList());

        final List<SensitivityValue> sensitivityValues = new ArrayList<>(List.of(
            new SensitivityValue(0, 0, 500.3, 2.7),
            new SensitivityValue(0, 1, 500.4, 2.6),
            new SensitivityValue(0, 2, 500.5, 2.5),
            new SensitivityValue(specific ? 3 : 2, 1, 500.6, 2.4),
            new SensitivityValue(specific ? 4 : 2, 0, 500.7, 2.3),
            new SensitivityValue(specific ? 5 : 2, 2, 500.8, 2.2)
        ));
        Collections.shuffle(sensitivityValues);

        final SensitivityAnalysisResult result = new SensitivityAnalysisResult(sensitivityFactors,
            contingenciesStatuses,
            sensitivityValues);

        final UUID resultUuid = UUID.randomUUID();
        given(uuidGeneratorService.generate()).willReturn(resultUuid);
        given(RUNNER.runAsync(eq(NETWORK), eq(VARIANT_ID), anyList(), anyList(), anyList(),
            any(SensitivityAnalysisParameters.class), any(ComputationManager.class), any(Reporter.class)))
            .willReturn(CompletableFuture.completedFuture(result));

        UUID gottenResultUuid = analysisService.runAndSaveResult(
            new SensitivityAnalysisRunContext(NETWORK_UUID, VARIANT_ID,
                Collections.emptyList(), getDummyInputData(), null, null, null, null));
        assertThat(gottenResultUuid, not(nullValue()));
        assertThat(gottenResultUuid, is(resultUuid));

        ResultsSelector.ResultsSelectorBuilder builder = ResultsSelector.builder();
        ResultsSelector selectorN = builder
            .isJustBefore(true)
            .functionType(MW_FUNC_TYPE)
            .functionIds(Set.of("l1", "l2", "l3"))
            .variableIds(Set.of("GEN", "LOAD"))
            .sortKeysWithWeightAndDirection(Map.of(ResultsSelector.SortKey.SENSITIVITY, -1))
            .build();

        SensitivityRunQueryResult gottenResult;
        gottenResult = analysisService.getRunResult(resultUuid, selectorN);
        assertThat(gottenResult, not(nullValue()));
        List<? extends SensitivityOfTo> sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities, not(nullValue()));
        assertThat(sensitivities.size(), is(0));
        List<Double> sensitivityVals;

        ResultsSelector selectorNK = builder.isJustBefore(false).build();
        gottenResult = analysisService.getRunResult(resultUuid, selectorNK);
        assertThat(gottenResult, not(nullValue()));
        sensitivities = gottenResult.getSensitivities();
        assertThat(sensitivities, not(nullValue()));
        assertThat(sensitivities.size(), is(6));
        assertThat(sensitivities, Every.everyItem(Matchers.instanceOf(SensitivityWithContingency.class)));
        sensitivityVals = sensitivities.stream().map(SensitivityOfTo::getValue).collect(Collectors.toList());
        assertThat(sensitivityVals, not(hasItem(500.2)));
        assertThat(sensitivityVals, isOrderedAccordingTo(Comparator.<Double>naturalOrder()));
    }
}

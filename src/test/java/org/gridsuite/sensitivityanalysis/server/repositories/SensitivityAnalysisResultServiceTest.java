/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.sensitivity.*;
import com.vladmihalcea.sql.SQLStatementCountValidator;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisResultService;
import org.junit.Before;
import org.junit.Test;
import org.apache.commons.compress.utils.Lists;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.SortKey;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.util.ContingencyResult;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityResultsBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.sensitivityanalysis.server.util.TestUtils.assertRequestsCount;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
class SensitivityAnalysisResultServiceTest {
    public static final String BRANCH_ID1 = "branchId1";
    public static final String BRANCH_ID2 = "branchId2";
    public static final String GEN_ID1 = "genId1";
    public static final String GEN_ID2 = "genId2";
    public static final String CONTINGENCY_ID1 = "contingencyId1";
    public static final String CONTINGENCY_ID2 = "contingencyId2";

    @Autowired
    private SensitivityAnalysisResultService sensitivityAnalysisResultService;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private ContingencyResultRepository contingencyResultRepository;

    @Autowired
    private SensitivityResultRepository sensitivityResultRepository;

    @Autowired
    private GlobalStatusRepository globalStatusRepository;

    @BeforeEach
    public void setUp() {
        sensitivityAnalysisResultService.deleteAll();
    }

    @Test
    void testCreateResult() {
        UUID resultUuid = UUID.randomUUID();

        SQLStatementCountValidator.reset();
        createResult(resultUuid);

        assertRequestsCount(4, 4, 0, 0);
        assertThat(analysisResultRepository.findByResultUuid(resultUuid)).isNotNull();
        assertThat(contingencyResultRepository.findAll()).hasSize(2);
        assertThat(sensitivityResultRepository.findAll()).hasSize(12);
    }

    @Test
    void testDeleteResult() {
        UUID resultUuid = UUID.randomUUID();
        createResult(resultUuid);
        sensitivityAnalysisResultService.insertStatus(List.of(resultUuid), "SUCCESS");

        SQLStatementCountValidator.reset();
        sensitivityAnalysisResultService.delete(resultUuid);

        assertRequestsCount(2, 0, 0, 6);
        assertThat(analysisResultRepository.findByResultUuid(resultUuid)).isNull();
        assertThat(globalStatusRepository.findByResultUuid(resultUuid)).isNull();
        assertThat(contingencyResultRepository.findAll()).isEmpty();
        assertThat(sensitivityResultRepository.findAll()).isEmpty();
    }

    @Test
    void testDeleteResults() {
        IntStream.range(0, 10).forEach(i -> {
            UUID resultUuid = UUID.randomUUID();
            createResult(resultUuid);
            sensitivityAnalysisResultService.insertStatus(List.of(resultUuid), "SUCCESS"); // SensitivityAnalysisStatus.COMPLETED ??
        });

        SQLStatementCountValidator.reset();
        sensitivityAnalysisResultService.deleteAll();

        assertRequestsCount(0, 0, 0, 6);
        assertThat(analysisResultRepository.findAll()).isEmpty();
        assertThat(globalStatusRepository.findAll()).isEmpty();
        assertThat(contingencyResultRepository.findAll()).isEmpty();
        assertThat(sensitivityResultRepository.findAll()).isEmpty();
    }

    @Test
    void testGetRunResultInNAndSortedOnSensitivities() {
        UUID resultUuid = UUID.randomUUID();
        createResult(resultUuid);
        fillResult(resultUuid);

        var selectorN = ResultsSelector.builder()
            .tabSelection(ResultTab.N)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .sortKeysWithWeightAndDirection(Map.of(SortKey.SENSITIVITY, 1))
            .build();
        var result = sensitivityAnalysisResultRepository.getRunResult(resultUuid, selectorN);
        var sensitivities = result.getSensitivities();
        assertThat(sensitivities)
            .isNotNull()
            .hasSize(4)
            .extracting(SensitivityOfTo::getValue)
            .containsOnly(0.1, 0.4, 0.7, 1.0)
            .isSorted();
    }

    @Test
    void testGetRunResultInNAndReverseSortedOnSensitivities() {
        UUID resultUuid = UUID.randomUUID();
        createResult(resultUuid);
        fillResult(resultUuid);

        var selectorN = ResultsSelector.builder()
            .tabSelection(ResultTab.N)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .sortKeysWithWeightAndDirection(Map.of(SortKey.SENSITIVITY, -1))
            .build();
        var result = sensitivityAnalysisResultRepository.getRunResult(resultUuid, selectorN);
        var sensitivities = result.getSensitivities();
        assertThat(sensitivities)
            .isNotNull()
            .hasSize(4)
            .extracting(SensitivityOfTo::getValue)
            .containsOnly(0.1, 0.4, 0.7, 1.0)
            .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void testGetRunResultInNKSortedBySensitivitiesAfterContingency() {
        UUID resultUuid = UUID.randomUUID();
        createResult(resultUuid);
        fillResult(resultUuid);

        var selectorNK = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .sortKeysWithWeightAndDirection(Map.of(SortKey.POST_SENSITIVITY, 1))
            .build();
        var result = sensitivityAnalysisResultRepository.getRunResult(resultUuid, selectorNK);
        assertThat(result).isNotNull();
        var sensitivities = result.getSensitivities();
        assertThat(sensitivities).isNotNull().hasSize(8);
        var afterContingencyValues = sensitivities.stream().map(s -> (SensitivityWithContingency) s).map(SensitivityWithContingency::getValueAfter).toList();
        assertThat(afterContingencyValues).containsExactly(-1.0, -0.4, 0.2, 0.3, 0.5, 0.6, 0.8, 0.9).isSorted();
    }

    @Test
    void testGetRunResultPagedInNKSortedBySensitivitiesAfterContingency() {
        UUID resultUuid = UUID.randomUUID();
        createResult(resultUuid);
        fillResult(resultUuid);

        var pagedSelector = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .sortKeysWithWeightAndDirection(Map.of(SortKey.POST_SENSITIVITY, 1))
            .pageNumber(1)
            .pageSize(3)
            .build();
        var result = sensitivityAnalysisResultRepository.getRunResult(resultUuid, pagedSelector);
        assertThat(result).isNotNull();
        var sensitivities = result.getSensitivities();
        assertThat(sensitivities).isNotNull();
        var sensitivityValues = sensitivities.stream().map(s -> (SensitivityWithContingency) s).map(SensitivityWithContingency::getValueAfter).collect(Collectors.toList());
        assertThat(sensitivityValues).containsOnly(0.3, 0.5, 0.6).hasSize(3);
    }

    @Test
    void testGetRunResultEmptyPaged() {
        UUID resultUuid = UUID.randomUUID();
        createResult(resultUuid);
        fillResult(resultUuid);

        var emptyPagedSelector = ResultsSelector.builder()
            .tabSelection(ResultTab.N_K)
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .pageSize(3)
            .pageNumber(3)
            .build();
        var result = sensitivityAnalysisResultRepository.getRunResult(resultUuid, emptyPagedSelector);
        assertThat(result).isNotNull();
        var sensitivities = result.getSensitivities();
        assertThat(sensitivities).isEmpty();
    }

    @Test
    void testNotFailingWhenWritingContingencyResultThatDoesNotExist() {
        UUID resultUuid = UUID.randomUUID();
        createResult(resultUuid);

        assertDoesNotThrow(
            () -> sensitivityAnalysisResultRepository.writeContingenciesStatus(
                resultUuid,
                List.of(new ContingencyResult(10, SensitivityAnalysisResult.Status.SUCCESS)))
        );
    }

    private void createResult(UUID resultUuid) {
        List<Contingency> contingencies = List.of(
            Contingency.builder(CONTINGENCY_ID1).build(),
            Contingency.builder(CONTINGENCY_ID2).build()
        );
        List<List<SensitivityFactor>> factors = createFactors(List.of(BRANCH_ID1, BRANCH_ID2), List.of(GEN_ID1, GEN_ID2), contingencies);
        var analysisResult = sensitivityAnalysisResultRepository.insertAnalysisResult(resultUuid);
        Map<String, ContingencyResultEntity> contingencyResultsByContingencyId = SensitivityResultsBuilder.buildContingencyResults(contingencies, analysisResult);
        sensitivityAnalysisResultRepository.saveAllContingencyResultsAndFlush(contingencyResultsByContingencyId.values().stream().collect(Collectors.toSet()));
        var results = SensitivityResultsBuilder.buildSensitivityResults(factors, analysisResult, contingencyResultsByContingencyId);
        sensitivityAnalysisResultRepository.saveAllResultsAndFlush(results.getLeft());
        sensitivityAnalysisResultRepository.saveAllResultsAndFlush(results.getRight());
    }

    private void fillResult(UUID resultUuid) {
        final List<SensitivityValue> sensitivityValues = new ArrayList<>(List.of(
            new SensitivityValue(0, -1, 0.1, 501),
            new SensitivityValue(1, 0, 0.2, 502),
            new SensitivityValue(2, 1, 0.3, 503),
            new SensitivityValue(3, 2, 0.4, 504),
            new SensitivityValue(4, -1, 0.5, 505),
            new SensitivityValue(5, -1, 0.6, 506),
            new SensitivityValue(6, 1, 0.7, 507),
            new SensitivityValue(7, 0, 0.8, 508),
            new SensitivityValue(8, 2, 0.9, 509),
            new SensitivityValue(9, -1, 1.0, 510),
            new SensitivityValue(10, 1, -1.0, 511),
            new SensitivityValue(11, 0, -0.4, 512)
        ));
        sensitivityAnalysisResultRepository.writeSensitivityValues(resultUuid, sensitivityValues);
    }

    private static List<List<SensitivityFactor>> createFactors(List<String> branchIds, List<String> variableIds, List<Contingency> contingencies) {
        return branchIds.stream().flatMap(branchId -> variableIds.stream().map(variableId -> createFactors(branchId, variableId, contingencies))).toList();
    }

    private static List<SensitivityFactor> createFactors(String branchId, String variableId, List<Contingency> contingencies) {
        List<SensitivityFactor> factors = Lists.newArrayList();
        factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, ContingencyContext.none()));
        factors.addAll(
            contingencies.stream()
                .map(c -> new SensitivityFactor(
                    SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                    branchId,
                    SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableId,
                    false,
                    ContingencyContext.specificContingency(c.getId())))
                .toList());
        return factors;
    }
}

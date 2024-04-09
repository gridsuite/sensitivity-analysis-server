/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.contingency.*;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
class SensitivityResultsBuilderTest {
    public static final String FUNCTION_ID_1 = "functionId1";
    public static final String FUNCTION_ID_2 = "functionId2";
    public static final String VARIABLE_ID_1 = "variableId1";
    public static final String VARIABLE_ID_2 = "variableId2";
    public static final String CONTINGENCY_ID_1 = "contingencyId1";
    public static final String CONTINGENCY_ID_2 = "contingencyId2";
    public static final String CONTINGENCY_ID_3 = "contingencyId3";

    @Test
    void testBuildContingencyResults() {
        List<Contingency> contingencies = getContingencies();
        AnalysisResultEntity analysisResult = new AnalysisResultEntity(UUID.randomUUID(), LocalDateTime.now());

        Map<String, ContingencyResultEntity> contingencyResultsByContingencyId = SensitivityResultsBuilder.buildContingencyResults(contingencies, analysisResult);

        List<ContingencyResultEntity> expectedContingencyResults = getExpectedContingencyResultEntities(analysisResult);

        List<ContingencyResultEntity> sortedContingencyResults = contingencyResultsByContingencyId.values().stream().sorted(Comparator.comparingDouble(ContingencyResultEntity::getIndex)).toList();
        IntStream.range(0, sortedContingencyResults.size()).forEach(i -> compareContingencyResultEntities(sortedContingencyResults.get(i), expectedContingencyResults.get(i)));
    }

    @Test
    void testBuildResults() {
        List<List<SensitivityFactor>> groupedFactors = getGroupedFactors();
        List<Contingency> contingencies = getContingencies();
        AnalysisResultEntity analysisResult = new AnalysisResultEntity(UUID.randomUUID(), LocalDateTime.now());
        Map<String, ContingencyResultEntity> contingencyResultsByContingencyId = SensitivityResultsBuilder.buildContingencyResults(contingencies, analysisResult);

        List<SensitivityResultEntity> results = SensitivityResultsBuilder.buildSensitivityResults(groupedFactors, analysisResult, contingencyResultsByContingencyId);

        SensitivityResultEntity preContingencySensitivityResult1 = new SensitivityResultEntity(0, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, analysisResult, null, null);
        SensitivityResultEntity preContingencySensitivityResult2 = new SensitivityResultEntity(4, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, analysisResult, null, null);
        List<SensitivityResultEntity> expectedResults = getExpectedSensitivityResults(analysisResult, preContingencySensitivityResult1, preContingencySensitivityResult2);

        List<SensitivityResultEntity> sortedResults = results.stream().sorted(Comparator.comparingDouble(SensitivityResultEntity::getFactorIndex)).toList();
        IntStream.range(0, sortedResults.size()).forEach(i -> compareSensitivityResultEntities(sortedResults.get(i), expectedResults.get(i)));
    }

    private static List<List<SensitivityFactor>> getGroupedFactors() {
        return List.of(
            List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, ContingencyContext.none()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, ContingencyContext.specificContingency(CONTINGENCY_ID_1)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, ContingencyContext.specificContingency(CONTINGENCY_ID_2)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, ContingencyContext.specificContingency(CONTINGENCY_ID_3))
            ),
            List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, ContingencyContext.none()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, ContingencyContext.specificContingency(CONTINGENCY_ID_1)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, ContingencyContext.specificContingency(CONTINGENCY_ID_2)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, ContingencyContext.specificContingency(CONTINGENCY_ID_3))
            )
        );
    }

    private static List<Contingency> getContingencies() {
        return List.of(
            new Contingency(CONTINGENCY_ID_1, new BranchContingency("branchContingencyId")),
            new Contingency(CONTINGENCY_ID_2, new TwoWindingsTransformerContingency("twtContingencyId")),
            new Contingency(CONTINGENCY_ID_3, new HvdcLineContingency("hvdcContingencyId"))
        );
    }

    private static List<ContingencyResultEntity> getExpectedContingencyResultEntities(AnalysisResultEntity analysisResult) {
        ContingencyResultEntity contingencyResult1 = new ContingencyResultEntity(0, CONTINGENCY_ID_1, analysisResult);
        ContingencyResultEntity contingencyResult2 = new ContingencyResultEntity(1, CONTINGENCY_ID_2, analysisResult);
        ContingencyResultEntity contingencyResult3 = new ContingencyResultEntity(2, CONTINGENCY_ID_3, analysisResult);
        return List.of(contingencyResult1, contingencyResult2, contingencyResult3);
    }

    private static List<SensitivityResultEntity> getExpectedSensitivityResults(AnalysisResultEntity analysisResult, SensitivityResultEntity preContingencySensitivityResult1, SensitivityResultEntity preContingencySensitivityResult2) {
        List<ContingencyResultEntity> expectedContingencyResults = getExpectedContingencyResultEntities(analysisResult);
        return List.of(
            new SensitivityResultEntity(1, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, analysisResult, expectedContingencyResults.get(0), preContingencySensitivityResult1),
            new SensitivityResultEntity(2, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, analysisResult, expectedContingencyResults.get(1), preContingencySensitivityResult1),
            new SensitivityResultEntity(3, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, FUNCTION_ID_1, SensitivityVariableType.INJECTION_ACTIVE_POWER, VARIABLE_ID_1, false, analysisResult, expectedContingencyResults.get(2), preContingencySensitivityResult1),
            new SensitivityResultEntity(5, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, analysisResult, expectedContingencyResults.get(0), preContingencySensitivityResult2),
            new SensitivityResultEntity(6, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, analysisResult, expectedContingencyResults.get(1), preContingencySensitivityResult2),
            new SensitivityResultEntity(7, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, FUNCTION_ID_2, SensitivityVariableType.TRANSFORMER_PHASE_1, VARIABLE_ID_2, true, analysisResult, expectedContingencyResults.get(2), preContingencySensitivityResult2)
        );
    }

    private static void compareSensitivityResultEntities(SensitivityResultEntity r1, SensitivityResultEntity r2) {
        if (r1 == r2) {
            return;
        }
        compareAnalysisResultEntities(r1.getAnalysisResult(), r2.getAnalysisResult());
        compareSensitivityResultEntities(r1.getPreContingencySensitivityResult(), r2.getPreContingencySensitivityResult());
        compareContingencyResultEntities(r1.getContingencyResult(), r2.getContingencyResult());
        assertEquals(r1.getFunctionType(), r2.getFunctionType());
        assertEquals(r1.getFunctionId(), r2.getFunctionId());
        assertEquals(r1.getVariableType(), r2.getVariableType());
        assertEquals(r1.getVariableId(), r2.getVariableId());
        assertEquals(r1.isVariableSet(), r2.isVariableSet());
        assertEquals(r1.getFactorIndex(), r2.getFactorIndex());
    }

    private static void compareAnalysisResultEntities(AnalysisResultEntity r1, AnalysisResultEntity r2) {
        assertEquals(r1.getResultUuid(), r2.getResultUuid());
        assertEquals(r1.getWriteTimeStamp(), r2.getWriteTimeStamp());
    }

    private static void compareContingencyResultEntities(ContingencyResultEntity r1, ContingencyResultEntity r2) {
        if (r1 == r2) {
            return;
        }
        compareAnalysisResultEntities(r1.getAnalysisResult(), r2.getAnalysisResult());
        assertEquals(r1.getIndex(), r2.getIndex());
        assertEquals(r1.getContingencyId(), r2.getContingencyId());
        assertEquals(r1.getStatus(), r2.getStatus());
    }
}

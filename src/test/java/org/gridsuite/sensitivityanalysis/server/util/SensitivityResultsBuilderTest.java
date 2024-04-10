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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
class SensitivityResultsBuilderTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void testBuildResults() {
        String functionId1 = "functionId1";
        String functionId2 = "functionId2";
        String variableId1 = "variableId1";
        String variableId2 = "variableId2";
        String contingencyId1 = "contingencyId1";
        String contingencyId2 = "contingencyId2";
        String contingencyId3 = "contingencyId3";

        List<List<SensitivityFactor>> groupedFactors = List.of(
            List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, ContingencyContext.none()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, ContingencyContext.specificContingency(contingencyId1)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, ContingencyContext.specificContingency(contingencyId2)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, ContingencyContext.specificContingency(contingencyId3))
            ),
            List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, ContingencyContext.none()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, ContingencyContext.specificContingency(contingencyId1)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, ContingencyContext.specificContingency(contingencyId2)),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, ContingencyContext.specificContingency(contingencyId3))
            )
        );
        List<Contingency> contingencies = List.of(
            new Contingency(contingencyId1, new BranchContingency("branchContingencyId")),
            new Contingency(contingencyId2, new TwoWindingsTransformerContingency("twtContingencyId")),
            new Contingency(contingencyId3, new HvdcLineContingency("hvdcContingencyId"))
        );
        AnalysisResultEntity analysisResult = new AnalysisResultEntity(UUID.randomUUID(), LocalDateTime.now());
        Set<SensitivityResultEntity> results = SensitivityResultsBuilder.buildResults(analysisResult, groupedFactors, contingencies);

        SensitivityResultEntity preContingencySensitivityResult1 = new SensitivityResultEntity(0, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, analysisResult, null, null);
        SensitivityResultEntity preContingencySensitivityResult2 = new SensitivityResultEntity(4, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, analysisResult, null, null);
        ContingencyResultEntity contingencyResult1 = new ContingencyResultEntity(0, contingencyId1, analysisResult);
        ContingencyResultEntity contingencyResult2 = new ContingencyResultEntity(1, contingencyId2, analysisResult);
        ContingencyResultEntity contingencyResult3 = new ContingencyResultEntity(2, contingencyId3, analysisResult);

        List<SensitivityResultEntity> expectedResults = List.of(
            new SensitivityResultEntity(1, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, analysisResult, contingencyResult1, preContingencySensitivityResult1),
            new SensitivityResultEntity(2, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, analysisResult, contingencyResult2, preContingencySensitivityResult1),
            new SensitivityResultEntity(3, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId1, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId1, false, analysisResult, contingencyResult3, preContingencySensitivityResult1),
            new SensitivityResultEntity(5, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, analysisResult, contingencyResult1, preContingencySensitivityResult2),
            new SensitivityResultEntity(6, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, analysisResult, contingencyResult2, preContingencySensitivityResult2),
            new SensitivityResultEntity(7, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, functionId2, SensitivityVariableType.TRANSFORMER_PHASE_1, variableId2, true, analysisResult, contingencyResult3, preContingencySensitivityResult2)
        );
        List<SensitivityResultEntity> sortedResults = results.stream().sorted(Comparator.comparingDouble(SensitivityResultEntity::getFactorIndex)).toList();
        IntStream.range(0, sortedResults.size()).forEach(i -> compareSensitivityResultEntities(sortedResults.get(i), expectedResults.get(i)));
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

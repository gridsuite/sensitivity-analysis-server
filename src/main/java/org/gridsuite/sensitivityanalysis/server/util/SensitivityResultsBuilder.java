/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.contingency.Contingency;
import com.powsybl.sensitivity.SensitivityFactor;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 * <p>
 *     It Builds the entities based on groups of factors and a list of contingencies.
 *     Each group of factors requires a certain structure, starting with the pre-contingency sensitivity factor with
 *     contingency context NONE, then all the contingencies for the same couple (functionId, variableId) with
 *     contingency context SPECIFIC. This is currently the case because this is how it is built in
 *     SensitivityAnalysisInputBuilderService. Anyway, the way the DB model is built kind of depends on this structure too.
 *     So this should not be changed, except deep refactoring.
 * </p>
 */
public final class SensitivityResultsBuilder {

    private SensitivityResultsBuilder() {
        // Should not be instantiated
    }

    public static Set<SensitivityResultEntity> buildResults(AnalysisResultEntity analysisResult,
                                                            List<List<SensitivityFactor>> factorsGroup,
                                                            List<Contingency> contingencies) {
        Map<String, ContingencyResultEntity> contingenciesById = buildContingencyResults(contingencies, analysisResult);
        return buildSensitivityResults(factorsGroup, analysisResult, contingenciesById);
    }

    private static Map<String, ContingencyResultEntity> buildContingencyResults(List<Contingency> contingencies,
                                                                                AnalysisResultEntity analysisResult) {
        return IntStream.range(0, contingencies.size())
            .mapToObj(i -> new ContingencyResultEntity(i, contingencies.get(i).getId(), analysisResult))
            .collect(Collectors.toMap(
                ContingencyResultEntity::getContingencyId,
                Function.identity()
            ));
    }

    private static Set<SensitivityResultEntity> buildSensitivityResults(List<List<SensitivityFactor>> factorsGroups,
                                                                         AnalysisResultEntity analysisResult,
                                                                         Map<String, ContingencyResultEntity> contingenciesById) {
        AtomicInteger factorCounter = new AtomicInteger(0);
        return factorsGroups.stream()
            .flatMap(factorsGroup -> {
                if (factorsGroup.isEmpty()) {
                    return Stream.of();
                }

                // For the information we need to create the entities, all the sensitivity factors of a group are equivalent
                // So we can keep the pre-contingency sensitivity factor.
                SensitivityFactor preContingencySensitivityfactor = factorsGroup.get(0);
                SensitivityResultEntity preContingencySensitivityResult = buildNSensitivityResultEntity(
                    analysisResult,
                    preContingencySensitivityfactor,
                    factorCounter.getAndIncrement());

                // No need to return preContingencySensitivityResult if it's not the only result in the group because it will be saved
                // by JPA cascading persist operation (as it is referenced in the sensitivity results of the contingencies).
                // But if it is the only one we should return it explicitly.
                if (factorsGroup.size() == 1) {
                    return Stream.of(preContingencySensitivityResult);
                }

                // We should skip the first element as we want to only keep contingency related factors here
                return factorsGroup.subList(1, factorsGroup.size()).stream()
                    .map(sensitivityFactor -> buildNKSensitivityResultEntity(
                        analysisResult,
                        preContingencySensitivityResult,
                        contingenciesById.get(sensitivityFactor.getContingencyContext().getContingencyId()),
                        factorCounter.getAndIncrement()));
            })
            .collect(Collectors.toSet());
    }

    private static SensitivityResultEntity buildNSensitivityResultEntity(AnalysisResultEntity analysisResult,
                                                                         SensitivityFactor factor,
                                                                         int index) {
        return new SensitivityResultEntity(
            index,
            factor.getFunctionType(),
            factor.getFunctionId(),
            factor.getVariableType(),
            factor.getVariableId(),
            factor.isVariableSet(),
            analysisResult,
            null,
            null
        );
    }

    private static SensitivityResultEntity buildNKSensitivityResultEntity(AnalysisResultEntity analysisResult,
                                                                          SensitivityResultEntity preContingencySensitivityResult,
                                                                          ContingencyResultEntity contingencyResult,
                                                                          int index) {
        return new SensitivityResultEntity(
            index,
            preContingencySensitivityResult.getFunctionType(),
            preContingencySensitivityResult.getFunctionId(),
            preContingencySensitivityResult.getVariableType(),
            preContingencySensitivityResult.getVariableId(),
            preContingencySensitivityResult.isVariableSet(),
            analysisResult,
            contingencyResult,
            preContingencySensitivityResult
        );
    }
}

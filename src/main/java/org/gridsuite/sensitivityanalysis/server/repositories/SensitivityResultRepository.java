/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.sensitivity.SensitivityFunctionType;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public interface SensitivityResultRepository extends JpaRepository<SensitivityResultEntity, UUID>, JpaSpecificationExecutor<SensitivityResultEntity> {

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity s WHERE s.analysisResult.resultUuid = :analysisResultUuid AND s.preContingencySensitivityResult is not null")
    void deleteAllPostContingenciesByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity s WHERE s.analysisResult.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity s WHERE s.preContingencySensitivityResult is not null")
    void deleteAllPostContingencies();

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity")
    void deleteAll();

    @Query(value = "SELECT distinct s.functionId from SensitivityResultEntity as s " +
        "where s.analysisResult.resultUuid = :resultUuid " +
        "and s.functionType = :sensitivityFunctionType " +
        "and ((:withContingency = true and s.contingencyResult is not null ) or (:withContingency = false and s.contingencyResult is null ))" +
        "order by s.functionId")
    List<String> getDistinctFunctionIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType, boolean withContingency);

    @Query(value = "SELECT distinct s.variableId from SensitivityResultEntity as s " +
        "where s.analysisResult.resultUuid = :resultUuid " +
        "and s.functionType = :sensitivityFunctionType " +
        "and ((:withContingency = true and s.contingencyResult is not null ) or (:withContingency = false and s.contingencyResult is null ))" +
        "order by s.variableId")
    List<String> getDistinctVariableIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType, boolean withContingency);

    @Query(value = "SELECT distinct s.contingencyResult.contingencyId from SensitivityResultEntity as s " +
        "where s.analysisResult.resultUuid = :resultUuid and s.functionType = :sensitivityFunctionType " +
        "order by s.contingencyResult.contingencyId")
    List<String> getDistinctContingencyIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType);
}

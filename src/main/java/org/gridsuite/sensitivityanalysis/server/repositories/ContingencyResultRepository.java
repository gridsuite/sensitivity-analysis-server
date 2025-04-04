/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public interface ContingencyResultRepository extends JpaRepository<ContingencyResultEntity, UUID> {

    @Modifying
    @Query(value = "DELETE FROM ContingencyResultEntity f WHERE f.analysisResult.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Override
    @Query(value = "DELETE FROM ContingencyResultEntity")
    void deleteAll();

    ContingencyResultEntity findByAnalysisResultAndIndex(AnalysisResultEntity analysisResult, int index);
}

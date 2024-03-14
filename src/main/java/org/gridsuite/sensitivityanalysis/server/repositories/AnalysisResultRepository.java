/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import java.util.UUID;

import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResultEntity, UUID> {
    @Modifying
    @Query(value = "DELETE FROM AnalysisResultEntity")
    void deleteAll();

    AnalysisResultEntity findByResultUuid(UUID resultUuid);
}

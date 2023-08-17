/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

public interface SensitivityRepository extends JpaRepository<SensitivityEntity, UUID> {
    Page<SensitivityEntity> findByResult(AnalysisResultEntity result, Pageable pageable);

    Page<SensitivityEntity> findAllByResultAndFactorIndexInAndContingencyIndexIsLessThan(AnalysisResultEntity result, List<Integer> factorIndex, int contingencyIndex, Pageable pageable);
    Page<SensitivityEntity> findAllByResultAndFactorIndexInAndContingencyIndexIsGreaterThan(AnalysisResultEntity result, List<Integer> factorIndex, int contingencyIndex, Pageable pageable);

    List<SensitivityEntity> findByResult(AnalysisResultEntity result);
    Optional<SensitivityEntity> findByResultAndFactorIndexAndContingencyIndexIsLessThan(AnalysisResultEntity result, Integer factorIndex, int contingencyIndex);
}

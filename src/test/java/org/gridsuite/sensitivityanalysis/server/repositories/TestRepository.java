/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparing;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Repository
public class TestRepository {

    private final SensitivityResultRepository sensitivityResultRepository;

    public TestRepository(SensitivityResultRepository sensitivityResultRepository) {
        this.sensitivityResultRepository = sensitivityResultRepository;
    }

    @Transactional
    public List<? extends SensitivityOfTo> createSortedSensitivityList() {
        //contingency.id comparator
        Comparator<ContingencyResultEntity> comparatorByContingencyId = comparing(ContingencyResultEntity::getContingencyId, Comparator.comparing(String::toString));
        //sensitivityId comparator (the toString is needed because UUID comparator is not the same as the string one)
        Comparator<SensitivityResultEntity> comparatorBySensiId = comparing(s -> s.getId().toString());
        //contingency.id and resultUuid (in that order) comparator
        Comparator<SensitivityResultEntity> comparatorByContingencyIdAndSensiId = comparing(SensitivityResultEntity::getContingencyResult, comparatorByContingencyId).thenComparing(comparatorBySensiId);
        return sensitivityResultRepository.findAll().stream()
            .filter(s -> s.getContingencyResult() != null)
            .sorted(comparatorByContingencyIdAndSensiId)
            .map(sensitivityEntity ->
                (SensitivityWithContingency) SensitivityWithContingency.builder().funcId(sensitivityEntity.getFunctionId())
                    .contingencyId(sensitivityEntity.getContingencyResult().getContingencyId())
                    .varId(sensitivityEntity.getVariableId())
                    .varIsAFilter(sensitivityEntity.isVariableSet())
                    .value(sensitivityEntity.getRawSensitivityResult().getValue())
                    .functionReference(sensitivityEntity.getRawSensitivityResult().getFunctionReference())
                    .build())
            .toList();
    }
}

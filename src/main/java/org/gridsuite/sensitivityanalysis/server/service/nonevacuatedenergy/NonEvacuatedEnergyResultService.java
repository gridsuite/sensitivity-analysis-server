/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import org.gridsuite.sensitivityanalysis.server.computation.service.AbstractComputationResultService;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStatus;
import org.gridsuite.sensitivityanalysis.server.entities.nonevacuatedenergy.NonEvacuatedEnergyGlobalStatusEntity;
import org.gridsuite.sensitivityanalysis.server.entities.nonevacuatedenergy.NonEvacuatedEnergyResultEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy.NonEvacuatedEnergyResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy.NonEvacuatedEnergyStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyResultService extends AbstractComputationResultService<NonEvacuatedEnergyStatus> {

    private final NonEvacuatedEnergyStatusRepository nonEvacuatedEnergyStatusRepository;

    private final NonEvacuatedEnergyResultRepository nonEvacuatedEnergyResultRepository;

    public NonEvacuatedEnergyResultService(NonEvacuatedEnergyStatusRepository nonEvacuatedEnergyStatusRepository,
                                           NonEvacuatedEnergyResultRepository nonEvacuatedEnergyResultRepository) {
        this.nonEvacuatedEnergyStatusRepository = nonEvacuatedEnergyStatusRepository;
        this.nonEvacuatedEnergyResultRepository = nonEvacuatedEnergyResultRepository;
    }

    private static NonEvacuatedEnergyGlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new NonEvacuatedEnergyGlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    @Override
    public void insertStatus(List<UUID> resultUuids, NonEvacuatedEnergyStatus status) {
        Objects.requireNonNull(resultUuids);
        nonEvacuatedEnergyStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> toStatusEntity(uuid, status.name())).toList());
    }

    @Transactional
    public void insert(UUID resultUuid, String result, String status) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            nonEvacuatedEnergyResultRepository.save(new NonEvacuatedEnergyResultEntity(resultUuid,
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS),
                result));
        }
        nonEvacuatedEnergyStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    @Transactional
    @Override
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        nonEvacuatedEnergyStatusRepository.deleteByResultUuid(resultUuid);
        nonEvacuatedEnergyResultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    @Override
    public void deleteAll() {
        nonEvacuatedEnergyStatusRepository.deleteAll();
        nonEvacuatedEnergyResultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    @Override
    public NonEvacuatedEnergyStatus findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        NonEvacuatedEnergyGlobalStatusEntity globalEntity = nonEvacuatedEnergyStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return NonEvacuatedEnergyStatus.valueOf(globalEntity.getStatus());
        } else {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public String getRunResult(UUID resultUuid) {
        NonEvacuatedEnergyResultEntity resultEntity = nonEvacuatedEnergyResultRepository.findByResultUuid(resultUuid);
        if (resultEntity == null) {
            return null;
        }
        return resultEntity.getResult();
    }
}

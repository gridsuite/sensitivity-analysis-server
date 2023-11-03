/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy;

import org.gridsuite.sensitivityanalysis.server.entities.GlobalStatusEntity;
import org.gridsuite.sensitivityanalysis.server.entities.nonevacuatedenergy.ResultEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@org.springframework.stereotype.Repository
public class NonEvacuatedEnergyRepository {

    private final NonEvacuatedEnergyStatusRepository globalStatusRepository;

    private final NonEvacuatedEnergyResultRepository nonEvacuatedEnergyResultRepository;

    public NonEvacuatedEnergyRepository(NonEvacuatedEnergyStatusRepository globalStatusRepository,
                                        NonEvacuatedEnergyResultRepository nonEvacuatedEnergyResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.nonEvacuatedEnergyResultRepository = nonEvacuatedEnergyResultRepository;
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void insert(UUID resultUuid, String result, String status) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            nonEvacuatedEnergyResultRepository.save(new ResultEntity(resultUuid,
                LocalDateTime.now().truncatedTo(ChronoUnit.MICROS),
                result));
        }
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        nonEvacuatedEnergyResultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        nonEvacuatedEnergyResultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public String findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return globalEntity.getStatus();
        } else {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public String getRunResult(UUID resultUuid) {
        ResultEntity resultEntity = nonEvacuatedEnergyResultRepository.findByResultUuid(resultUuid);
        if (resultEntity == null) {
            return null;
        }
        return resultEntity.getResult();
    }
}

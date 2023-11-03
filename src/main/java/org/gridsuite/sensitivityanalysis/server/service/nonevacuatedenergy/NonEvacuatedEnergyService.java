/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy.NonEvacuatedEnergyRepository;
import org.gridsuite.sensitivityanalysis.server.service.NotificationService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisCancelContext;
import org.gridsuite.sensitivityanalysis.server.service.UuidGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyService {
    private final NonEvacuatedEnergyRepository nonEvacuatedEnergyRepository;

    private final UuidGeneratorService uuidGeneratorService;

    private final NotificationService notificationService;

    private final ObjectMapper objectMapper;

    @Autowired
    public NonEvacuatedEnergyService(NonEvacuatedEnergyRepository nonEvacuatedEnergyRepository,
                                     UuidGeneratorService uuidGeneratorService,
                                     NotificationService notificationService,
                                     ObjectMapper objectMapper) {
        this.nonEvacuatedEnergyRepository = Objects.requireNonNull(nonEvacuatedEnergyRepository);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.notificationService = notificationService;
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public UUID runAndSaveResult(RunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), SensitivityAnalysisStatus.RUNNING.name());
        notificationService.sendRunMessage("publishNonEvacuatedEnergyRun-out-0", new ResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    public String getRunResult(UUID resultUuid) {
        return nonEvacuatedEnergyRepository.getRunResult(resultUuid);
    }

    public void deleteResult(UUID resultUuid) {
        nonEvacuatedEnergyRepository.delete(resultUuid);
    }

    public void deleteResults() {
        nonEvacuatedEnergyRepository.deleteAll();
    }

    public String getStatus(UUID resultUuid) {
        return nonEvacuatedEnergyRepository.findStatus(resultUuid);
    }

    public void setStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        nonEvacuatedEnergyRepository.insertStatus(resultUuids, status);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage("publishNonEvacuatedEnergyCancel-out-0", new SensitivityAnalysisCancelContext(resultUuid, receiver).toMessage());
    }
}

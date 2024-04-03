/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStatus;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy.NonEvacuatedEnergyRepository;
import org.gridsuite.sensitivityanalysis.server.service.LoadFlowService;
import org.gridsuite.sensitivityanalysis.server.service.NonEvacuatedNotificationService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisCancelContext;
import org.gridsuite.sensitivityanalysis.server.service.UuidGeneratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyService {
    private final String defaultProvider;

    private final NonEvacuatedEnergyRepository nonEvacuatedEnergyRepository;

    private final UuidGeneratorService uuidGeneratorService;

    private final NonEvacuatedNotificationService notificationService;

    private final LoadFlowService loadFlowService;

    private final ObjectMapper objectMapper;

    public NonEvacuatedEnergyService(@Value("${non-evacuated-energy.default-provider}") String defaultProvider,
                                     NonEvacuatedEnergyRepository nonEvacuatedEnergyRepository,
                                     UuidGeneratorService uuidGeneratorService,
                                     NonEvacuatedNotificationService notificationService,
                                     LoadFlowService loadFlowService,
                                     ObjectMapper objectMapper) {
        this.defaultProvider = defaultProvider;
        this.nonEvacuatedEnergyRepository = Objects.requireNonNull(nonEvacuatedEnergyRepository);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.notificationService = notificationService;
        this.loadFlowService = loadFlowService;
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public UUID runAndSaveResult(NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext, UUID loadFlowParametersUuid) {
        Objects.requireNonNull(nonEvacuatedEnergyRunContext);
        // complete nonEvacuatedEnergyRunContext with loadFlowParameters
        completeNonEvacuatedEnergyRunContext(nonEvacuatedEnergyRunContext, loadFlowParametersUuid);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), NonEvacuatedEnergyStatus.RUNNING.name());
        notificationService.sendRunMessage(new NonEvacuatedEnergyResultContext(resultUuid, nonEvacuatedEnergyRunContext).toMessage(objectMapper));
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
        notificationService.sendCancelMessage(new SensitivityAnalysisCancelContext(resultUuid, receiver).toMessage());
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    private void completeNonEvacuatedEnergyRunContext(NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext, UUID loadFlowParametersUuid) {
        LoadFlowParametersValues loadFlowParametersValues = loadFlowService.getLoadFlowParameters(loadFlowParametersUuid, nonEvacuatedEnergyRunContext.getProvider());
        nonEvacuatedEnergyRunContext.getNonEvacuatedEnergyInputData().setLoadFlowSpecificParameters(loadFlowParametersValues.specificParameters());
        nonEvacuatedEnergyRunContext.getNonEvacuatedEnergyInputData().getParameters().setLoadFlowParameters(loadFlowParametersValues.commonParameters());
    }
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.sensitivity.SensitivityFunctionType;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService {
    private SensitivityAnalysisResultRepository resultRepository;

    private UuidGeneratorService uuidGeneratorService;

    private ObjectMapper objectMapper;

    @Autowired
    NotificationService notificationService;

    private Double defaultResultsThreshold;

    @Autowired
    public SensitivityAnalysisService(@Value("${sensi.resultsThreshold}") Double defaultResultsThreshold,
                                      SensitivityAnalysisResultRepository resultRepository,
                                      UuidGeneratorService uuidGeneratorService,
                                      ObjectMapper objectMapper) {
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.defaultResultsThreshold = defaultResultsThreshold;
    }

    public UUID runAndSaveResult(SensitivityAnalysisRunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), SensitivityAnalysisStatus.RUNNING.name());
        notificationService.sendRunMessage(new SensitivityAnalysisResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    public String getResult(UUID resultUuid) {
        return resultRepository.find(resultUuid);
    }

    public List<SensitivityOfTo> getResult(UUID resultUuid, Collection<String> funcIds, Collection<String> varIds,
        SensitivityFunctionType sensitivityFunctionType) {
        return resultRepository.getSensitivities(resultUuid, funcIds, varIds, sensitivityFunctionType);
    }

    public List<SensitivityWithContingency> getResult(UUID resultUuid,
        Collection<String> funcIds, Collection<String> varIds, Collection<String> contingencyIds,
        SensitivityFunctionType sensitivityFunctionType) {
        return resultRepository.getSensitivities(resultUuid, funcIds, varIds, contingencyIds, sensitivityFunctionType);
    }

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults() {
        resultRepository.deleteAll();
    }

    public String getStatus(UUID resultUuid) {
        return resultRepository.findStatus(resultUuid);
    }

    public void setStatus(List<UUID> resultUuids, String status) {
        resultRepository.insertStatus(resultUuids, status);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new SensitivityAnalysisCancelContext(resultUuid, receiver).toMessage());
    }

    public Double getDefaultResultThresholdValue() {
        return defaultResultsThreshold;
    }
}

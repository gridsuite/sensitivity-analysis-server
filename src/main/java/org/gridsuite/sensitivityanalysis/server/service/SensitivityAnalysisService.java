/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.util.ServiceLoaderCache;
import com.powsybl.sensitivity.SensitivityAnalysisProvider;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService {

    private final Double defaultResultsThreshold;

    private final String defaultProvider;

    private final SensitivityAnalysisResultRepository resultRepository;

    private final UuidGeneratorService uuidGeneratorService;

    private final NotificationService notificationService;

    private final ObjectMapper objectMapper;

    @Autowired
    public SensitivityAnalysisService(@Value("${sensitivity-analysis.results-threshold}") Double defaultResultsThreshold,
                                      @Value("${sensitivity-analysis.default-provider}") String defaultProvider,
                                      SensitivityAnalysisResultRepository resultRepository,
                                      UuidGeneratorService uuidGeneratorService,
                                      NotificationService notificationService,
                                      ObjectMapper objectMapper) {
        this.defaultResultsThreshold = defaultResultsThreshold;
        this.defaultProvider = defaultProvider;
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.notificationService = notificationService;
        this.objectMapper = Objects.requireNonNull(objectMapper);
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

    public List<String> getProviders() {
        return new ServiceLoaderCache<>(SensitivityAnalysisProvider.class).getServices().stream()
                .map(SensitivityAnalysisProvider::getName)
                .collect(Collectors.toList());
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }
}

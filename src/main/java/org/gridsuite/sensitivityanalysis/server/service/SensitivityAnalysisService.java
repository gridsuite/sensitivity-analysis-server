/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.sensitivity.SensitivityAnalysisProvider;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityFactorsIdsByGroup;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityResultFilterOptions;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService {

    public static final String INJECTIONS = "injections";

    public static final String CONTINGENCIES = "contingencies";

    private final String defaultProvider;

    private final SensitivityAnalysisResultRepository resultRepository;

    private final UuidGeneratorService uuidGeneratorService;

    private final NotificationService notificationService;

    private final ActionsService actionsService;

    private final FilterService filterService;

    private final ObjectMapper objectMapper;

    @Autowired
    public SensitivityAnalysisService(@Value("${sensitivity-analysis.default-provider}") String defaultProvider,
                                      SensitivityAnalysisResultRepository resultRepository,
                                      UuidGeneratorService uuidGeneratorService,
                                      NotificationService notificationService,
                                      ActionsService actionsService,
                                      FilterService filterService,
                                      ObjectMapper objectMapper) {
        this.defaultProvider = defaultProvider;
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.notificationService = notificationService;
        this.actionsService = actionsService;
        this.filterService = filterService;
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

    public SensitivityRunQueryResult getRunResult(UUID resultUuid, ResultsSelector selector) {
        return resultRepository.getRunResult(resultUuid, selector);
    }

    public SensitivityResultFilterOptions getSensitivityResultOptions(UUID resultUuid, ResultsSelector selector) {
        return resultRepository.getSensitivityResultFilterOptions(resultUuid, selector);
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

    public List<String> getProviders() {
        return SensitivityAnalysisProvider.findAll().stream()
                .map(SensitivityAnalysisProvider::getName)
                .collect(Collectors.toList());
    }

    public Long getFactorsCount(SensitivityFactorsIdsByGroup factorIds, UUID networkUuid, String variantId, Boolean isInjectionsSet) {
        Long containersAttributesCount = 1L;
        if (Boolean.TRUE.equals(isInjectionsSet)) {
            containersAttributesCount *= factorIds.getIds().get(INJECTIONS).size();
            factorIds.getIds().remove(INJECTIONS);
        }
        containersAttributesCount *= getFactorsCount(factorIds, networkUuid, variantId);
        return containersAttributesCount;
    }

    private Long getFactorsCount(SensitivityFactorsIdsByGroup factorIds, UUID networkUuid, String variantId) {
        Map<String, List<UUID>> ids = factorIds.getIds();
        long contAttributesCountTemp = 1L;
        if (ids.containsKey(CONTINGENCIES) && !ids.get(CONTINGENCIES).isEmpty()) {
            int sumContingencyListSizes = getContingenciesCount(ids.get(CONTINGENCIES), networkUuid, variantId);
            sumContingencyListSizes = Math.max(sumContingencyListSizes, 1);
            contAttributesCountTemp *= sumContingencyListSizes;
            ids.remove(CONTINGENCIES);
        }
        ids.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));
        Map<String, Long> map = filterService.getIdentifiablesCount(factorIds, networkUuid, null);
        for (Long count : map.values()) {
            if (count != 0) {
                contAttributesCountTemp *= count;
            }
        }

        return contAttributesCountTemp;
    }

    private Integer getContingenciesCount(List<UUID> ids, UUID networkUuid, String variantId) {
        return ids.stream()
                .mapToInt(uuid -> actionsService.getContingencyList(uuid, networkUuid, variantId).size())
                .sum();
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public byte[] exportSensitivityResultsAsCsv(UUID resultUuid, SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        if (sensitivityAnalysisCsvFileInfos == null ||
                sensitivityAnalysisCsvFileInfos.getSensitivityFunctionType() == null ||
                sensitivityAnalysisCsvFileInfos.getTabSelection() == null ||
                CollectionUtils.isEmpty(sensitivityAnalysisCsvFileInfos.getCsvHeaders())) {
            throw new PowsyblException("Missing information to export sensitivity result as csv : Sensitivity result tab, sensitivity function type and csv file headers must be provided");
        }
        ResultsSelector selector = ResultsSelector.builder()
                .functionType(sensitivityAnalysisCsvFileInfos.getSensitivityFunctionType())
                .tabSelection(sensitivityAnalysisCsvFileInfos.getTabSelection())
                .build();

        SensitivityRunQueryResult result = getRunResult(resultUuid, selector);
        if (result == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("sensitivity_result.csv"));

            CsvWriterSettings settings = new CsvWriterSettings();
            CsvWriter csvWriter = new CsvWriter(zipOutputStream, settings);
            csvWriter.writeHeaders(sensitivityAnalysisCsvFileInfos.getCsvHeaders());
            if (selector.getTabSelection() == ResultTab.N) {
                result.getSensitivities()
                        .forEach(sensitivity -> csvWriter.writeRow(
                                sensitivity.getFuncId(),
                                sensitivity.getVarId(),
                                nullIfNan(sensitivity.getFunctionReference()),
                                nullIfNan(sensitivity.getValue())
                        ));
            } else if (selector.getTabSelection() == ResultTab.N_K) {
                result.getSensitivities()
                        .stream()
                        .map(sensitivity -> (SensitivityWithContingency) sensitivity)
                        .forEach(sensitivityWithContingency -> {
                            csvWriter.writeRow(
                                    sensitivityWithContingency.getFuncId(),
                                    sensitivityWithContingency.getVarId(),
                                    sensitivityWithContingency.getContingencyId(),
                                    nullIfNan(sensitivityWithContingency.getFunctionReference()),
                                    nullIfNan(sensitivityWithContingency.getValue()),
                                    nullIfNan(sensitivityWithContingency.getFunctionReferenceAfter()),
                                    nullIfNan(sensitivityWithContingency.getValueAfter())
                            );
                        });
            }

            csvWriter.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Double nullIfNan(double d) {
        return Double.isNaN(d) ? null : d;
    }
}

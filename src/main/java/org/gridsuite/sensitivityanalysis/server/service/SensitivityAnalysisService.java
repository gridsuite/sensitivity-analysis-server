/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.sensitivity.SensitivityAnalysisProvider;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.gridsuite.sensitivityanalysis.server.SensibilityAnalysisException;
import com.powsybl.ws.commons.computation.service.AbstractComputationService;
import com.powsybl.ws.commons.computation.service.NotificationService;
import com.powsybl.ws.commons.computation.service.UuidGeneratorService;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.sensitivityanalysis.server.SensibilityAnalysisException.Type.FILE_EXPORT_ERROR;
import static org.gridsuite.sensitivityanalysis.server.SensibilityAnalysisException.Type.INVALID_EXPORT_PARAMS;
import static org.gridsuite.sensitivityanalysis.server.SensibilityAnalysisException.Type.RESULT_NOT_FOUND;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService extends AbstractComputationService<SensitivityAnalysisRunContext, SensitivityAnalysisResultService, SensitivityAnalysisStatus> {

    public static final String INJECTIONS = "injections";

    public static final String CONTINGENCIES = "contingencies";

    private final ActionsService actionsService;

    private final FilterService filterService;

    public SensitivityAnalysisService(@Value("${sensitivity-analysis.default-provider}") String defaultProvider,
                                      SensitivityAnalysisResultService resultService,
                                      UuidGeneratorService uuidGeneratorService,
                                      NotificationService notificationService,
                                      ActionsService actionsService,
                                      FilterService filterService,
                                      ObjectMapper objectMapper) {
        super(notificationService, resultService, objectMapper, uuidGeneratorService, defaultProvider);
        this.actionsService = actionsService;
        this.filterService = filterService;
    }

    @Override
    public UUID runAndSaveResult(SensitivityAnalysisRunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), SensitivityAnalysisStatus.RUNNING);
        notificationService.sendRunMessage(new SensitivityAnalysisResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    public SensitivityRunQueryResult getRunResult(UUID resultUuid, ResultsSelector selector) {
        return resultService.getRunResult(resultUuid, selector);
    }

    public SensitivityResultFilterOptions getSensitivityResultOptions(UUID resultUuid, ResultsSelector selector) {
        return resultService.getSensitivityResultFilterOptions(resultUuid, selector);
    }

    @Override
    public List<String> getProviders() {
        return SensitivityAnalysisProvider.findAll().stream()
                .map(SensitivityAnalysisProvider::getName)
                .toList();
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
            int sumContingencyListSizes = actionsService.getContingencyCount(ids.get(CONTINGENCIES), networkUuid, variantId);
            sumContingencyListSizes = Math.max(sumContingencyListSizes, 1);
            contAttributesCountTemp *= sumContingencyListSizes;
            ids.remove(CONTINGENCIES);
        }
        ids.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));
        Map<String, Long> map = filterService.getIdentifiablesCount(factorIds, networkUuid, variantId);
        for (Long count : map.values()) {
            if (count != 0) {
                contAttributesCountTemp *= count;
            }
        }

        return contAttributesCountTemp;
    }

    public byte[] exportSensitivityResultsAsCsv(UUID resultUuid, SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        if (sensitivityAnalysisCsvFileInfos == null ||
                sensitivityAnalysisCsvFileInfos.getSensitivityFunctionType() == null ||
                sensitivityAnalysisCsvFileInfos.getResultTab() == null ||
                CollectionUtils.isEmpty(sensitivityAnalysisCsvFileInfos.getCsvHeaders())) {
            throw new SensibilityAnalysisException(INVALID_EXPORT_PARAMS, "Missing information to export sensitivity result as csv : Sensitivity result tab, sensitivity function type and csv file headers must be provided");
        }
        ResultsSelector selector = ResultsSelector.builder()
                .functionType(sensitivityAnalysisCsvFileInfos.getSensitivityFunctionType())
                .tabSelection(sensitivityAnalysisCsvFileInfos.getResultTab())
                .build();

        SensitivityRunQueryResult result = getRunResult(resultUuid, selector);
        if (result == null) {
            throw new SensibilityAnalysisException(RESULT_NOT_FOUND, "The sensitivity analysis result '" + resultUuid + "' does not exist");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("sensitivity_result.csv"));

            // adding BOM to the beginning of file to help excel in some versions to detect this is UTF-8 encoding bytes
            writeUTF8Bom(zipOutputStream);
            CsvWriterSettings settings = new CsvWriterSettings();
            CsvWriter csvWriter = new CsvWriter(zipOutputStream, StandardCharsets.UTF_8, settings);
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
                        .map(SensitivityWithContingency.class::cast)
                        .forEach(sensitivityWithContingency -> csvWriter.writeRow(
                                sensitivityWithContingency.getFuncId(),
                                sensitivityWithContingency.getVarId(),
                                sensitivityWithContingency.getContingencyId(),
                                nullIfNan(sensitivityWithContingency.getFunctionReference()),
                                nullIfNan(sensitivityWithContingency.getValue()),
                                nullIfNan(sensitivityWithContingency.getFunctionReferenceAfter()),
                                nullIfNan(sensitivityWithContingency.getValueAfter())
                        ));
            }

            csvWriter.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new SensibilityAnalysisException(FILE_EXPORT_ERROR, e.getMessage());
        }
    }

    private static void writeUTF8Bom(OutputStream outputStream) throws IOException {
        outputStream.write(0xef);
        outputStream.write(0xbb);
        outputStream.write(0xbf);
    }

    private static Double nullIfNan(double d) {
        return Double.isNaN(d) ? null : d;
    }
}

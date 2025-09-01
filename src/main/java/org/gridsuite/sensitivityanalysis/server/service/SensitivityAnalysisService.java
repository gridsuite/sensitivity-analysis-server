/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.sensitivity.SensitivityAnalysisProvider;
import com.univocity.parsers.csv.CsvFormat;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractComputationService;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.computation.service.UuidGeneratorService;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.gridsuite.sensitivityanalysis.server.SensibilityAnalysisException;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.sensitivityanalysis.server.SensibilityAnalysisException.Type.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService extends AbstractComputationService<SensitivityAnalysisRunContext, SensitivityAnalysisResultService, SensitivityAnalysisStatus> {

    public static final String INJECTIONS = "injections";

    public static final String CONTINGENCIES = "contingencies";

    public static final char CSV_DELIMITER_FR = ';';
    public static final char CSV_DELIMITER_EN = ',';
    public static final char CSV_QUOTE_ESCAPE = '"';

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

    public SensitivityRunQueryResult getRunResult(UUID resultUuid, UUID networkUuid, String variantId, ResultsSelector selector, List<ResourceFilterDTO> resourceFilters, GlobalFilter globalFilter) {
        List<ResourceFilterDTO> allResourceFilters = new ArrayList<>();
        if (resourceFilters != null) {
            allResourceFilters.addAll(resourceFilters);
        }
        if (globalFilter != null) {
            Optional<ResourceFilterDTO> resourceGlobalFilters = filterService.getResourceFilter(networkUuid, variantId, globalFilter);
            resourceGlobalFilters.ifPresent(allResourceFilters::add);
        }
        return resultService.getRunResult(resultUuid, selector, allResourceFilters);
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

    private static void setFormat(CsvFormat format, String language) {
        format.setLineSeparator(System.lineSeparator());
        format.setDelimiter(language != null && language.equals("fr") ? CSV_DELIMITER_FR : CSV_DELIMITER_EN);
        format.setQuoteEscape(CSV_QUOTE_ESCAPE);
    }

    private static String convertDoubleToLocale(Double value, String language) {
        if (value != null) {
            return NumberFormat.getInstance(language != null && language.equals("fr") ? Locale.FRENCH : Locale.US).format(value);
        }
        return null;
    }

    public byte[] exportSensitivityResultsAsCsv(UUID resultUuid, SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos, UUID networkUuid, String variantId, List<ResourceFilterDTO> resourceFilters, GlobalFilter globalFilter) {
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

        SensitivityRunQueryResult result = getRunResult(resultUuid, networkUuid, variantId, selector, resourceFilters, globalFilter);
        if (result == null) {
            throw new SensibilityAnalysisException(RESULT_NOT_FOUND, "The sensitivity analysis result '" + resultUuid + "' does not exist");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("sensitivity_result.csv"));

            // adding BOM to the beginning of file to help excel in some versions to detect this is UTF-8 encoding bytes
            writeUTF8Bom(zipOutputStream);
            CsvWriterSettings settings = new CsvWriterSettings();
            setFormat(settings.getFormat(), sensitivityAnalysisCsvFileInfos.getLanguage());
            CsvWriter csvWriter = new CsvWriter(zipOutputStream, StandardCharsets.UTF_8, settings);
            csvWriter.writeHeaders(sensitivityAnalysisCsvFileInfos.getCsvHeaders());
            if (selector.getTabSelection() == ResultTab.N) {
                result.getSensitivities()
                        .forEach(sensitivity -> csvWriter.writeRow(
                                sensitivity.getFuncId(),
                                sensitivity.getVarId(),
                                convertDoubleToLocale(nullIfNan(sensitivity.getFunctionReference()), sensitivityAnalysisCsvFileInfos.getLanguage()),
                                convertDoubleToLocale(nullIfNan(sensitivity.getValue()), sensitivityAnalysisCsvFileInfos.getLanguage())
                        ));
            } else if (selector.getTabSelection() == ResultTab.N_K) {
                result.getSensitivities()
                        .stream()
                        .map(SensitivityWithContingency.class::cast)
                        .forEach(sensitivityWithContingency -> csvWriter.writeRow(
                                sensitivityWithContingency.getFuncId(),
                                sensitivityWithContingency.getVarId(),
                                sensitivityWithContingency.getContingencyId(),
                                convertDoubleToLocale(nullIfNan(sensitivityWithContingency.getFunctionReference()), sensitivityAnalysisCsvFileInfos.getLanguage()),
                                convertDoubleToLocale(nullIfNan(sensitivityWithContingency.getValue()), sensitivityAnalysisCsvFileInfos.getLanguage()),
                                convertDoubleToLocale(nullIfNan(sensitivityWithContingency.getFunctionReferenceAfter()), sensitivityAnalysisCsvFileInfos.getLanguage()),
                                convertDoubleToLocale(nullIfNan(sensitivityWithContingency.getValueAfter()), sensitivityAnalysisCsvFileInfos.getLanguage())
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

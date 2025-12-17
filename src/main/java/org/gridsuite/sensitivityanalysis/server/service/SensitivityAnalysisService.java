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
import org.gridsuite.computation.error.ComputationException;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractComputationService;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.computation.service.UuidGeneratorService;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.FactorCount;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.error.SensitivityAnalysisBusinessErrorCode;
import org.gridsuite.sensitivityanalysis.server.error.SensitivityAnalysisException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.computation.error.ComputationBusinessErrorCode.INVALID_EXPORT_PARAMS;
import static org.gridsuite.computation.error.ComputationBusinessErrorCode.RESULT_NOT_FOUND;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService extends AbstractComputationService<SensitivityAnalysisRunContext, SensitivityAnalysisResultService, SensitivityAnalysisStatus> {

    public static final char CSV_DELIMITER_FR = ';';
    public static final char CSV_DELIMITER_EN = ',';
    public static final char CSV_QUOTE_ESCAPE = '"';
    public static final int MAX_RESULTS_THRESHOLD = 500_000;
    public static final int MAX_VARIABLES_THRESHOLD = 5_000;

    private final SensitivityAnalysisFactorCountService sensitivityAnalysisFactorCountService;

    private final FilterService filterService;

    public SensitivityAnalysisService(@Value("${sensitivity-analysis.default-provider}") String defaultProvider,
                                      SensitivityAnalysisResultService resultService,
                                      UuidGeneratorService uuidGeneratorService,
                                      NotificationService notificationService,
                                      SensitivityAnalysisFactorCountService sensitivityAnalysisFactorCountService,
                                      FilterService filterService,
                                      ObjectMapper objectMapper) {
        super(notificationService, resultService, objectMapper, uuidGeneratorService, defaultProvider);
        this.sensitivityAnalysisFactorCountService = sensitivityAnalysisFactorCountService;
        this.filterService = filterService;
    }

    @Override
    public UUID runAndSaveResult(SensitivityAnalysisRunContext runContext) {
        Objects.requireNonNull(runContext);
        var resultUuid = uuidGeneratorService.generate();

        FactorCount factorCount = sensitivityAnalysisFactorCountService.getFactorCount(
                runContext.getNetworkUuid(),
                runContext.getVariantId(),
                runContext.getParameters().getSensitivityInjectionsSets(),
                runContext.getParameters().getSensitivityInjections(),
                runContext.getParameters().getSensitivityHVDCs(),
                runContext.getParameters().getSensitivityPSTs(),
                runContext.getParameters().getSensitivityNodes());
        if (factorCount.resultCount() > MAX_RESULTS_THRESHOLD || factorCount.variableCount() > MAX_VARIABLES_THRESHOLD) {
            throw new SensitivityAnalysisException(SensitivityAnalysisBusinessErrorCode.TOO_MANY_FACTORS, "Too many factors to run sensitivity analysis", Map.of("resultCount", factorCount.resultCount(), "resultCountLimit", MAX_RESULTS_THRESHOLD, "variableCount", factorCount.variableCount(), "variableCountLimit", MAX_VARIABLES_THRESHOLD));
        }

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

    private static void setFormat(CsvFormat format, String language) {
        format.setLineSeparator(System.lineSeparator());
        format.setDelimiter(language != null && language.equals("fr") ? CSV_DELIMITER_FR : CSV_DELIMITER_EN);
        format.setQuoteEscape(CSV_QUOTE_ESCAPE);
    }

    private static String convertDoubleToLocale(Double value, String language) {
        if (value != null) {
            NumberFormat nf = NumberFormat.getInstance(language != null && language.equals("fr") ? Locale.FRENCH : Locale.US);
            nf.setGroupingUsed(false);
            return nf.format(value);
        }
        return null;
    }

    public byte[] exportSensitivityResultsAsCsv(UUID resultUuid, SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos, UUID networkUuid, String variantId, ResultsSelector selector, List<ResourceFilterDTO> resourceFilters, GlobalFilter globalFilter) {
        if (sensitivityAnalysisCsvFileInfos == null ||
                sensitivityAnalysisCsvFileInfos.getSensitivityFunctionType() == null ||
                sensitivityAnalysisCsvFileInfos.getResultTab() == null ||
                CollectionUtils.isEmpty(sensitivityAnalysisCsvFileInfos.getCsvHeaders())) {
            throw new ComputationException(INVALID_EXPORT_PARAMS, "Missing information to export sensitivity result as csv : Sensitivity result tab, sensitivity function type and csv file headers must be provided");
        }
        SensitivityRunQueryResult result = getRunResult(resultUuid, networkUuid, variantId, selector, resourceFilters, globalFilter);
        if (result == null) {
            throw new ComputationException(RESULT_NOT_FOUND, "The sensitivity analysis result '" + resultUuid + "' does not exist");
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
            throw new UncheckedIOException("Error occured during data csv export", e);
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

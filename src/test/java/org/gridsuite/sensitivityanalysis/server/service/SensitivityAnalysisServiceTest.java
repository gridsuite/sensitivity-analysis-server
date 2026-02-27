/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityFunctionType;
import org.gridsuite.computation.error.ComputationException;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.FactorCount;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.error.SensitivityAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.gridsuite.sensitivityanalysis.server.util.TestUtils.DEFAULT_PROVIDER;
import static org.gridsuite.sensitivityanalysis.server.util.TestUtils.unzip;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */
@SpringBootTest
class SensitivityAnalysisServiceTest {

    @Autowired
    private SensitivityAnalysisService analysisService;

    @MockitoBean
    private SensitivityAnalysisParametersService parametersService;

    @MockitoBean
    private SensitivityAnalysisFactorCountService sensitivityAnalysisFactorCountService;

    @MockitoBean
    private SensitivityAnalysisResultService sensitivityAnalysisResultService;

    @MockitoBean(name = "notificationService")
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        var parametersInfos = Mockito.mock(SensitivityAnalysisParametersInfos.class);
        given(parametersInfos.getProvider()).willReturn("open-loadflow");
        given(parametersService.getParameters(any(UUID.class), any(String.class))).willReturn(Optional.of(parametersInfos));

        var inputData = Mockito.mock(SensitivityAnalysisInputData.class);
        given(parametersService.buildInputData(any(), any())).willReturn(inputData);

        FactorCount mockedFactorCount = new FactorCount(10, 1000);
        given(sensitivityAnalysisFactorCountService.getFactorCount(any(), any(), any(), any(), any(), any(), any())).willReturn(mockedFactorCount);
    }

    @Test
    void testRunAndSave() {
        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersService.getParameters(UUID.randomUUID(), "userId")
                .orElse(parametersService.getDefauSensitivityAnalysisParametersInfos());

        SensitivityAnalysisInputData inputData = parametersService.buildInputData(sensitivityAnalysisParametersInfos, UUID.randomUUID());

        analysisService.runAndSaveResult(
                new SensitivityAnalysisRunContext(
                        UUID.randomUUID(),
                        "variantId",
                        "me",
                        Mockito.mock(ReportInfos.class),
                        "userId",
                        DEFAULT_PROVIDER,
                        inputData
                ));

        verify(sensitivityAnalysisResultService, times(1)).insertStatus(any(), eq(SensitivityAnalysisStatus.RUNNING));
        verify(notificationService, times(1)).sendRunMessage(any());
    }

    @Test
    void testRunAndSaveThrowsIfTooManyResults() {
        given(sensitivityAnalysisFactorCountService.getFactorCount(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(new FactorCount(10, SensitivityAnalysisService.MAX_RESULTS_THRESHOLD + 1));
        SensitivityAnalysisRunContext sensitivityAnalysisRunContext = new SensitivityAnalysisRunContext(
                UUID.randomUUID(),
                "variantId",
                "me",
                Mockito.mock(ReportInfos.class),
                "userId",
                DEFAULT_PROVIDER,
                Mockito.mock(SensitivityAnalysisInputData.class)
        );

        SensitivityAnalysisException exception = assertThrows(SensitivityAnalysisException.class, () ->
                analysisService.runAndSaveResult(sensitivityAnalysisRunContext)
        );

        assertThat(exception.getMessage()).contains("Too many factors to run sensitivity analysis");

        verify(sensitivityAnalysisResultService, times(0)).insertStatus(any(), eq(SensitivityAnalysisStatus.RUNNING));
        verify(notificationService, times(0)).sendRunMessage(any());
    }

    @Test
    void testRunAndSaveThrowsIfTooManyVariables() {
        given(sensitivityAnalysisFactorCountService.getFactorCount(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(new FactorCount(SensitivityAnalysisService.MAX_VARIABLES_THRESHOLD + 1, 100));
        SensitivityAnalysisRunContext sensitivityAnalysisRunContext = new SensitivityAnalysisRunContext(
                UUID.randomUUID(),
                "variantId",
                "me",
                Mockito.mock(ReportInfos.class),
                "userId",
                DEFAULT_PROVIDER,
                Mockito.mock(SensitivityAnalysisInputData.class)
        );

        SensitivityAnalysisException exception = assertThrows(SensitivityAnalysisException.class, () ->
                analysisService.runAndSaveResult(sensitivityAnalysisRunContext)
        );

        assertThat(exception.getMessage()).contains("Too many factors to run sensitivity analysis");

        verify(sensitivityAnalysisResultService, times(0)).insertStatus(any(), eq(SensitivityAnalysisStatus.RUNNING));
        verify(notificationService, times(0)).sendRunMessage(any());
    }

    @Test
    void testExportCsvInN() throws Exception {
        given(sensitivityAnalysisResultService.getRunResult(any(), any(), any())).willReturn(
            SensitivityRunQueryResult.builder()
                .resultTab(ResultTab.N)
                .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
                .sensitivities(List.of(
                    SensitivityOfTo.builder()
                        .varId("varId1")
                        .funcId("funcId1")
                        .value(0.1)
                        .functionReference(100)
                        .build()
                ))
                .requestedChunkSize(10)
                .chunkOffset(0)
                .totalSensitivitiesCount(1L)
                .filteredSensitivitiesCount(1L)
                .build()
        );

        SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos = SensitivityAnalysisCsvFileInfos.builder()
            .sensitivityFunctionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .resultTab(ResultTab.N)
            .csvHeaders(List.of("functionId", "variableId", "functionReference", "value"))
            .language("en")
            .build();
        ResultsSelector selector = ResultsSelector.builder()
                .tabSelection(ResultTab.N)
                .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
                .build();

        byte[] zip = analysisService.exportSensitivityResultsAsCsv(UUID.randomUUID(), sensitivityAnalysisCsvFileInfos, null, null, selector, null, null);
        byte[] csv = unzip(zip);
        String csvStr = new String(csv, StandardCharsets.UTF_8);
        List<String> actualLines = Arrays.asList(csvStr.split("\n"));

        // Including "\uFEFF" indicates the UTF-8 BOM at the start.
        List<String> expectedLines = new ArrayList<>(List.of("\uFEFFfunctionId,variableId,functionReference,value",
            "funcId1,varId1,100,0.1"));

        actualLines.sort(String::compareTo);
        expectedLines.sort(String::compareTo);
        assertThat(actualLines).usingRecursiveComparison().isEqualTo(expectedLines);
    }

    @Test
    void testExportCsvInNK() throws Exception {
        given(sensitivityAnalysisResultService.getRunResult(any(), any(), any())).willReturn(
            getDefaultQueryBuilder()
                .resultTab(ResultTab.N_K)
                .sensitivities(List.of(
                    SensitivityWithContingency.builder()
                        .varId("varId1")
                        .funcId("funcId1")
                        .value(0.1)
                        .functionReference(100)
                        .valueAfter(0.2)
                        .functionReferenceAfter(200)
                        .contingencyId("contingencyId1")
                        .build()
                ))
                .build()
        );

        SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos = SensitivityAnalysisCsvFileInfos.builder()
            .sensitivityFunctionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .resultTab(ResultTab.N_K)
            .csvHeaders(List.of("functionId", "variableId", "contingencyId", "functionReference", "value", "functionReferenceAfter", "valueAfter"))
            .language("en")
            .build();
        ResultsSelector selector = ResultsSelector.builder()
                .tabSelection(ResultTab.N_K)
                .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
                .build();

        byte[] zip = analysisService.exportSensitivityResultsAsCsv(UUID.randomUUID(), sensitivityAnalysisCsvFileInfos, null, null, selector, null, null);
        byte[] csv = unzip(zip);
        String csvStr = new String(csv, StandardCharsets.UTF_8);
        List<String> actualLines = Arrays.asList(csvStr.split("\n"));

        // Including "\uFEFF" indicates the UTF-8 BOM at the start.
        List<String> expectedLines = new ArrayList<>(List.of("\uFEFFfunctionId,variableId,contingencyId,functionReference,value,functionReferenceAfter,valueAfter",
            "funcId1,varId1,contingencyId1,100,0.1,200,0.2"));

        actualLines.sort(String::compareTo);
        expectedLines.sort(String::compareTo);
        assertThat(actualLines).usingRecursiveComparison().isEqualTo(expectedLines);
    }

    @Test
    void testExportCsvThrowsIfResultUuidIsNotPresent() {
        SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos = SensitivityAnalysisCsvFileInfos.builder()
            .sensitivityFunctionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .resultTab(ResultTab.N)
            .csvHeaders(List.of("functionId", "variableId", "functionReference", "value"))
            .language("en")
            .build();
        final UUID resultUuid = UUID.randomUUID();
        assertThrows(ComputationException.class, () -> analysisService.exportSensitivityResultsAsCsv(resultUuid, sensitivityAnalysisCsvFileInfos, null, null, null, null, null));
    }

    private static SensitivityRunQueryResult.SensitivityRunQueryResultBuilder getDefaultQueryBuilder() {
        return SensitivityRunQueryResult.builder()
            .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
            .requestedChunkSize(10)
            .chunkOffset(0)
            .totalSensitivitiesCount(1L)
            .filteredSensitivitiesCount(1L);
    }
}

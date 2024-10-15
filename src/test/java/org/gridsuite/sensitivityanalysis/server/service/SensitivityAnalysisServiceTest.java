/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.ws.commons.computation.dto.ReportInfos;
import com.powsybl.ws.commons.computation.service.NotificationService;
import org.gridsuite.sensitivityanalysis.server.SensibilityAnalysisException;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

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

    @MockBean
    private SensitivityAnalysisParametersService parametersService;

    @MockBean
    private SensitivityAnalysisResultService sensitivityAnalysisResultService;

    @MockBean(name = "notificationService")
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        var parametersInfos = Mockito.mock(SensitivityAnalysisParametersInfos.class);
        given(parametersInfos.getProvider()).willReturn("open-loadflow");
        given(parametersService.getParameters(any())).willReturn(Optional.of(parametersInfos));

        var inputData = Mockito.mock(SensitivityAnalysisInputData.class);
        given(parametersService.buildInputData(any(), any())).willReturn(inputData);
    }

    @Test
    void testRunAndSave() {
        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersService.getParameters(UUID.randomUUID())
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
    void testExportCsvInN() throws Exception {
        given(sensitivityAnalysisResultService.getRunResult(any(), any())).willReturn(
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
            .build();

        byte[] zip = analysisService.exportSensitivityResultsAsCsv(UUID.randomUUID(), sensitivityAnalysisCsvFileInfos);
        byte[] csv = unzip(zip);
        String csvStr = new String(csv, StandardCharsets.UTF_8);
        List<String> actualLines = Arrays.asList(csvStr.split("\n"));

        // Including "\uFEFF" indicates the UTF-8 BOM at the start.
        List<String> expectedLines = new ArrayList<>(List.of("\uFEFFfunctionId,variableId,functionReference,value",
            "funcId1,varId1,100.0,0.1"));

        actualLines.sort(String::compareTo);
        expectedLines.sort(String::compareTo);
        assertThat(actualLines).usingRecursiveComparison().isEqualTo(expectedLines);
    }

    @Test
    void testExportCsvInNK() throws Exception {
        given(sensitivityAnalysisResultService.getRunResult(any(), any())).willReturn(
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
            .build();

        byte[] zip = analysisService.exportSensitivityResultsAsCsv(UUID.randomUUID(), sensitivityAnalysisCsvFileInfos);
        byte[] csv = unzip(zip);
        String csvStr = new String(csv, StandardCharsets.UTF_8);
        List<String> actualLines = Arrays.asList(csvStr.split("\n"));

        // Including "\uFEFF" indicates the UTF-8 BOM at the start.
        List<String> expectedLines = new ArrayList<>(List.of("\uFEFFfunctionId,variableId,contingencyId,functionReference,value,functionReferenceAfter,valueAfter",
            "funcId1,varId1,contingencyId1,100.0,0.1,200.0,0.2"));

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
            .build();

        assertThrows(SensibilityAnalysisException.class, () -> analysisService.exportSensitivityResultsAsCsv(UUID.randomUUID(), sensitivityAnalysisCsvFileInfos));
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

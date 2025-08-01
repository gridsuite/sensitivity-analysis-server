/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.ws.commons.computation.ComputationException;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ReportInfos;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.utils.FilterUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStatus;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisParametersService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisRunContext;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisWorkerService;
import org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy.NonEvacuatedEnergyRunContext;
import org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy.NonEvacuatedEnergyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.powsybl.ws.commons.computation.service.NotificationService.HEADER_USER_ID;
import static org.springframework.http.MediaType.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + SensitivityAnalysisApi.API_VERSION)
@Tag(name = "Sensitivity analysis server")
public class SensitivityAnalysisController {
    private final SensitivityAnalysisService service;

    private final SensitivityAnalysisWorkerService workerService;
    private final SensitivityAnalysisParametersService sensitivityAnalysisParametersService;

    private final NonEvacuatedEnergyService nonEvacuatedEnergyService;

    private final ObjectMapper objectMapper;

    public SensitivityAnalysisController(SensitivityAnalysisService service, SensitivityAnalysisWorkerService workerService,
                                         NonEvacuatedEnergyService nonEvacuatedEnergyService,
                                         SensitivityAnalysisParametersService sensitivityAnalysisParametersService,
                                         ObjectMapper objectMapper) {
        this.service = service;
        this.workerService = workerService;
        this.nonEvacuatedEnergyService = nonEvacuatedEnergyService;
        this.sensitivityAnalysisParametersService = sensitivityAnalysisParametersService;
        this.objectMapper = objectMapper;
    }

    private ResultsSelector getSelector(String selectorJson) throws JsonProcessingException {
        return selectorJson == null ?
                ResultsSelector.builder()
                        .functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)
                        .tabSelection(ResultTab.N_K)
                        .build() :
                objectMapper.readValue(selectorJson, ResultsSelector.class);
    }

    @PostMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a sensitivity analysis on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200",
                                        description = "The sensitivity analysis has been performed",
                                        content = {@Content(mediaType = APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = SensitivityAnalysisResult.class))})})
    public ResponseEntity<SensitivityAnalysisResult> run(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                         @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                                         @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                                         @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                                         @Parameter(description = "The type name for the report") @RequestParam(name = "reportType", required = false, defaultValue = "SensitivityAnalysis") String reportType,
                                                         @Parameter(description = "parametersUuid") @RequestParam(name = "parametersUuid", required = false) UUID parametersUuid,
                                                         @Parameter(description = "loadFlow parameters uuid") @RequestParam(name = "loadFlowParametersUuid") UUID loadFlowParametersUuid,
                                                         @RequestHeader(HEADER_USER_ID) String userId) {
        SensitivityAnalysisResult result = workerService.run(networkUuid, variantId, new ReportInfos(reportUuid, reporterId, reportType), userId, parametersUuid, loadFlowParametersUuid);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PostMapping(value = "/networks/{networkUuid}/run-and-save", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a sensitivity analysis on a network and save results in the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200",
                                        description = "The sensitivity analysis has been performed and results have been save to database",
                                        content = {@Content(mediaType = APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = SensitivityAnalysisResult.class))})})
    public ResponseEntity<UUID> runAndSave(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                           @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                           @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                           @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                           @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                           @Parameter(description = "The type name for the report") @RequestParam(name = "reportType", required = false, defaultValue = "SensitivityAnalysis") String reportType,
                                           @Parameter(description = "parametersUuid") @RequestParam(name = "parametersUuid", required = false) UUID parametersUuid,
                                           @Parameter(description = "loadFlow parameters uuid") @RequestParam(name = "loadFlowParametersUuid") UUID loadFlowParametersUuid,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        SensitivityAnalysisRunContext runContext = sensitivityAnalysisParametersService.createRunContext(
                networkUuid,
                variantId,
                receiver,
                new ReportInfos(reportUuid, reporterId, reportType),
                userId,
                parametersUuid,
                loadFlowParametersUuid
        );
        UUID resultUuid = service.runAndSaveResult(runContext);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    @GetMapping(value = "/networks/{networkUuid}/factors-count", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get factors count")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis factors count"),
        @ApiResponse(responseCode = "404", description = "Filters or contingencies has not been found")})
    public ResponseEntity<Long> getFactorsCount(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                                @Parameter(description = "Is Injections Set") @RequestParam(name = "isInjectionsSet", required = false) Boolean isInjectionsSet,
                                                SensitivityFactorsIdsByGroup factorsIds) {
        return ResponseEntity.ok().body(service.getFactorsCount(factorsIds, networkUuid, variantId, isInjectionsSet));
    }

    @GetMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a sensitivity analysis result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result"),
        @ApiResponse(responseCode = "404", description = "Sensitivity analysis result has not been found"),
        @ApiResponse(responseCode = "400", description = "Invalid filter format")})
    public ResponseEntity<SensitivityRunQueryResult> getResult(
            @Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
            @RequestParam(name = "selector", required = false) String selectorJson,
            @Parameter(description = "JSON array of filters") @RequestParam(name = "filters", required = false) String filters,
            @Parameter(description = "Global Filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
            @Parameter(description = "network Uuid") @RequestParam(name = "networkUuid", required = false) UUID networkUuid,
            @Parameter(description = "variant Id") @RequestParam(name = "variantId", required = false) String variantId
    ) {
        try {
            String decodedStringFilters = filters != null ? URLDecoder.decode(filters, StandardCharsets.UTF_8) : null;
            String decodedStringGlobalFilters = globalFilters != null ? URLDecoder.decode(globalFilters, StandardCharsets.UTF_8) : null;
            List<ResourceFilterDTO> resourceFilters = FilterUtils.fromStringFiltersToDTO(decodedStringFilters, objectMapper);
            GlobalFilter globalFilter = FilterUtils.fromStringGlobalFiltersToDTO(decodedStringGlobalFilters, objectMapper);
            ResultsSelector selector = getSelector(selectorJson);
            SensitivityRunQueryResult result = service.getRunResult(resultUuid, networkUuid, variantId, selector, resourceFilters, globalFilter);
            return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result) : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (ComputationException e) {
            // Handle JSON processing errors with bad request status
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            // Handle all other exceptions with internal server error status
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/results/{resultUuid}/filter-options", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all filter options of sensitivity analysis results")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result filter options"),
        @ApiResponse(responseCode = "404", description = "Sensitivity analysis result has not been found")})
    public ResponseEntity<SensitivityResultFilterOptions> getResultFilerOptions(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                                                @RequestParam(name = "selector", required = false) String selectorJson) {
        try {
            ResultsSelector selector = getSelector(selectorJson);
            SensitivityResultFilterOptions result = service.getSensitivityResultOptions(resultUuid, selector);
            return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete sensitivity analysis results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All sensitivity analysis results have been deleted")})
    public ResponseEntity<Void> deleteResults(@Parameter(description = "Results UUID") @RequestParam(value = "resultsUuids", required = false) List<UUID> resultsUuids) {
        service.deleteResults(resultsUuids);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the sensitivity analysis status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis status")})
    public ResponseEntity<String> getStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        SensitivityAnalysisStatus result = service.getStatus(resultUuid);
        return ResponseEntity.ok().body(result == null ? null : result.name());
    }

    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the sensitivity analysis status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis status has been invalidated")})
    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        service.setStatus(resultUuids, SensitivityAnalysisStatus.NOT_DONE);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a sensitivity analysis computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                     @RequestHeader(HEADER_USER_ID) String userId) {
        service.stop(resultUuid, receiver, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/providers", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all sensitivity analysis providers")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Sensitivity analysis providers have been found")})
    public ResponseEntity<List<String>> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(service.getProviders());
    }

    @GetMapping(value = "/default-provider", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Get sensitivity analysis default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The sensitivity analysis default provider has been found"))
    public ResponseEntity<String> getDefaultProvider() {
        return ResponseEntity.ok().body(service.getDefaultProvider());
    }

    @PostMapping(value = "/results/{resultUuid}/csv")
    @Operation(summary = "export sensitivity results as csv file")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Sensitivity results successfully exported as csv file"))
    public ResponseEntity<byte[]> exportSensitivityResultsAsCsv(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                                @RequestBody SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(APPLICATION_OCTET_STREAM);
        httpHeaders.setContentDispositionFormData("attachment", "sensitivity_results.zip");
        byte[] csv = service.exportSensitivityResultsAsCsv(resultUuid, sensitivityAnalysisCsvFileInfos);
        return ResponseEntity.ok()
                .headers(httpHeaders)
                .body(csv);
    }

    @PostMapping(value = "/networks/{networkUuid}/non-evacuated-energy/run-and-save", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a non evacuated energy sensitivity analysis on a network and save results in the database")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The non evacuated energy sensitivity analysis default provider has been found"))
    public ResponseEntity<UUID> runNonEvacuatedEnergy(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                           @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                           @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                           @Parameter(description = "Provider") @RequestParam(name = "provider", required = false) String provider,
                                           @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                           @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                           @Parameter(description = "The type name for the report") @RequestParam(name = "reportType", required = false, defaultValue = "NonEvacuatedEnergy") String reportType,
                                           @Parameter(description = "loadFlow parameters uuid") @RequestParam(name = "loadFlowParametersUuid") UUID loadFlowParametersUuid,
                                           @RequestBody NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData,
                                           @RequestHeader(HEADER_USER_ID) String userId) {

        NonEvacuatedEnergyRunContext runContext = sensitivityAnalysisParametersService.createNonEvacuatedEnergyRunContext(
                networkUuid,
                variantId,
                receiver,
                new ReportInfos(reportUuid, reporterId, reportType),
                userId,
                provider,
                loadFlowParametersUuid,
                nonEvacuatedEnergyInputData
        );

        UUID resultUuid = nonEvacuatedEnergyService.runAndSaveResult(runContext);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    @GetMapping(value = "/non-evacuated-energy/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a non evacuated energy result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The non evacuated energy result"),
        @ApiResponse(responseCode = "404", description = "Non evacuated energy result has not been found")})
    public ResponseEntity<String> getResult(@Parameter(description = "Result UUID")
                                                @PathVariable("resultUuid") UUID resultUuid) {
        String result = nonEvacuatedEnergyService.getRunResult(resultUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
            : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/non-evacuated-energy/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete non evacuated energy results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All non evacuated energy results have been deleted")})
    public ResponseEntity<Void> deleteNonEvacuatedEnergyResults(@Parameter(description = "Results UUID") @RequestParam(value = "resultsUuids", required = false) List<UUID> resultsUuids) {
        nonEvacuatedEnergyService.deleteResults(resultsUuids);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/non-evacuated-energy/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the non evacuated energy status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The non evacuated energy status status")})
    public ResponseEntity<String> getNonEvacuatedEnergyStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        NonEvacuatedEnergyStatus result = nonEvacuatedEnergyService.getStatus(resultUuid);
        return ResponseEntity.ok().body(result == null ? null : result.name());
    }

    @PutMapping(value = "/non-evacuated-energy/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the non evacuated energy status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The non evacuated energy status has been invalidated")})
    public ResponseEntity<Void> invalidateNonEvacuatedEnergyStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        nonEvacuatedEnergyService.setStatus(resultUuids, NonEvacuatedEnergyStatus.NOT_DONE);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/non-evacuated-energy/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a non evacuated energy computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The non evacuated energy has been stopped")})
    public ResponseEntity<Void> stopNonEvacuatedEnergy(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                       @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                                       @RequestHeader(HEADER_USER_ID) String userId) {
        nonEvacuatedEnergyService.stop(resultUuid, receiver, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/non-evacuated-energy-default-provider", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Get sensitivity analysis non evacuated energy default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The sensitivity analysis non evacuated energy default provider has been found"))
    public ResponseEntity<String> getNonEvacuatedEnergyDefaultProvider() {
        return ResponseEntity.ok().body(nonEvacuatedEnergyService.getDefaultProvider());
    }
}

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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisRunContext;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisWorkerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + SensitivityAnalysisApi.API_VERSION)
@Tag(name = "Sensitivity analysis server")
public class SensitivityAnalysisController {
    private final SensitivityAnalysisService service;

    private final SensitivityAnalysisWorkerService workerService;

    public SensitivityAnalysisController(SensitivityAnalysisService service, SensitivityAnalysisWorkerService workerService) {
        this.service = service;
        this.workerService = workerService;
    }

    private static List<UUID> getNonNullOtherNetworkUuids(List<UUID> otherNetworkUuids) {
        return otherNetworkUuids != null ? otherNetworkUuids : Collections.emptyList();
    }

    @PostMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a sensitivity analysis on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200",
                                        description = "The sensitivity analysis has been performed",
                                        content = {@Content(mediaType = APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = SensitivityAnalysisResult.class))})})
    public ResponseEntity<SensitivityAnalysisResult> run(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                         @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                                         @Parameter(description = "Other networks UUID (to merge with main one))") @RequestParam(name = "networkUuid", required = false) List<UUID> otherNetworkUuids,
                                                         @Parameter(description = "Provider") @RequestParam(name = "provider", required = false) String provider,
                                                         @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                                         @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                                         @RequestBody SensitivityAnalysisInputData sensitivityAnalysisInputData) {
        List<UUID> nonNullOtherNetworkUuids = getNonNullOtherNetworkUuids(otherNetworkUuids);
        SensitivityAnalysisResult result = workerService.run(new SensitivityAnalysisRunContext(networkUuid, variantId, nonNullOtherNetworkUuids, sensitivityAnalysisInputData, null, provider, reportUuid, reporterId));
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
                                           @Parameter(description = "Other networks UUID (to merge with main one))") @RequestParam(name = "networkUuid", required = false) List<UUID> otherNetworkUuids,
                                           @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                           @Parameter(description = "Provider") @RequestParam(name = "provider", required = false) String provider,
                                           @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportUuid,
                                           @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reporterId,
                                           @RequestBody SensitivityAnalysisInputData sensitivityAnalysisInputData) {
        List<UUID> nonNullOtherNetworkUuids = getNonNullOtherNetworkUuids(otherNetworkUuids);
        UUID resultUuid = service.runAndSaveResult(new SensitivityAnalysisRunContext(networkUuid, variantId, nonNullOtherNetworkUuids, sensitivityAnalysisInputData, receiver, provider, reportUuid, reporterId));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    @GetMapping(value = "/results/{resultUuid}/tabbed", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a sensitivity analysis result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result"),
        @ApiResponse(responseCode = "404", description = "Sensitivity analysis result has not been found")})
    public ResponseEntity<SensitivityRunQueryResult> getResultAfter(@Parameter(description = "Result UUID")
        @PathVariable("resultUuid") UUID resultUuid,
        @RequestParam(name = "selector", required = false) String selectorJson) {

        ObjectMapper mapper = new ObjectMapper();
        ResultsSelector selector;
        if (selectorJson == null) {
            selector = ResultsSelector.builder().functionType(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1).isJustBefore(false).build();
        } else {
            try {
                selector = mapper.readValue(selectorJson, ResultsSelector.class);
            } catch (JsonProcessingException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        SensitivityRunQueryResult result = service.getRunResult(resultUuid, selector);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
            : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a sensitivity analysis result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis result has been deleted")})
    public ResponseEntity<Void> deleteResult(
        @Parameter(description = "Result UUID")
        @PathVariable("resultUuid") UUID resultUuid) {
        service.deleteResult(resultUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete all sensitivity analysis results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All sensitivity analysis results have been deleted")})
    public ResponseEntity<Void> deleteResults() {
        service.deleteResults();
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the sensitivity analysis status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis status")})
    public ResponseEntity<String> getStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        String result = service.getStatus(resultUuid);
        return ResponseEntity.ok().body(result);
    }

    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the sensitivity analysis status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis status has been invalidated")})
    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        service.setStatus(resultUuids, SensitivityAnalysisStatus.NOT_DONE.name());
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a sensitivity analysis computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The sensitivity analysis has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver) {
        service.stop(resultUuid, receiver);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results-threshold-default-value")
    @Operation(summary = "get results threshold default value")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "the results threshold default value has been found"))
    public ResponseEntity<Double> getDefaultResultsThreshold() {
        return ResponseEntity.ok().body(service.getDefaultResultThresholdValue());
    }

}

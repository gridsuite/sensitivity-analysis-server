/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityAnalysisRunnerSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalysisWorkerService.class);

    private final NetworkStoreService networkStoreService;

    private final ReportService reportService;

    private final SensitivityAnalysisResultRepository resultRepository;

    private final ObjectMapper objectMapper;

    private final Map<UUID, CompletableFuture<SensitivityAnalysisResult>> futures = new ConcurrentHashMap<>();

    private final Map<UUID, SensitivityAnalysisCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private final Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancelSensitivityAnalysis = new ReentrantLock();

    private final NotificationService notificationService;

    private final SensitivityAnalysisExecutionService sensitivityAnalysisExecutionService;

    private final SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService;

    private Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier;

    public SensitivityAnalysisWorkerService(NetworkStoreService networkStoreService, ReportService reportService, NotificationService notificationService,
                                            SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService,
                                            SensitivityAnalysisExecutionService sensitivityAnalysisExecutionService,
                                            SensitivityAnalysisResultRepository resultRepository, ObjectMapper objectMapper,
                                            SensitivityAnalysisRunnerSupplier sensitivityAnalysisRunnerSupplier) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.reportService = Objects.requireNonNull(reportService);
        this.notificationService = notificationService;
        this.sensitivityAnalysisExecutionService = Objects.requireNonNull(sensitivityAnalysisExecutionService);
        this.sensitivityAnalysisInputBuilderService = sensitivityAnalysisInputBuilderService;
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        sensitivityAnalysisFactorySupplier = sensitivityAnalysisRunnerSupplier::getRunner;
    }

    public void setSensitivityAnalysisFactorySupplier(Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier) {
        this.sensitivityAnalysisFactorySupplier = Objects.requireNonNull(sensitivityAnalysisFactorySupplier);
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network;
        try {
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    public SensitivityAnalysisResult run(SensitivityAnalysisRunContext context) {
        try {
            return run(context, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOGGER.error(FAIL_MESSAGE, e);
            return null;
        }
    }

    private SensitivityAnalysisResult run(SensitivityAnalysisRunContext context, UUID resultUuid) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(context);

        LOGGER.info("Run sensitivity analysis");

        SensitivityAnalysis.Runner sensitivityAnalysisRunner = sensitivityAnalysisFactorySupplier.apply(context.getProvider());

        Reporter rootReporter = Reporter.NO_OP;
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportUuid() != null) {
            final String reportType = context.getReportType();
            String rootReporterId = context.getReporterId() == null ? reportType : context.getReporterId() + "@" + reportType;
            rootReporter = new ReporterModel(rootReporterId, rootReporterId);
            reporter = rootReporter.createSubReporter(reportType, reportType + " (${providerToUse})", "providerToUse", sensitivityAnalysisRunner.getName());
            // Delete any previous sensi computation logs
            reportService.deleteReport(context.getReportUuid(), reportType);
        }

        CompletableFuture<SensitivityAnalysisResult> future = runSensitivityAnalysisAsync(context, sensitivityAnalysisRunner, reporter, resultUuid);

        SensitivityAnalysisResult result = future == null ? null : future.get();
        if (context.getReportUuid() != null) {
            reportService.sendReport(context.getReportUuid(), rootReporter);
        }
        return result;
    }

    private static SensitivityAnalysisParameters buildParameters(SensitivityAnalysisRunContext context) {
        SensitivityAnalysisParameters params = context.getSensitivityAnalysisInputData().getParameters() == null ?
            new SensitivityAnalysisParameters() : context.getSensitivityAnalysisInputData().getParameters();
        if (context.getSensitivityAnalysisInputData().getLoadFlowSpecificParameters() == null
                || context.getSensitivityAnalysisInputData().getLoadFlowSpecificParameters().isEmpty()) {
            return params; // no specific LF params
        }
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
                .filter(p -> p.getName().equals(context.getProvider()))
                .findFirst().orElseThrow(() -> new PowsyblException("Sensitivity analysis provider not found " + context.getProvider()));
        Extension<LoadFlowParameters> extension = lfProvider.loadSpecificParameters(context.getSensitivityAnalysisInputData().getLoadFlowSpecificParameters())
                .orElseThrow(() -> new PowsyblException("Cannot add specific loadflow parameters with sensitivity analysis provider " + context.getProvider()));
        params.getLoadFlowParameters().addExtension((Class) extension.getClass(), extension);
        return params;
    }

    private CompletableFuture<SensitivityAnalysisResult> runSensitivityAnalysisAsync(SensitivityAnalysisRunContext context,
                                                                                     SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                                                                                     Reporter reporter,
                                                                                     UUID resultUuid) {
        lockRunAndCancelSensitivityAnalysis.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }
            SensitivityAnalysisParameters sensitivityAnalysisParameters = buildParameters(context);
            Network network = getNetwork(context.getNetworkUuid(), context.getVariantId());
            sensitivityAnalysisInputBuilderService.build(context, network, reporter);

            CompletableFuture<SensitivityAnalysisResult> future = sensitivityAnalysisRunner.runAsync(
                network,
                context.getVariantId() != null ? context.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID,
                context.getSensitivityAnalysisInputs().getFactors(),
                new ArrayList<>(context.getSensitivityAnalysisInputs().getContingencies()),
                context.getSensitivityAnalysisInputs().getVariablesSets(),
                sensitivityAnalysisParameters,
                sensitivityAnalysisExecutionService.getLocalComputationManager(),
                reporter);
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancelSensitivityAnalysis.unlock();
        }
    }

    private void cancelSensitivityAnalysisAsync(SensitivityAnalysisCancelContext cancelContext) {
        lockRunAndCancelSensitivityAnalysis.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<SensitivityAnalysisResult> future = futures.get(cancelContext.getResultUuid());
            if (future != null) {
                future.cancel(true);  // cancel computation in progress
            }
            cleanSensitivityAnalysisResultsAndPublishCancel(cancelContext.getResultUuid(), cancelContext.getReceiver());
        } finally {
            lockRunAndCancelSensitivityAnalysis.unlock();
        }
    }

    private void cleanSensitivityAnalysisResultsAndPublishCancel(UUID resultUuid, String receiver) {
        resultRepository.delete(resultUuid);
        notificationService.publishStop("publishStopped-out-0", resultUuid, receiver);
        LOGGER.info(CANCEL_MESSAGE + " (resultUuid='{}')", resultUuid);
    }

    @Bean
    public Consumer<Message<String>> consumeRun() {
        return message -> {
            SensitivityAnalysisResultContext resultContext = SensitivityAnalysisResultContext.fromMessage(message, objectMapper);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();

                startTime.set(System.nanoTime());
                SensitivityAnalysisResult result = run(resultContext.getRunContext(), resultContext.getResultUuid());
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                resultRepository.insert(resultContext.getResultUuid(), result, SensitivityAnalysisStatus.COMPLETED.name());
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage("publishResult-out-0", resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                    LOGGER.info("Sensitivity analysis complete (resultUuid='{}')", resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanSensitivityAnalysisResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception | OutOfMemoryError e) {
                if (!(e instanceof CancellationException)) {
                    LOGGER.error(FAIL_MESSAGE, e);
                    notificationService.publishFail("publishFailed-out-0", resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), e.getMessage());
                    resultRepository.delete(resultContext.getResultUuid());
                    resultRepository.insertStatus(List.of(resultContext.getResultUuid()), SensitivityAnalysisStatus.FAILED.name());
                }
            } finally {
                futures.remove(resultContext.getResultUuid());
                cancelComputationRequests.remove(resultContext.getResultUuid());
                runRequests.remove(resultContext.getResultUuid());
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeCancel() {
        return message -> cancelSensitivityAnalysisAsync(SensitivityAnalysisCancelContext.fromMessage(message));
    }

    public Long getFactorsCount(Map<String, List<UUID>> ids, UUID networkUuid, String variantId, Boolean isInjectionsSet) {
        return sensitivityAnalysisInputBuilderService.getFactorsCount(ids, networkUuid, variantId, isInjectionsSet);
    }
}

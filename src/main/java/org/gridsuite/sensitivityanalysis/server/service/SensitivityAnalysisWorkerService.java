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
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.sensitivityanalysis.server.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityAnalysisRunnerSupplier;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityResultWriterPersisted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.FAIL_MESSAGE;
import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultsBuilder.buildResults;

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

    private final Map<UUID, CompletableFuture<Void>> futures = new ConcurrentHashMap<>();

    private final Map<UUID, SensitivityAnalysisCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private final Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancelSensitivityAnalysis = new ReentrantLock();

    private final NotificationService notificationService;

    private final SensitivityAnalysisExecutionService sensitivityAnalysisExecutionService;

    private final SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService;

    private final SensitivityAnalysisParametersService parametersService;

    private final SensitivityAnalysisObserver sensitivityAnalysisObserver;

    private final Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier;

    private final ApplicationContext applicationContext;

    public SensitivityAnalysisWorkerService(NetworkStoreService networkStoreService,
                                            ReportService reportService,
                                            NotificationService notificationService,
                                            SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService,
                                            SensitivityAnalysisExecutionService sensitivityAnalysisExecutionService,
                                            SensitivityAnalysisResultRepository resultRepository,
                                            ObjectMapper objectMapper,
                                            SensitivityAnalysisParametersService parametersService,
                                            SensitivityAnalysisRunnerSupplier sensitivityAnalysisRunnerSupplier,
                                            SensitivityAnalysisObserver sensitivityAnalysisObserver,
                                            ApplicationContext applicationContext) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.reportService = Objects.requireNonNull(reportService);
        this.notificationService = notificationService;
        this.sensitivityAnalysisExecutionService = Objects.requireNonNull(sensitivityAnalysisExecutionService);
        this.sensitivityAnalysisInputBuilderService = sensitivityAnalysisInputBuilderService;
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.parametersService = parametersService;
        sensitivityAnalysisFactorySupplier = sensitivityAnalysisRunnerSupplier::getRunner;
        this.sensitivityAnalysisObserver = sensitivityAnalysisObserver;
        this.applicationContext = applicationContext;
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

    public SensitivityAnalysisResult run(UUID networkUuid, String variantId, ReportInfos reportInfos, String userId, UUID parametersUuid, UUID loadFlowParametersUuid) {

        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersUuid != null
            ? parametersService.getParameters(parametersUuid)
            .orElse(parametersService.getDefauSensitivityAnalysisParametersInfos())
            : parametersService.getDefauSensitivityAnalysisParametersInfos();

        SensitivityAnalysisInputData inputData = parametersService.buildInputData(sensitivityAnalysisParametersInfos, loadFlowParametersUuid);

        SensitivityAnalysisRunContext runContext = new SensitivityAnalysisRunContext(networkUuid, variantId, inputData, null, sensitivityAnalysisParametersInfos.getProvider(), reportInfos, userId);
        try {
            return run(runContext, null, this::runAsyncInMemory);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOGGER.error(FAIL_MESSAGE, e);
            return null;
        }
    }

    private <T> T run(SensitivityAnalysisRunContext context, UUID resultUuid, SensitivityAnalysisRunnerWrapper<T> runner) throws Exception {
        Objects.requireNonNull(context);

        LOGGER.info("Run sensitivity analysis");

        SensitivityAnalysis.Runner sensitivityAnalysisRunner = sensitivityAnalysisFactorySupplier.apply(context.getProvider());

        AtomicReference<Reporter> rootReporter = new AtomicReference<>(Reporter.NO_OP);
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportUuid() != null) {
            final String reportType = context.getReportType();
            String rootReporterId = context.getReporterId() == null ? reportType : context.getReporterId() + "@" + reportType;
            rootReporter.set(new ReporterModel(rootReporterId, rootReporterId));
            reporter = rootReporter.get().createSubReporter(reportType, reportType + " (${providerToUse})", "providerToUse", sensitivityAnalysisRunner.getName());
            // Delete any previous sensi computation logs
            sensitivityAnalysisObserver.observe("report.delete", context, () ->
                reportService.deleteReport(context.getReportUuid(), reportType));
        }

        CompletableFuture<T> future = runSensitivityAnalysisAsync(context, sensitivityAnalysisRunner, reporter, resultUuid, runner);
        T result = sensitivityAnalysisObserver.observeRun("run", context, future::get);

        if (context.getReportUuid() != null) {
            sensitivityAnalysisObserver.observe("report.send", context, () ->
                reportService.sendReport(context.getReportUuid(), rootReporter.get()));
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

    private interface SensitivityAnalysisRunnerWrapper<T> {

        CompletableFuture<T> runAsync(SensitivityAnalysisRunContext context,
              SensitivityAnalysis.Runner sensitivityAnalysisRunner,
              UUID resultUuid,
              Reporter reporter,
              Network network,
              SensitivityAnalysisParameters parameters);
    }

    private <T> CompletableFuture<T> runSensitivityAnalysisAsync(SensitivityAnalysisRunContext context,
                                                                 SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                                                                 Reporter reporter,
                                                                 UUID resultUuid,
                                                                 SensitivityAnalysisRunnerWrapper<T> runner) {
        lockRunAndCancelSensitivityAnalysis.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return CompletableFuture.completedFuture(null);
            }
            SensitivityAnalysisParameters sensitivityAnalysisParameters = buildParameters(context);
            Network network = getNetwork(context.getNetworkUuid(), context.getVariantId());
            sensitivityAnalysisInputBuilderService.build(context, network, reporter);

            return runner.runAsync(context, sensitivityAnalysisRunner, resultUuid, reporter, network, sensitivityAnalysisParameters);
        } finally {
            lockRunAndCancelSensitivityAnalysis.unlock();
        }
    }

    private CompletableFuture<SensitivityAnalysisResult> runAsyncInMemory(SensitivityAnalysisRunContext context,
                                                                          SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                                                                          UUID resultUuid,
                                                                          Reporter reporter,
                                                                          Network network,
                                                                          SensitivityAnalysisParameters parameters) {
        List<SensitivityFactor> factors = context.getSensitivityAnalysisInputs().getFactors().stream().flatMap(Collection::stream).toList();
        List<Contingency> contingencies = new ArrayList<>(context.getSensitivityAnalysisInputs().getContingencies());

        SensitivityFactorReader sensitivityFactorReader = new SensitivityFactorModelReader(factors, network);
        SensitivityResultModelWriter writer = new SensitivityResultModelWriter(contingencies);

        CompletableFuture<Void> future = sensitivityAnalysisRunner.runAsync(
            network,
            context.getVariantId() != null ? context.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID,
            sensitivityFactorReader,
            writer,
            contingencies,
            context.getSensitivityAnalysisInputs().getVariablesSets(),
            parameters,
            sensitivityAnalysisExecutionService.getLocalComputationManager(),
            reporter);
        return future.thenApply(r -> new SensitivityAnalysisResult(factors, writer.getContingencyStatuses(), writer.getValues()));
    }

    private CompletableFuture<Void> runAsyncPersisted(SensitivityAnalysisRunContext context,
                                                      SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                                                      UUID resultUuid,
                                                      Reporter reporter,
                                                      Network network,
                                                      SensitivityAnalysisParameters parameters) {
        List<List<SensitivityFactor>> groupedFactors = context.getSensitivityAnalysisInputs().getFactors();
        List<Contingency> contingencies = new ArrayList<>(context.getSensitivityAnalysisInputs().getContingencies());

        // WARNING : we're sure order is maintained because InputBuilderService creates List<(preContingency, contingency1, contingency2, etc...)>
        // That's why it works but if this changes we should set up a much more complicated mechanism
        AnalysisResultEntity analysisResult = resultRepository.insertAnalysisResult(resultUuid);
        resultRepository.saveAllResultsAndFlush(buildResults(analysisResult, groupedFactors, contingencies));

        SensitivityResultWriterPersisted writer = (SensitivityResultWriterPersisted) applicationContext.getBean("sensitivityResultWriterPersisted");
        writer.start(resultUuid);

        List<SensitivityFactor> factors = groupedFactors.stream().flatMap(Collection::stream).toList();
        SensitivityFactorReader sensitivityFactorReader = new SensitivityFactorModelReader(factors, network);

        CompletableFuture<Void> future = sensitivityAnalysisRunner.runAsync(
            network,
            context.getVariantId() != null ? context.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID,
            sensitivityFactorReader,
            writer,
            contingencies,
            context.getSensitivityAnalysisInputs().getVariablesSets(),
            parameters,
            sensitivityAnalysisExecutionService.getLocalComputationManager(),
            reporter);
        if (resultUuid != null) {
            futures.put(resultUuid, future);
        }
        return future
            .thenRun(() -> {
                while (writer.isWorking()) {
                    // Nothing to do
                }
                writer.interrupt();
            });
    }

    private void cancelSensitivityAnalysisAsync(SensitivityAnalysisCancelContext cancelContext) {
        lockRunAndCancelSensitivityAnalysis.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<Void> future = futures.get(cancelContext.getResultUuid());
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
        notificationService.publishSensitivityAnalysisStop(resultUuid, receiver);
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
                run(resultContext.getRunContext(), resultContext.getResultUuid(), this::runAsyncPersisted);
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                resultRepository.insertStatus(List.of(resultContext.getResultUuid()), SensitivityAnalysisStatus.COMPLETED.name());

                notificationService.sendSensitivityAnalysisResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                LOGGER.info("Sensitivity analysis complete (resultUuid='{}')", resultContext.getResultUuid());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception | OutOfMemoryError e) {
                if (!(e instanceof CancellationException)) {
                    LOGGER.error(FAIL_MESSAGE, e);
                    notificationService.publishSensitivityAnalysisFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(),
                        e.getMessage(), resultContext.getRunContext().getUserId());
                    resultRepository.delete(resultContext.getResultUuid());
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
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
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
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import org.gridsuite.sensitivityanalysis.server.computation.service.*;
import org.gridsuite.sensitivityanalysis.server.computation.dto.ReportInfos;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.sensitivityanalysis.server.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityAnalysisRunnerSupplier;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityResultWriterPersisted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gridsuite.sensitivityanalysis.server.computation.service.NotificationService.getFailedMessage;
import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultsBuilder.buildContingencyResults;
import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultsBuilder.buildSensitivityResults;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisWorkerService extends AbstractWorkerService<Void, SensitivityAnalysisRunContext, SensitivityAnalysisInputData, SensitivityAnalysisResultService> {
    public static final String COMPUTATION_TYPE = "Sensitivity analysis";

    public static final int CONTINGENCY_RESULTS_BUFFER_SIZE = 128;

    public static final int MAX_RESULTS_BUFFER_SIZE = 128;

    private final SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService;

    private final SensitivityAnalysisParametersService parametersService;

    private final Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier;

    private final ApplicationContext applicationContext;

    public SensitivityAnalysisWorkerService(NetworkStoreService networkStoreService,
                                            ReportService reportService,
                                            NotificationService notificationService,
                                            SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService,
                                            ExecutionService executionService,
                                            SensitivityAnalysisResultService resultService,
                                            ObjectMapper objectMapper,
                                            SensitivityAnalysisParametersService parametersService,
                                            SensitivityAnalysisRunnerSupplier sensitivityAnalysisRunnerSupplier,
                                            SensitivityAnalysisObserver observer,
                                            ApplicationContext applicationContext) {
        super(networkStoreService, notificationService, reportService, resultService, executionService, observer, objectMapper);
        this.sensitivityAnalysisInputBuilderService = sensitivityAnalysisInputBuilderService;
        this.parametersService = parametersService;
        sensitivityAnalysisFactorySupplier = sensitivityAnalysisRunnerSupplier::getRunner;
        this.applicationContext = applicationContext;
    }

    /*public void setSensitivityAnalysisFactorySupplier(Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier) {
        this.sensitivityAnalysisFactorySupplier = Objects.requireNonNull(sensitivityAnalysisFactorySupplier);
    }*/

    public SensitivityAnalysisResult run(UUID networkUuid, String variantId, ReportInfos reportInfos, String userId, UUID parametersUuid, UUID loadFlowParametersUuid) {

        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersUuid != null
            ? parametersService.getParameters(parametersUuid)
            .orElse(parametersService.getDefauSensitivityAnalysisParametersInfos())
            : parametersService.getDefauSensitivityAnalysisParametersInfos();

        SensitivityAnalysisInputData inputData = parametersService.buildInputData(sensitivityAnalysisParametersInfos, loadFlowParametersUuid);

        SensitivityAnalysisRunContext runContext = new SensitivityAnalysisRunContext(
                networkUuid, variantId, null, reportInfos, userId, sensitivityAnalysisParametersInfos.getProvider(), inputData);
        try {
            Network network = getNetwork(runContext.getNetworkUuid(),
                    runContext.getVariantId());
            return run(network, runContext, null); // TODO ici lancer this::runAsyncInMemory
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOGGER.error(getFailedMessage(getComputationType()), e);
            return null;
        }
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeRun() {
        return super.consumeRun();
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeCancel() {
        return super.consumeCancel();
    }

    /**
     * TODO RUN de Joris : comparer avec celui de abstract et le copier coller pour la verion memory si nécessaire
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
     */

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

    @Override
    protected CompletableFuture<SensitivityAnalysisResult> getCompletableFuture(Network network, SensitivityAnalysisRunContext runContext, String provider) {
        SensitivityAnalysis.Runner sensitivityAnalysisRunner = sensitivityAnalysisFactorySupplier.apply(runContext.getProvider());
        String variantId = runContext.getVariantId() != null ? runContext.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID;

        SensitivityAnalysisParameters sensitivityAnalysisParameters = buildParameters(runContext);
        sensitivityAnalysisInputBuilderService.build(runContext, network, runContext.getReporter());

        return sensitivityAnalysisRunner.runAsync(
                network,
                variantId,
                runContext.getSensitivityAnalysisInputs().getFactors(),
                new ArrayList<>(runContext.getSensitivityAnalysisInputs().getContingencies()),
                runContext.getSensitivityAnalysisInputs().getVariablesSets(),
                sensitivityAnalysisParameters,
                executionService.getComputationManager(),
                runContext.getReporter());
    }

    /**
     * TODO version joris à comparer
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
     */

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

        saveSensitivityResults(groupedFactors, resultUuid, contingencies);

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
                .exceptionally(e -> {
                    LOGGER.error("Error occurred during computation", e);
                    writer.interrupt();
                    return null;
                })
                .thenRun(() -> {
                    while (writer.isWorking()) {
                        // Nothing to do
                    }
                    writer.interrupt();
                });
    }

    private void saveSensitivityResults(List<List<SensitivityFactor>> groupedFactors, UUID resultUuid, List<Contingency> contingencies) {
        AnalysisResultEntity analysisResult = resultRepository.insertAnalysisResult(resultUuid);

        Map<String, ContingencyResultEntity> contingencyResults = buildContingencyResults(contingencies, analysisResult);
        Lists.partition(contingencyResults.values().stream().toList(), CONTINGENCY_RESULTS_BUFFER_SIZE)
                .parallelStream()
                .forEach(resultRepository::saveAllContingencyResultsAndFlush);

        Pair<List<SensitivityResultEntity>, List<SensitivityResultEntity>> sensitivityResults = buildSensitivityResults(groupedFactors, analysisResult, contingencyResults);
        Lists.partition(sensitivityResults.getLeft(), MAX_RESULTS_BUFFER_SIZE)
                .parallelStream()
                .forEach(resultRepository::saveAllResultsAndFlush);
        Lists.partition(sensitivityResults.getRight(), MAX_RESULTS_BUFFER_SIZE)
                .parallelStream()
                .forEach(resultRepository::saveAllResultsAndFlush);
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    @Override
    protected SensitivityAnalysisResultContext fromMessage(Message<String> message) {
        return SensitivityAnalysisResultContext.fromMessage(message, objectMapper);
    }

    @Override
    protected void saveResult(Network network, AbstractResultContext<SensitivityAnalysisRunContext> resultContext, SensitivityAnalysisResult result) {
        resultService.insert(resultContext.getResultUuid(), result, SensitivityAnalysisStatus.COMPLETED);
    }

    /**
     * TODO : consumeRun de Joris
     *
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
     */
}

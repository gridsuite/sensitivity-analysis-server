/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.sensitivity.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.computation.service.*;
import org.gridsuite.sensitivityanalysis.server.PropertyServerNameProvider;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.util.BatchAsyncPollerFactory;
import org.gridsuite.sensitivityanalysis.server.util.ScheduledThreadPoolFactory;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityAnalysisRunnerSupplier;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityResultPersistedWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gridsuite.computation.service.NotificationService.getFailedMessage;
import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultsBuilder.buildContingencyResults;
import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultsBuilder.buildSensitivityResults;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Slf4j
@Service
public class SensitivityAnalysisWorkerService extends AbstractWorkerService<Boolean, SensitivityAnalysisRunContext, SensitivityAnalysisInputData, SensitivityAnalysisResultService> {

    public static final String COMPUTATION_TYPE = "Sensitivity analysis";
    public static final int CONTINGENCY_RESULTS_BUFFER_SIZE = 128;
    public static final int MAX_RESULTS_BUFFER_SIZE = 128;

    protected final SensitivityAnalysisInMemoryObserver inMemoryObserver;
    private final SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService;
    private final SensitivityAnalysisParametersService parametersService;
    private final Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier;
    private final ScheduledThreadPoolFactory scheduledThreadPoolFactory;
    private final BatchAsyncPollerFactory batchAsyncPollerFactory;

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
                                            SensitivityAnalysisInMemoryObserver inMemoryObserver,
                                            PropertyServerNameProvider propertyServerNameProvider) {
        super(networkStoreService, notificationService, reportService, resultService, executionService, observer, objectMapper, propertyServerNameProvider);
        this.sensitivityAnalysisInputBuilderService = sensitivityAnalysisInputBuilderService;
        this.parametersService = parametersService;
        this.sensitivityAnalysisFactorySupplier = sensitivityAnalysisRunnerSupplier::getRunner;
        this.inMemoryObserver = inMemoryObserver;
        this.scheduledThreadPoolFactory = ScheduledThreadPoolFactory.getDefault();
        this.batchAsyncPollerFactory = BatchAsyncPollerFactory.getDefault();
    }

    @Override
    protected boolean resultCanBeSaved(Boolean isResultOk) {
        return true;
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
    protected CompletableFuture<Boolean> getCompletableFuture(SensitivityAnalysisRunContext runContext, String provider, UUID resultUuid) {
        SensitivityAnalysis.Runner sensitivityAnalysisRunner = sensitivityAnalysisFactorySupplier.apply(runContext.getProvider());
        String variantId = runContext.getVariantId() != null ? runContext.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID;

        SensitivityAnalysisParameters sensitivityAnalysisParameters = buildParameters(runContext);
        sensitivityAnalysisInputBuilderService.build(runContext, runContext.getNetwork(), runContext.getReportNode());

        List<List<SensitivityFactor>> groupedFactors = runContext.getSensitivityAnalysisInputs().getFactors();
        List<Contingency> contingencies = new ArrayList<>(runContext.getSensitivityAnalysisInputs().getContingencies());

        saveSensitivityResults(groupedFactors, resultUuid, contingencies);

        List<SensitivityFactor> factors = groupedFactors.stream().flatMap(Collection::stream).toList();
        SensitivityFactorReader sensitivityFactorReader = new SensitivityFactorModelReader(factors, runContext.getNetwork());
        SensitivityResultPersistedWriter sensitivityResultPersistedWriter = new SensitivityResultPersistedWriter(resultUuid, resultService, scheduledThreadPoolFactory, batchAsyncPollerFactory);

        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setContingencies(contingencies)
                .setVariableSets(runContext.getSensitivityAnalysisInputs().getVariablesSets())
                .setParameters(sensitivityAnalysisParameters)
                .setComputationManager(executionService.getComputationManager())
                .setReportNode(runContext.getReportNode());

        return sensitivityAnalysisRunner.runAsync(runContext.getNetwork(), variantId, sensitivityFactorReader, sensitivityResultPersistedWriter, runParameters)
                .thenApply(unused -> Boolean.TRUE)
                .whenComplete((result, throwable) -> syncWriterCompletion(throwable, sensitivityResultPersistedWriter))
                .exceptionally(throwable -> handleAsyncError(throwable, runContext));
    }

    private void saveSensitivityResults(List<List<SensitivityFactor>> groupedFactors, UUID resultUuid, List<Contingency> contingencies) {
        AnalysisResultEntity analysisResult = resultService.insertAnalysisResult(resultUuid);

        Map<String, ContingencyResultEntity> contingencyResults = buildContingencyResults(contingencies, analysisResult);
        Lists.partition(contingencyResults.values().stream().toList(), CONTINGENCY_RESULTS_BUFFER_SIZE)
                .parallelStream()
                .forEach(resultService::saveAllContingencyResultsAndFlush);

        Pair<List<SensitivityResultEntity>, List<SensitivityResultEntity>> sensitivityResults = buildSensitivityResults(groupedFactors, analysisResult, contingencyResults);
        Lists.partition(sensitivityResults.getLeft(), MAX_RESULTS_BUFFER_SIZE)
                .parallelStream()
                .forEach(resultService::saveAllResultsAndFlush);
        Lists.partition(sensitivityResults.getRight(), MAX_RESULTS_BUFFER_SIZE)
                .parallelStream()
                .forEach(resultService::saveAllResultsAndFlush);
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
    protected void saveResult(Network network, AbstractResultContext<SensitivityAnalysisRunContext> resultContext, Boolean isResultOk) {
        SensitivityAnalysisStatus status = isResultOk.equals(Boolean.TRUE) ? SensitivityAnalysisStatus.COMPLETED : SensitivityAnalysisStatus.FAILED;
        resultService.insertStatus(List.of(resultContext.getResultUuid()), status);
    }

    /**
     * The following run, runInMemory, runSensitivityAnalysisAsync and runAsyncInMemory functions are specific to the "run in memory" alternative run mode.
     * This mode is different from the default mode that saves results in database as you run and uses the default functions from the AbstractWorkerService service class.
     */
    public SensitivityAnalysisResult run(UUID networkUuid, String variantId, ReportInfos reportInfos, String userId, UUID parametersUuid, UUID loadFlowParametersUuid) {

        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersUuid != null
                ? parametersService.getParameters(parametersUuid)
                .orElse(parametersService.getDefauSensitivityAnalysisParametersInfos())
                : parametersService.getDefauSensitivityAnalysisParametersInfos();

        SensitivityAnalysisInputData inputData = parametersService.buildInputData(sensitivityAnalysisParametersInfos, loadFlowParametersUuid);

        String provider = sensitivityAnalysisParametersInfos.getProvider();
        if (provider == null) {
            provider = parametersService.getDefauSensitivityAnalysisParametersInfos().getProvider();
        }
        SensitivityAnalysisRunContext runContext = new SensitivityAnalysisRunContext(
                networkUuid, variantId, null, reportInfos, userId, provider, inputData);
        try {
            return runInMemory(runContext);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error(getFailedMessage(getComputationType()), e);
            return null;
        }
    }

    private SensitivityAnalysisResult runInMemory(SensitivityAnalysisRunContext runContext) throws Exception {
        Objects.requireNonNull(runContext);

        log.info("Run sensitivity analysis");

        SensitivityAnalysis.Runner sensitivityAnalysisRunner = sensitivityAnalysisFactorySupplier.apply(runContext.getProvider());

        AtomicReference<ReportNode> rootReporter = new AtomicReference<>(ReportNode.NO_OP);
        ReportNode reporter = ReportNode.NO_OP;
        if (runContext.getReportInfos().reportUuid() != null) {
            final String reportType = runContext.getReportInfos().computationType();
            String rootReporterId = runContext.getReportInfos().reporterId() == null ? reportType : runContext.getReportInfos().reporterId() + "@" + reportType;
            rootReporter.set(ReportNode.newRootReportNode()
                    .withAllResourceBundlesFromClasspath()
                    .withMessageTemplate("sensitivity.analysis.server.rootReporterId")
                    .withUntypedValue("rootReporterId", rootReporterId)
                    .build());
            reporter = rootReporter.get().newReportNode().withMessageTemplate("sensitivity.analysis.server.reportType")
                    .withUntypedValue("reportType", reportType)
                    .withUntypedValue("providerToUse", sensitivityAnalysisRunner.getName()).add();
            // Delete any previous sensi computation logs
            inMemoryObserver.observe("report.delete",
                    runContext, () -> reportService.deleteReport(runContext.getReportInfos().reportUuid()));
        }

        CompletableFuture<SensitivityAnalysisResult> future = runSensitivityAnalysisAsync(runContext, sensitivityAnalysisRunner, reporter);
        SensitivityAnalysisResult result = inMemoryObserver.observeRun("run", runContext, future::get);

        if (runContext.getReportInfos().reportUuid() != null) {
            inMemoryObserver.observe("report.send", runContext, () -> reportService.sendReport(runContext.getReportInfos().reportUuid(), rootReporter.get()));
        }
        return result;
    }

    private CompletableFuture<SensitivityAnalysisResult> runSensitivityAnalysisAsync(SensitivityAnalysisRunContext context,
                                                                                     SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                                                                                     ReportNode reporter) {
        lockRunAndCancel.lock();
        try {
            SensitivityAnalysisParameters sensitivityAnalysisParameters = buildParameters(context);
            Network network = getNetwork(context.getNetworkUuid(), context.getVariantId());
            sensitivityAnalysisInputBuilderService.build(context, network, reporter);

            return runAsyncInMemory(context, sensitivityAnalysisRunner, reporter, network, sensitivityAnalysisParameters);
        } finally {
            lockRunAndCancel.unlock();
        }
    }

    private CompletableFuture<SensitivityAnalysisResult> runAsyncInMemory(SensitivityAnalysisRunContext context,
                                                                          SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                                                                          ReportNode reporter,
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
                executionService.getComputationManager(),
                reporter);
        return future.thenApply(r -> new SensitivityAnalysisResult(factors, writer.getContingencyStatuses(), writer.getValues()));
    }

    private void syncWriterCompletion(Throwable throwable, SensitivityResultPersistedWriter persistedWriter) {
        try {
            persistedWriter.notifyCompletion();

            // success of the computation -> we wait for the writer to properly finish its job
            // failure of the computation -> we don't wait for the writer : we don't need to persist results
            if (throwable == null) {
                persistedWriter.waitForCompletion();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // writer can throw an exception if one or more results could not be persisted
            throw new RuntimeException("Unexpected error during writer completion", e);
        } finally {
            persistedWriter.close();
        }
    }

    private Boolean handleAsyncError(Throwable throwable, SensitivityAnalysisRunContext runContext) {
        log.error("Error occurred during computation", throwable);
        runContext.getReportNode()
                .newReportNode()
                .withMessageTemplate("sensitivity.analysis.server.sensitivityComputationFailed")
                .withUntypedValue("exception", throwable.getMessage())
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
        // false since the computation failed
        return false;
    }
}

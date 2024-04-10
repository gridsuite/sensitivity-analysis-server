/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.Extension;
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
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityAnalysisRunnerSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gridsuite.sensitivityanalysis.server.computation.service.NotificationService.getFailedMessage;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisWorkerService extends AbstractWorkerService<SensitivityAnalysisResult, SensitivityAnalysisRunContext, SensitivityAnalysisInputData, SensitivityAnalysisResultService> {
    public static final String COMPUTATION_TYPE = "Sensitivity analysis";

    private final SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService;

    private final SensitivityAnalysisParametersService parametersService;

    private Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier;

    public SensitivityAnalysisWorkerService(NetworkStoreService networkStoreService,
                                            ReportService reportService,
                                            NotificationService notificationService,
                                            SensitivityAnalysisInputBuilderService sensitivityAnalysisInputBuilderService,
                                            ExecutionService executionService,
                                            SensitivityAnalysisResultService resultService,
                                            ObjectMapper objectMapper,
                                            SensitivityAnalysisParametersService parametersService,
                                            SensitivityAnalysisRunnerSupplier sensitivityAnalysisRunnerSupplier,
                                            SensitivityAnalysisObserver observer) {
        super(networkStoreService, notificationService, reportService, resultService, executionService, observer, objectMapper);
        this.sensitivityAnalysisInputBuilderService = sensitivityAnalysisInputBuilderService;
        this.parametersService = parametersService;
        sensitivityAnalysisFactorySupplier = sensitivityAnalysisRunnerSupplier::getRunner;
    }

    public void setSensitivityAnalysisFactorySupplier(Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier) {
        this.sensitivityAnalysisFactorySupplier = Objects.requireNonNull(sensitivityAnalysisFactorySupplier);
    }

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
            return run(network, runContext, null);
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
}

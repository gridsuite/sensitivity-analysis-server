/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicDouble;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStageDefinition;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStagesSelection;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStatus;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.*;
import org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy.NonEvacuatedEnergyRepository;
import org.gridsuite.sensitivityanalysis.server.computation.service.NotificationService;
import org.gridsuite.sensitivityanalysis.server.service.NonEvacuatedNotificationService;
import org.gridsuite.sensitivityanalysis.server.computation.service.ReportService;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisCancelContext;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisExecutionService;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityAnalysisRunnerSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Math.abs;
import static java.util.stream.Collectors.toMap;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyWorkerService {
    public static final String COMPUTATION_TYPE = "????????????"; // TODO à remplir avec ce qui a dans observer machin je suppose

    private static final Logger LOGGER = LoggerFactory.getLogger(NonEvacuatedEnergyWorkerService.class);

    private final NetworkStoreService networkStoreService;

    private final ReportService reportService;

    private final NonEvacuatedEnergyRepository nonEvacuatedEnergyRepository;

    private final ObjectMapper objectMapper;

    private final Map<UUID, CompletableFuture<String>> futures = new ConcurrentHashMap<>();

    private final Map<UUID, SensitivityAnalysisCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private final Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancel = new ReentrantLock();

    private final NonEvacuatedNotificationService notificationService;

    private final SensitivityAnalysisExecutionService sensitivityAnalysisExecutionService;

    private final NonEvacuatedEnergyInputBuilderService nonEvacuatedEnergyInputBuilderService;

    private Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier;

    private static final double EPSILON = 0.0001;

    private static final int MAX_ITERATION_IN_STAGE = 4;

    private static final double EPSILON_MAX_VARIATION = 0.3;

    // to serialize null key in results by contingency HashMap as "" (for pre-contingency results)
    static class NullKeySerializer extends StdSerializer<Object> {
        public NullKeySerializer() {
            this(null);
        }

        public NullKeySerializer(Class<Object> t) {
            super(t);
        }

        @Override
        public void serialize(Object nullKey, JsonGenerator jsonGenerator, SerializerProvider unused) throws IOException {
            jsonGenerator.writeFieldName("");
        }
    }

    public NonEvacuatedEnergyWorkerService(NetworkStoreService networkStoreService, ReportService reportService,
                                           NonEvacuatedNotificationService notificationService,
                                           NonEvacuatedEnergyInputBuilderService nonEvacuatedEnergyInputBuilderService,
                                           SensitivityAnalysisExecutionService sensitivityAnalysisExecutionService,
                                           NonEvacuatedEnergyRepository nonEvacuatedEnergyRepository, ObjectMapper objectMapper,
                                           SensitivityAnalysisRunnerSupplier sensitivityAnalysisRunnerSupplier) {
        this.networkStoreService = Objects.requireNonNull(networkStoreService);
        this.reportService = Objects.requireNonNull(reportService);
        this.notificationService = notificationService;
        this.sensitivityAnalysisExecutionService = Objects.requireNonNull(sensitivityAnalysisExecutionService);
        this.nonEvacuatedEnergyInputBuilderService = nonEvacuatedEnergyInputBuilderService;
        this.nonEvacuatedEnergyRepository = Objects.requireNonNull(nonEvacuatedEnergyRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.objectMapper.getSerializerProvider().setNullKeySerializer(new NullKeySerializer());
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

    private String run(NonEvacuatedEnergyRunContext context, UUID resultUuid) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(context);

        LOGGER.info("Run non evacuated energy sensitivity analysis");

        SensitivityAnalysis.Runner sensitivityAnalysisRunner = sensitivityAnalysisFactorySupplier.apply(context.getProvider());

        Reporter rootReporter = Reporter.NO_OP;
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportUuid() != null) {
            final String reportType = context.getReportType();
            String rootReporterId = context.getReporterId() == null ? reportType : context.getReporterId() + "@" + reportType;
            rootReporter = new ReporterModel(rootReporterId, rootReporterId);
            reporter = rootReporter.createSubReporter(reportType, reportType + " (${providerToUse})", "providerToUse", sensitivityAnalysisRunner.getName());
            // Delete any previous non evacuated energy computation logs
            reportService.deleteReport(context.getReportUuid(), reportType);
        }

        try {
            CompletableFuture<String> future = runAsync(context, sensitivityAnalysisRunner, reporter, resultUuid);
            return future == null ? null : future.get();
        } finally {
            if (context.getReportUuid() != null) {
                reportService.sendReport(context.getReportUuid(), rootReporter);
            }
        }
    }

    private static SensitivityAnalysisParameters buildParameters(NonEvacuatedEnergyRunContext context) {
        SensitivityAnalysisParameters params = context.getNonEvacuatedEnergyInputData().getParameters() == null ?
            new SensitivityAnalysisParameters() : context.getNonEvacuatedEnergyInputData().getParameters();

        // set the flowFlowThreshold value
        params.setFlowFlowSensitivityValueThreshold(context.getNonEvacuatedEnergyInputData().getNonEvacuatedEnergyGeneratorsCappings().getSensitivityThreshold());

        if (context.getNonEvacuatedEnergyInputData().getLoadFlowSpecificParameters() == null
                || context.getNonEvacuatedEnergyInputData().getLoadFlowSpecificParameters().isEmpty()) {
            return params; // no specific LF params
        }
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
                .filter(p -> p.getName().equals(context.getProvider()))
                .findFirst().orElseThrow(() -> new PowsyblException("Load flow provider not found " + context.getProvider()));
        Extension<LoadFlowParameters> extension = lfProvider.loadSpecificParameters(context.getNonEvacuatedEnergyInputData().getLoadFlowSpecificParameters())
                .orElseThrow(() -> new PowsyblException("Cannot add specific loadflow parameters with sensitivity analysis provider " + context.getProvider()));
        params.getLoadFlowParameters().addExtension((Class) extension.getClass(), extension);
        return params;
    }

    private void cloneNetworkVariant(Network network, String originVariantId, String destinationVariantId) {
        String startingVariant = StringUtils.isBlank(originVariantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : originVariantId;
        try {
            network.getVariantManager().cloneVariant(startingVariant, destinationVariantId, true);  // cloning variant
            network.getVariantManager().setWorkingVariant(destinationVariantId);  // set current variant to destination variant
        } catch (PowsyblException e) {
            throw new PowsyblException("Variant '" + startingVariant + "' not found !!");
        }
    }

    private void applyStage(Network network,
                            NonEvacuatedEnergyStagesSelection stageSelection,
                            NonEvacuatedEnergyRunContext context,
                            Reporter reporter) {
        // loop on each energy source in the stage input data
        for (int i = 0; i < stageSelection.getStagesDefinitionIndex().size(); ++i) {
            int stageDefinitionIndex = stageSelection.getStagesDefinitionIndex().get(i);
            NonEvacuatedEnergyStageDefinition stageDefinition = context.getNonEvacuatedEnergyInputData().getNonEvacuatedEnergyStagesDefinition().get(stageDefinitionIndex);
            float pMaxPercent = stageDefinition.getPMaxPercents().get(stageSelection.getPMaxPercentsIndex().get(i));

            stageDefinition.getGenerators().stream()
                .flatMap(generatorFilter -> nonEvacuatedEnergyInputBuilderService.getIdentifiablesFromContainer(context.getNetworkUuid(), context.getVariantId(), generatorFilter, List.of(IdentifiableType.GENERATOR), reporter))
                .map(IdentifiableAttributes::getId)
                .map(network::getGenerator)
                .filter(Objects::nonNull)
                .forEach(generator -> {
                    double oldTargetP = generator.getTargetP();
                    double newTargetP = (generator.getMaxP() * pMaxPercent) / 100;
                    generator.setTargetP(newTargetP);
                    reporter.report(Report.builder()
                        .withKey("ApplyStageGeneratorTargetP")
                        .withDefaultMessage("Generator ${generatorId} targetP modification : ${oldTargetP} --> ${newTargetP}")
                        .withSeverity(TypedValue.TRACE_SEVERITY)
                        .withValue("generatorId", generator.getId())
                        .withValue("oldTargetP", oldTargetP)
                        .withValue("newTargetP", newTargetP)
                        .build());
                });
        }
    }

    private LimitInfos getLimitInfos(SensitivityFactor factor, boolean isIst, Float coeff, String limitName,
                                     Optional<CurrentLimits> currentLimits1, Optional<CurrentLimits> currentLimits2) {
        String name = null;
        double value = Double.NaN;
        TwoSides side = TwoSides.ONE;

        if (isIst) {
            name = "IST";
            if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                value = currentLimits1.map(l -> l.getPermanentLimit() * coeff / 100).orElse(Double.NaN);
            } else if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_2) {
                value = currentLimits2.map(l -> l.getPermanentLimit() * coeff / 100).orElse(Double.NaN);
                side = TwoSides.TWO;
            }
        } else if (!StringUtils.isEmpty(limitName)) {
            name = limitName;
            if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                Optional<LoadingLimits.TemporaryLimit> limit = currentLimits1.flatMap(currentLimits -> currentLimits.getTemporaryLimits().stream().filter(l -> l.getName().equals(limitName)).findFirst());
                value = limit.map(temporaryLimit -> temporaryLimit.getValue() * coeff / 100).orElse(Double.NaN);
            } else if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_2) {
                Optional<LoadingLimits.TemporaryLimit> limit = currentLimits2.flatMap(currentLimits -> currentLimits.getTemporaryLimits().stream().filter(l -> l.getName().equals(limitName)).findFirst());
                value = limit.map(temporaryLimit -> temporaryLimit.getValue() * coeff / 100).orElse(Double.NaN);
                side = TwoSides.TWO;
            }
        }
        return new LimitInfos(name, value, side);
    }

    private LimitInfos getLimitValueFromFactorAndSensitivityValue(SensitivityValue sensitivityValue,
                                                                  SensitivityFactor factor,
                                                                  MonitoredBranchThreshold monitoredBranchThreshold,
                                                                  MonitoredBranchDetailResult monitoredBranchDetailResult) {
        int contingencyIndex = sensitivityValue.getContingencyIndex();

        Branch<?> branch = monitoredBranchThreshold.getBranch();
        Optional<CurrentLimits> currentLimits1 = branch.getCurrentLimits1();
        Optional<CurrentLimits> currentLimits2 = branch.getCurrentLimits2();

        // use contingencyIndex (-1 or not) to check istN/Nlimitname or istNm1/Nm1Limitname
        // limits in iidm are in A, so consider only SensitivityFunctionType BRANCH_CURRENT to return
        // the limit value multiplied by the input coefficient (nCoeff/nm1Coeff)
        LimitInfos limitInfos;
        if (contingencyIndex < 0) { // N
            limitInfos = getLimitInfos(factor, monitoredBranchThreshold.isIstN(), monitoredBranchThreshold.getNCoeff(), monitoredBranchThreshold.getNLimitName(), currentLimits1, currentLimits2);
        } else {  // N-1
            limitInfos = getLimitInfos(factor, monitoredBranchThreshold.isIstNm1(), monitoredBranchThreshold.getNm1Coeff(), monitoredBranchThreshold.getNm1LimitName(), currentLimits1, currentLimits2);
        }

        // update monitored branch detail result
        if (!Double.isNaN(limitInfos.getValue())) {  // sensitivity in A/MW
            monitoredBranchDetailResult.setIntensity(sensitivityValue.getFunctionReference());
            monitoredBranchDetailResult.setLimitName(limitInfos.getName());
            monitoredBranchDetailResult.setLimitValue(limitInfos.getValue());
            monitoredBranchDetailResult.setPercentOverload((sensitivityValue.getFunctionReference() / limitInfos.getValue()) * 100.);
        }

        // limit value is set only for sensitivity in A/MW : it will be NaN for sensitivity in MW/MW
        return limitInfos;
    }

    private double computeVariationForMonitoredBranch(Network network,
                                                      NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                                      SensitivityFactor factor,
                                                      MonitoredBranchThreshold monitoredBranchThreshold,
                                                      String contingencyId,
                                                      Map<String, Double> generatorsSensitivities,
                                                      SensitivityValue sensitivityValue,
                                                      Map<String, GeneratorCapping> generatorCappings,
                                                      MonitoredBranchDetailResult monitoredBranchDetailResult,
                                                      Reporter reporter) {
        double injectionVariation = Double.NaN;
        double functionReference = sensitivityValue.getFunctionReference();

        // get the limit value (in A) to consider from the monitored branch limits, using the factor function type and the monitored branch thresholds parameters
        LimitInfos limitInfos = getLimitValueFromFactorAndSensitivityValue(sensitivityValue, factor, monitoredBranchThreshold, monitoredBranchDetailResult);

        if (!Double.isNaN(limitInfos.getValue())) {
            String contingencyStr = contingencyId != null ? ("Contingency " + contingencyId) : "N";
            reporter.report(Report.builder()
                .withKey("MonitoredBranchLimitValueToConsider")
                .withDefaultMessage("${contingency} : ${limitName} limit value to consider on side ${side} for monitored branch ${branchId} = ${limitValue} to compare with I = ${intensity}")
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .withValue("contingency", contingencyStr)
                .withValue("limitName", limitInfos.getName())
                .withValue("side", limitInfos.getSide().name())
                .withValue("branchId", monitoredBranchThreshold.getBranch().getId())
                .withValue("limitValue", limitInfos.getValue())
                .withValue("intensity", functionReference)
                .build());

            // compare the functionReference (monitored branch intensity in A) to the limit value (in A)
            double delta = limitInfos.getValue() - functionReference;
            if (delta < 0) {
                reporter.report(Report.builder()
                    .withKey("MonitoredBranchConstraintDeltaFound")
                    .withDefaultMessage("Constraint found for monitored branch ${branchId} : value of intensity to reduce = ${delta}")
                    .withSeverity(TypedValue.TRACE_SEVERITY)
                    .withValue("branchId", monitoredBranchThreshold.getBranch().getId())
                    .withValue("delta", Math.abs(delta))
                    .build());

                // monitored branch is over the limit :
                // we compute the generators cappings needed to set the monitored branch under the limit
                injectionVariation = computeInjectionVariationForMonitoredBranch(network, nonEvacuatedEnergyInputs, generatorsSensitivities, delta, generatorCappings, monitoredBranchDetailResult);

                reporter.report(Report.builder()
                    .withKey("GeneratorsVariationForMonitoredBranch")
                    .withDefaultMessage("Generator variation found for monitored branch ${branchId} : ${variation}")
                    .withSeverity(TypedValue.TRACE_SEVERITY)
                    .withValue("branchId", monitoredBranchThreshold.getBranch().getId())
                    .withValue("variation", injectionVariation)
                    .build());
            } else {
                reporter.report(Report.builder()
                    .withKey("MonitoredBranchNoConstraint")
                    .withDefaultMessage("No constraint found for monitored branch ${branchId}")
                    .withSeverity(TypedValue.TRACE_SEVERITY)
                    .withValue("branchId", monitoredBranchThreshold.getBranch().getId())
                    .build());
            }
        }

        return injectionVariation;
    }

    private Map<String, Double> getSensitivityCoeffs(Map<String, Double> sensitivities) {
        Double referenceSensitivity = 1.;
        Map<String, Double> sensitivityCoeff = new HashMap<>();
        int i = 0;
        String referenceGroupId;

        for (Map.Entry<String, Double> entry : sensitivities.entrySet()) {
            if (i == 0) {  // generator with the most impact is the reference generator : define a sensitivity coefficient of 1
                referenceGroupId = entry.getKey();
                sensitivityCoeff.put(referenceGroupId, 1.);
                referenceSensitivity = entry.getValue();
                i++;
            } else {  // other generator : compute sensitivity coefficient from sensitivity value and reference generator sensitivity value
                sensitivityCoeff.put(entry.getKey(), entry.getValue() / referenceSensitivity);
            }
        }
        return sensitivityCoeff;
    }

    private double computeInjectionVariationForMonitoredBranch(Network network,
                                                               NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                                               Map<String, Double> generatorsSensitivities,
                                                               double delta,
                                                               Map<String, GeneratorCapping> generatorCappings,
                                                               MonitoredBranchDetailResult monitoredBranchDetailResult) {
        // sort the map in reverse order to get first the generator which have the most impact
        LinkedHashMap<String, Double> sensitivities = generatorsSensitivities.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        // define sensitivity coefficient for each generator
        Map<String, Double> sensitivityCoeff = getSensitivityCoeffs(sensitivities);

        // compute the generators cappings needed to set the monitored branch under the limit
        Map<EnergySource, Double> variationsByEnergySource = new EnumMap<>(EnergySource.class);
        double injectionVariation = computeInjectionVariationFromGeneratorsSensitivities(network, nonEvacuatedEnergyInputs, sensitivities, sensitivityCoeff, delta, variationsByEnergySource, generatorCappings);

        // update sensitivities by energy source in monitored branch result
        Map<EnergySource, Double> sensitivitiesByEnergySource = new EnumMap<>(EnergySource.class);
        sensitivities.forEach((key, value) -> {
            Generator generator = network.getGenerator(key);
            if (generator != null) {
                if (!sensitivitiesByEnergySource.containsKey(generator.getEnergySource())) {
                    sensitivitiesByEnergySource.put(generator.getEnergySource(), value);
                } else {
                    sensitivitiesByEnergySource.put(generator.getEnergySource(), sensitivitiesByEnergySource.get(generator.getEnergySource()) + value);
                }
            }
        });
        monitoredBranchDetailResult.getSensitivityByEnergySource().putAll(sensitivitiesByEnergySource);

        return injectionVariation;
    }

    private double computeInjectionVariationFromGeneratorsSensitivities(Network network,
                                                                        NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                                                        Map<String, Double> generatorsSensitivities,
                                                                        Map<String, Double> sensitivitiesCoefficients,
                                                                        double delta,
                                                                        Map<EnergySource, Double> variationsByEnergySource,
                                                                        Map<String, GeneratorCapping> generatorCappings) {
        double injectionVariation = 0;

        // compute the coefficient sum
        double coeffSum = 0;
        for (Map.Entry<String, Double> entry : generatorsSensitivities.entrySet()) {
            coeffSum += entry.getValue() * sensitivitiesCoefficients.get(entry.getKey());
        }
        double referenceGeneratorVariation = abs(delta / coeffSum);

        // for each generator, we try to limit his power
        double remaining = 0;
        List<Generator> generatorsToDispatch = new ArrayList<>();

        // compute the variation to do for each generator
        for (Map.Entry<String, Double> entry : sensitivitiesCoefficients.entrySet()) {
            String generatorId = entry.getKey();
            Generator generator = network.getGenerator(generatorId);
            double sensitivityCoefficent = entry.getValue();
            double generatorVariation = sensitivityCoefficent * referenceGeneratorVariation;
            double targetP = generator.getTargetP();

            if (generatorVariation > targetP) {
                // the generator variation is higher than the generator targetP :
                // we cap the generator variation to the targetP and this generator will no more be considered in the further dispatching process
                // we add the delta between the generation variation and the generator targetP the remaining
                remaining += generatorVariation - targetP;
                generatorVariation = targetP;
            } else {
                // variation is lower than generator targetP :
                // we keep this generator for the further dispatching process
                generatorsToDispatch.add(generator);
            }
            injectionVariation += sensitivityCoefficent * referenceGeneratorVariation;
            GeneratorCapping generatorCapping = new GeneratorCapping();
            generatorCapping.setGeneratorId(generatorId);
            generatorCapping.setEnergySource(generator.getEnergySource());
            generatorCapping.setPInit(nonEvacuatedEnergyInputs.getGeneratorsPInit().get(generatorId));
            generatorCapping.setCapping(generatorVariation);
            generatorCapping.setCumulatedCapping(0.);
            generatorCappings.put(generatorId, generatorCapping);
            variationsByEnergySource.put(generator.getEnergySource(), generatorVariation);
        }

        // there is a remaining, and we still have generators to dispatch
        if (remaining >= EPSILON && !generatorsToDispatch.isEmpty()) {
            int i = 0;

            // loop until no more remaining or no more generators to dispatch or max iteration reached
            while (remaining >= EPSILON && !generatorsToDispatch.isEmpty() && i <= 10) {
                i++;
                int j = 0;
                String referenceGeneratorId = "";
                Map<String, Double> sensitivityCoefficientRemaining = new HashMap<>();

                // recomputing the remaining sensitivity coefficients for each generator to dispatch
                coeffSum = 0;
                for (Generator generator : generatorsToDispatch) {
                    String generatorId = generator.getId();
                    if (j == 0) {
                        // first generator is the new reference generator and will have a coefficient of 1
                        referenceGeneratorId = generatorId;
                        sensitivityCoefficientRemaining.put(referenceGeneratorId, 1.);
                        j++;
                    } else {
                         // other generator : compute sensitivity remaining coefficient from sensitivity value and reference generator sensitivity value
                        sensitivityCoefficientRemaining.put(generatorId, generatorsSensitivities.get(generatorId) / generatorsSensitivities.get(referenceGeneratorId));
                    }
                    coeffSum += sensitivityCoefficientRemaining.get(generatorId);
                }

                if (coeffSum != 0) {
                    referenceGeneratorVariation = abs(remaining / coeffSum);

                    // for each generator to dispatch, we try to limit his power
                    ListIterator<Generator> iterator = generatorsToDispatch.listIterator();
                    while (iterator.hasNext()) {
                        Generator generator = iterator.next();
                        String generatorId = generator.getId();
                        double generatorVariation = sensitivityCoefficientRemaining.get(generatorId) * referenceGeneratorVariation;
                        double targetP = generator.getTargetP();

                        if (generatorVariation + generatorCappings.get(generatorId).getCapping() > targetP) {
                            // the sum of generator variation and generator capping is higher than the generator targetP :
                            // we update the generator capping with the targetP and remove this generator from the generators to dispatch
                            // we substract the delta between the generator targetP and the generator capping from the remaining
                            variationsByEnergySource.put(generator.getEnergySource(), variationsByEnergySource.get(generator.getEnergySource()) + targetP - generatorCappings.get(generatorId).getCapping());
                            generatorCappings.get(generatorId).setCapping(targetP);
                            remaining -= targetP - generatorCappings.get(generatorId).getCapping();
                            iterator.remove();
                        } else {
                            // the sum of generator variation and generator capping is lower than generator targetP :
                            // we keep this generator for the further dispatching process
                            // we add the generator variation to the generator capping
                            // we substract the generator variation from the remaining
                            remaining -= generatorVariation;
                            variationsByEnergySource.put(generator.getEnergySource(), variationsByEnergySource.get(generator.getEnergySource()) + generatorVariation);
                            generatorCappings.get(generatorId).setCapping(generatorCappings.get(generatorId).getCapping() + generatorVariation);
                        }
                    }
                }
            }
        }

        return injectionVariation;
    }

    private Map<String, SensitivitiesByBranch> collectSensitivitiesByBranches(SensitivityAnalysisResult sensiResult) {
        Map<String, SensitivitiesByBranch> sensitivitiesByBranches = new HashMap<>();
        for (SensitivityValue sensitivityValue : sensiResult.getValues()) {
            int factorIndex = sensitivityValue.getFactorIndex();
            SensitivityFactor factor = sensiResult.getFactors().get(factorIndex);
            SensitivityFunctionType functionType = factor.getFunctionType();
            // Here, we consider from the results only function type current
            if (functionType == SensitivityFunctionType.BRANCH_CURRENT_1 || functionType == SensitivityFunctionType.BRANCH_CURRENT_2) {
                String functionId = factor.getFunctionId(); // the function id here is a branch id
                String variableId = factor.getVariableId(); // the variable id here is a generator id
                int contingencyIndex = sensitivityValue.getContingencyIndex();
                String contingencyId;
                if (contingencyIndex >= 0) { // N-1
                    contingencyId = factor.getContingencyContext().getContingencyId();
                } else {
                    contingencyId = null; // N
                }

                SensitivitiesByBranch branchContingencies = sensitivitiesByBranches.computeIfAbsent(functionId, k -> new SensitivitiesByBranch(functionId, new HashMap<>()));
                Map<String, Double> generatorsSensitivities = branchContingencies.getSensitivitiesByContingency().computeIfAbsent(contingencyId, k -> new HashMap<>());
                generatorsSensitivities.put(variableId, sensitivityValue.getValue());
            }
        }
        return sensitivitiesByBranches;
    }

    private void updateMonitoredBranchResultWithCappingsInfos(Network network, Map<String, GeneratorCapping> generatorCappings,
                                                              MonitoredBranchDetailResult monitoredBranchDetailResult) {
        Map<EnergySource, Double> cappingsByEnergySource = new EnumMap<>(EnergySource.class);
        for (Map.Entry<String, GeneratorCapping> entry : generatorCappings.entrySet()) {
            Generator generator = network.getGenerator(entry.getKey());
            GeneratorCapping capping = !monitoredBranchDetailResult.getGeneratorsCapping().containsKey(entry.getKey())
                ? entry.getValue() : monitoredBranchDetailResult.getGeneratorsCapping().get(entry.getKey());
            capping.setCumulatedCapping(capping.getCumulatedCapping() + entry.getValue().getCapping());
            monitoredBranchDetailResult.getGeneratorsCapping().put(entry.getKey(), capping);
            cappingsByEnergySource.putIfAbsent(generator.getEnergySource(), 0.);
            cappingsByEnergySource.put(generator.getEnergySource(), cappingsByEnergySource.get(generator.getEnergySource()) + capping.getCumulatedCapping());
        }
        monitoredBranchDetailResult.getCappingByEnergySource().putAll(cappingsByEnergySource);
        monitoredBranchDetailResult.setOverallCapping(cappingsByEnergySource.values().stream().mapToDouble(d -> d).sum());
    }

    private Pair<String, Double> computeMaxVariationForAllMonitoredBranches(Network network,
                                                                            NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                                                            SensitivityAnalysisResult sensiResult,
                                                                            Map<String, Map<String, Boolean>> mapEncounteredBranchesByContingency,
                                                                            Map<String, SensitivitiesByBranch> sensitivitiesByBranches,
                                                                            StageDetailResult stageDetailResult,
                                                                            Map<String, Double> maxGeneratorsCappings,
                                                                            Reporter reporter) {
        double maxVariationForMonitoredBranch = -Double.MAX_VALUE;
        String maxMonitoredBranchId = null;

        for (SensitivityValue sensitivityValue : sensiResult.getValues()) {
            int factorIndex = sensitivityValue.getFactorIndex();
            SensitivityFactor factor = sensiResult.getFactors().get(factorIndex);
            String functionId = factor.getFunctionId(); // the function id here is a branch id
            int contingencyIndex = sensitivityValue.getContingencyIndex();
            String contingencyId = null;  // N
            if (contingencyIndex >= 0) { // N-1
                contingencyId = factor.getContingencyContext().getContingencyId();
            }

            Map<String, Boolean> mapEncounteredBranches = mapEncounteredBranchesByContingency.computeIfAbsent(contingencyId, k -> new HashMap<>());
            mapEncounteredBranches.putIfAbsent(functionId, Boolean.FALSE);

            // create or get result associated with the contingency in the stage detail result
            ContingencyStageDetailResult contingencyStageDetailResult = stageDetailResult.getResultsByContingency().computeIfAbsent(contingencyId, k -> new ContingencyStageDetailResult());

            // get monitored branch thresholds information from monitored branch id
            MonitoredBranchThreshold monitoredBranchThreshold = nonEvacuatedEnergyInputs.getBranchesThresholds().get(functionId);
            if (monitoredBranchThreshold != null) {
                // create or get result associated to the monitored branch
                MonitoredBranchDetailResult monitoredBranchDetailResult = contingencyStageDetailResult.getResultsByMonitoredBranch().computeIfAbsent(functionId, k -> new MonitoredBranchDetailResult());

                if (factor.isVariableSet()) {
                    monitoredBranchDetailResult.setP(sensitivityValue.getFunctionReference());
                }

                if (Boolean.FALSE.equals(mapEncounteredBranches.get(functionId))) {  // <branch, contingency> not already handled
                    // compute the variation for the monitored branch
                    SensitivitiesByBranch sensitivitiesByBranch = sensitivitiesByBranches.get(functionId);
                    Map<String, Double> generatorsSensitivities = sensitivitiesByBranch.getSensitivitiesByContingency().get(contingencyId);
                    Map<String, GeneratorCapping> generatorCappings = new HashMap<>();
                    double variation = computeVariationForMonitoredBranch(network, nonEvacuatedEnergyInputs, factor, monitoredBranchThreshold,
                        contingencyId, generatorsSensitivities, sensitivityValue, generatorCappings, monitoredBranchDetailResult, reporter);

                    // update monitored branch result with the cappings information
                    updateMonitoredBranchResultWithCappingsInfos(network, generatorCappings, monitoredBranchDetailResult);

                    // keeping only the max computed variation
                    if (!Double.isNaN(variation) && maxVariationForMonitoredBranch < variation) {
                        maxVariationForMonitoredBranch = variation;
                        maxMonitoredBranchId = monitoredBranchThreshold.getBranch().getId();
                        maxGeneratorsCappings.putAll(generatorCappings.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().getCapping())));
                    }
                    mapEncounteredBranches.put(functionId, Boolean.TRUE);
                }
            }
        }
        return Pair.of(maxMonitoredBranchId, maxVariationForMonitoredBranch);
    }

    private boolean analyzeSensitivityResults(Network network,
                                              NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                              SensitivityAnalysisResult sensiResult,
                                              Map<String, Double> generatorsCappings,
                                              StageDetailResult stageDetailResult,
                                              Reporter reporter) {
        AtomicBoolean noMoreLimitViolation = new AtomicBoolean(true);

        Map<String, Double> maxGeneratorsCappings = new HashMap<>();

        // collect, for each branch and then for each contingency, the sensitivity values for all generators who have an impact on the monitored branch
        Map<String, SensitivitiesByBranch> sensitivitiesByBranches = collectSensitivitiesByBranches(sensiResult);

        // loop on all sensitivity result values of the sensitivity analysis computation
        // each sensitivity value will contain a sensitivity factor, a contingency and the delta and reference value for a monitored branch
        Map<String, Map<String, Boolean>> mapEncounteredBranchesByContingency = new HashMap<>();

        Pair<String, Double> maxVariationForMonitoredBranch = computeMaxVariationForAllMonitoredBranches(network, nonEvacuatedEnergyInputs,
            sensiResult, mapEncounteredBranchesByContingency, sensitivitiesByBranches,
            stageDetailResult, maxGeneratorsCappings, reporter);

        if (maxVariationForMonitoredBranch.getValue() < EPSILON_MAX_VARIATION) {
            // the max variation for all monitored branches is small :
            // there is no limit violation detected and no further sensitivity analysis computation will be done for the current stage
            noMoreLimitViolation.set(true);
        } else {
            // the max variation for all monitored branches is not small :
            // there is a limit violation detected and a further sensitivity analysis computation will be done for the current stage, except if max iteration is reached
            // we memorize the generators cappings needed to eliminate this limit violation
            reporter.report(Report.builder()
                .withKey("MaxVariationForMonitoredBranchToConsider")
                .withDefaultMessage("The maximum variation has been found for monitored branch ${branchId}")
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .withValue("branchId", maxVariationForMonitoredBranch.getKey())
                .build());

            generatorsCappings.putAll(maxGeneratorsCappings);
            noMoreLimitViolation.set(false);
        }

        return noMoreLimitViolation.get();
    }

    private void applyGeneratorsCappings(Network network, Map<String, Double> generatorsCappings, Reporter reporter) {
        for (Map.Entry<String, Double> g : generatorsCappings.entrySet()) {
            Generator generator = network.getGenerator(g.getKey());
            if (generator != null) {
                // we use the capping value to reduce the generator targetP
                double oldTargetP = generator.getTargetP();
                double cappingValue = g.getValue();
                double newTargetP = generator.getTargetP() - cappingValue;
                generator.setTargetP(newTargetP);
                reporter.report(Report.builder()
                    .withKey("ApplyGeneratorCapping")
                    .withDefaultMessage("Capping generator ${generatorId} using capping value ${cappingValue} : targetP ${oldTargetP} --> ${newTargetP}")
                    .withSeverity(TypedValue.TRACE_SEVERITY)
                    .withValue("generatorId", generator.getId())
                    .withValue("oldTargetP", oldTargetP)
                    .withValue("cappingValue", cappingValue)
                    .withValue("newTargetP", newTargetP)
                    .build());
            }
        }
    }

    private void buildSummaryMonitoredBranchResults(Map.Entry<String, MonitoredBranchDetailResult> monitoredBranchResultEntry,
                                                    Double pInitTotal,
                                                    String contingencyName,
                                                    StageSummaryResult stageSummaryResult,
                                                    StageSummaryContingencyResult stageSummaryContingencyResult) {
        String monitoredBranchId = monitoredBranchResultEntry.getKey();
        MonitoredBranchDetailResult monitoredBranchDetailResult = monitoredBranchResultEntry.getValue();

        Double deltaPEchTotal = monitoredBranchDetailResult.getCappingByEnergySource().values().stream().mapToDouble(d -> d).sum();
        if (deltaPEchTotal > stageSummaryContingencyResult.getPLim()) {
            stageSummaryContingencyResult.setLimitViolated(true);
            stageSummaryContingencyResult.setPLim(pInitTotal - deltaPEchTotal);
            stageSummaryContingencyResult.setPercentOverload(monitoredBranchDetailResult.getPercentOverload());
            stageSummaryContingencyResult.setMonitoredEquipmentWithMaxLimit(monitoredBranchId);
            stageSummaryContingencyResult.setMonitoredEquipmentPower(monitoredBranchDetailResult.getP());
            stageSummaryContingencyResult.setCapping(monitoredBranchDetailResult.getOverallCapping());
        }

        Double pLim = contingencyName == null ? stageSummaryResult.getPLimN() : stageSummaryResult.getPLimNm1();
        if (deltaPEchTotal > pLim) {
            stageSummaryResult.setPLimMonitoredEquipment(monitoredBranchId);
            stageSummaryResult.setContingencyId(contingencyName);
            stageSummaryResult.setPLimLimit(monitoredBranchDetailResult.getLimitName());
            stageSummaryResult.setPercentOverload(monitoredBranchDetailResult.getPercentOverload());
            stageSummaryResult.setPLimN(deltaPEchTotal);
        }
    }

    private void buildSummaryContingencyResults(Map.Entry<String, ContingencyStageDetailResult> contingencyResultEntry,
                                                Double pInitTotal,
                                                StageSummaryResult stageSummaryResult) {
        String contingencyName = contingencyResultEntry.getKey();  // null if N
        ContingencyStageDetailResult contingencyResult = contingencyResultEntry.getValue();

        StageSummaryContingencyResult stageSummaryContingencyResult = new StageSummaryContingencyResult();
        stageSummaryContingencyResult.setLimitViolated(false);
        stageSummaryContingencyResult.setPLim(0.);

        // for each monitored branch in contingency result
        contingencyResult.getResultsByMonitoredBranch().entrySet().forEach(entry -> buildSummaryMonitoredBranchResults(entry, pInitTotal, contingencyName, stageSummaryResult, stageSummaryContingencyResult));

        stageSummaryResult.getResultsByContingency().put(contingencyName, stageSummaryContingencyResult);
    }

    private void buildSummaryStageResults(NonEvacuatedEnergyResults nonEvacuatedEnergyResults,
                                          Map.Entry<String, StageDetailResult> stageResultEntry) {
        String stageName = stageResultEntry.getKey();
        StageDetailResult stageDetailResult = stageResultEntry.getValue();

        StageSummaryResult stageSummaryResult = new StageSummaryResult();
        stageSummaryResult.setPLimN(0.);
        stageSummaryResult.setPLimNm1(0.);

        nonEvacuatedEnergyResults.getStagesSummary().put(stageName, stageSummaryResult);
        stageSummaryResult.setPInitByEnergySource(stageDetailResult.getPInitByEnergySource());
        Double pInitTotal = stageDetailResult.getPInitByEnergySource().values().stream().mapToDouble(d -> d).sum();

        // for each contingency results
        stageDetailResult.getResultsByContingency().entrySet().forEach(entry -> buildSummaryContingencyResults(entry, pInitTotal, stageSummaryResult));
    }

    private void buildSummaryResults(NonEvacuatedEnergyResults nonEvacuatedEnergyResults) {
        // for each stage detail result
        nonEvacuatedEnergyResults.getStagesDetail().entrySet().forEach(entry -> buildSummaryStageResults(nonEvacuatedEnergyResults, entry));
    }

    private void computeCappingsGeneratorsInitialP(Network network, NonEvacuatedEnergyRunContext context) {
        // memorize the initial targetP for the capping generators
        // compute and memorize the initial overall targetP for the capping generators by energy source
        context.getNonEvacuatedEnergyInputs().getGeneratorsPInit().clear();
        context.getNonEvacuatedEnergyInputs().getGeneratorsPInitByEnergySource().clear();

        Map<EnergySource, AtomicDouble> pInitByEnergySource = new EnumMap<>(EnergySource.class);
        context.getNonEvacuatedEnergyInputs().getCappingsGenerators().forEach((key, value) -> value.forEach(generatorId -> {
            Generator generator = network.getGenerator(generatorId);
            if (generator != null) {
                double targetP = generator.getTargetP();
                context.getNonEvacuatedEnergyInputs().getGeneratorsPInit().put(generatorId, targetP);
                AtomicDouble sum = pInitByEnergySource.computeIfAbsent(generator.getEnergySource(), k -> new AtomicDouble(0));
                sum.addAndGet(generator.getTargetP());
            }
        }));
        pInitByEnergySource.forEach((key, value) -> context.getNonEvacuatedEnergyInputs().getGeneratorsPInitByEnergySource().put(key, value.get()));
    }

    private String run(NonEvacuatedEnergyRunContext context,
                       Network network,
                       SensitivityAnalysisParameters sensitivityAnalysisParameters,
                       SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                       ComputationManager computationManager,
                       Reporter reporter) {
        String result = null;

        // build the contingencies, variable sets and sensitivity factors used as input of the sensitivity analysis computation
        nonEvacuatedEnergyInputBuilderService.build(context, network, reporter);

        List<NonEvacuatedEnergyStagesSelection> stages = context.getNonEvacuatedEnergyInputData().getNonEvacuatedEnergyStagesSelection();
        int stageIndex = 0;

        // create global result
        NonEvacuatedEnergyResults nonEvacuatedEnergyResults = new NonEvacuatedEnergyResults();

        // Loop on all generation stages
        int iStage = 1;
        for (NonEvacuatedEnergyStagesSelection stageSelection : stages) {
            Reporter subReporter = reporter.createSubReporter("Stage" + iStage, "Stage " + " (${stageName})", "stageName", stageSelection.getName());

            if (!stageSelection.isActivated()) {  // stage is not activated : we ignore it
                continue;
            }

            // create new network variant
            String stageVariantId = context.getNetworkUuid() + "_stage_" + stageIndex++;
            cloneNetworkVariant(network, context.getVariantId(), stageVariantId);

            try {
                // create stage detail result
                StageDetailResult stageDetailResult = new StageDetailResult();
                nonEvacuatedEnergyResults.getStagesDetail().put(stageSelection.getName(), stageDetailResult);

                // apply the generation stage before launching the sensitivity analysis
                applyStage(network, stageSelection, context, subReporter);

                // compute the initial overall targetP for the capping generators by energy source
                computeCappingsGeneratorsInitialP(network, context);
                stageDetailResult.setPInitByEnergySource(context.getNonEvacuatedEnergyInputs().getGeneratorsPInitByEnergySource());

                int iterationCount = 1;
                boolean noMoreLimitViolation = true;

                // do sensitivity computation until no more limit violation detected on a monitored branch or max iteration reached
                do {
                    // launch sensitivity analysis
                    CompletableFuture<SensitivityAnalysisResult> future = sensitivityAnalysisRunner.runAsync(
                        network,
                        stageVariantId,
                        context.getNonEvacuatedEnergyInputs().getFactors(),
                        context.getNonEvacuatedEnergyInputs().getContingencies(),
                        context.getNonEvacuatedEnergyInputs().getVariablesSets(),
                        sensitivityAnalysisParameters,
                        computationManager,
                        subReporter);

                    SensitivityAnalysisResult sensiResult = future == null ? null : future.get();

                    // generators cappings needed to eliminate on eventually limit violation on a monitored branch
                    Map<String, Double> generatorsCappings = new HashMap<>();

                    Reporter analyzeReporter = subReporter.createSubReporter("Analyzing results" + iterationCount, "Analyzing results");

                    if (sensiResult != null) {
                        // analyze sensi results and generate the generators cappings
                        noMoreLimitViolation = analyzeSensitivityResults(network, context.getNonEvacuatedEnergyInputs(), sensiResult, generatorsCappings, stageDetailResult, analyzeReporter);
                    }
                    if (!noMoreLimitViolation) {  // there is a limit violation on a monitored branch
                        // apply the generators cappings calculated just above to eliminate this limit violation
                        applyGeneratorsCappings(network, generatorsCappings, analyzeReporter);
                    }

                    iterationCount++;
                } while (iterationCount <= MAX_ITERATION_IN_STAGE && !noMoreLimitViolation);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null) {
                    msg = e.getClass().getName();
                }
                subReporter.report(Report.builder()
                    .withKey("sensitivityNonEvacuatedEnergyFailure")
                    .withDefaultMessage("Failure while running non evacuated energy computation exception : ${exception}")
                    .withSeverity(TypedValue.ERROR_SEVERITY)
                    .withValue("exception", msg)
                    .build());
            } finally {
                // remove network variant
                network.getVariantManager().removeVariant(stageVariantId);
            }
            iStage++;
        }

        // Build the non evacuated energy stage summary results from all stage detail results calculated
        buildSummaryResults(nonEvacuatedEnergyResults);

        try {
            result = objectMapper.writeValueAsString(nonEvacuatedEnergyResults);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.toString());
        }

        return result;
    }

    private CompletableFuture<String> runAsync(NonEvacuatedEnergyRunContext context,
                                               SensitivityAnalysis.Runner sensitivityAnalysisRunner,
                                               Reporter reporter,
                                               UUID resultUuid) {
        lockRunAndCancel.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }
            SensitivityAnalysisParameters sensitivityAnalysisParameters = buildParameters(context);

            if (sensitivityAnalysisParameters.getLoadFlowParameters().isDc()) {
                // loadflow in dc mode not allowed
                reporter.report(Report.builder()
                    .withKey("NonEvacuatedEnergyLoadFlowDcNotAllowed")
                    .withDefaultMessage("Loadflow in DC mode not allowed !!")
                    .withSeverity(TypedValue.ERROR_SEVERITY)
                    .build());
                throw new PowsyblException("Loadflow in DC mode not allowed !!");
            }

            Network network = getNetwork(context.getNetworkUuid(), context.getVariantId());
            ComputationManager computationManager = sensitivityAnalysisExecutionService.getLocalComputationManager();

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                run(context, network, sensitivityAnalysisParameters, sensitivityAnalysisRunner, computationManager, reporter)
            );

            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancel.unlock();
        }
    }

    private void cancelAsync(SensitivityAnalysisCancelContext cancelContext) {
        lockRunAndCancel.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<String> future = futures.get(cancelContext.getResultUuid());
            if (future != null) {
                future.cancel(true);  // cancel computation in progress
            }
            cleanResultsAndPublishCancel(cancelContext.getResultUuid(), cancelContext.getReceiver());
        } finally {
            lockRunAndCancel.unlock();
        }
    }

    //@Override // TODO reoverride
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    private void cleanResultsAndPublishCancel(UUID resultUuid, String receiver) {
        nonEvacuatedEnergyRepository.delete(resultUuid);
        notificationService.publishStop(resultUuid, receiver, getComputationType());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("{} (resultUuid='{}')",
                    NotificationService.getCancelMessage(getComputationType()),
                    resultUuid);
        }
    }

    @Bean
    public Consumer<Message<String>> consumeNonEvacuatedEnergyRun() {
        return message -> {
            NonEvacuatedEnergyResultContext nonEvacuatedEnergyResultContext = NonEvacuatedEnergyResultContext.fromMessage(message, objectMapper);
            try {
                runRequests.add(nonEvacuatedEnergyResultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();

                startTime.set(System.nanoTime());
                String result = run(nonEvacuatedEnergyResultContext.getRunContext(), nonEvacuatedEnergyResultContext.getResultUuid());
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                nonEvacuatedEnergyRepository.insert(nonEvacuatedEnergyResultContext.getResultUuid(), result, NonEvacuatedEnergyStatus.COMPLETED.name());
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage(nonEvacuatedEnergyResultContext.getResultUuid(), nonEvacuatedEnergyResultContext.getRunContext().getReceiver());
                    LOGGER.info("Non evacuated energy complete (resultUuid='{}')", nonEvacuatedEnergyResultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(nonEvacuatedEnergyResultContext.getResultUuid()) != null) {
                        cleanResultsAndPublishCancel(nonEvacuatedEnergyResultContext.getResultUuid(), cancelComputationRequests.get(nonEvacuatedEnergyResultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception | OutOfMemoryError e) {
                if (!(e instanceof CancellationException)) {
                    LOGGER.error(NotificationService.getFailedMessage(getComputationType()), e);
                    notificationService.publishFail(
                            nonEvacuatedEnergyResultContext.getResultUuid(),
                            nonEvacuatedEnergyResultContext.getRunContext().getReceiver(),
                            e.getMessage(), nonEvacuatedEnergyResultContext.getRunContext().getUserId(),
                            getComputationType());
                    nonEvacuatedEnergyRepository.delete(nonEvacuatedEnergyResultContext.getResultUuid());
                }
            } finally {
                futures.remove(nonEvacuatedEnergyResultContext.getResultUuid());
                cancelComputationRequests.remove(nonEvacuatedEnergyResultContext.getResultUuid());
                runRequests.remove(nonEvacuatedEnergyResultContext.getResultUuid());
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeNonEvacuatedEnergyCancel() {
        return message -> cancelAsync(SensitivityAnalysisCancelContext.fromMessage(message));
    }
}

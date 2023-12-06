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
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.CurrentLimits;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityValue;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.sensitivityanalysis.server.dto.EquipmentsContainer;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStageDefinition;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStagesSelection;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.ContingencyStageDetailResult;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.GeneratorCapping;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.MonitoredBranchDetailResult;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.NonEvacuatedEnergyResults;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.StageDetailResult;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.StageSummaryContingencyResult;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.StageSummaryResult;
import org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy.NonEvacuatedEnergyRepository;
import org.gridsuite.sensitivityanalysis.server.service.NotificationService;
import org.gridsuite.sensitivityanalysis.server.service.ReportService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Math.abs;
import static java.util.stream.Collectors.toMap;
import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NonEvacuatedEnergyWorkerService.class);

    private static final String NON_EVACUATED_ENERGY_TYPE_REPORT = "NonEvacuatedEnergy";

    private final NetworkStoreService networkStoreService;

    private final ReportService reportService;

    private final NonEvacuatedEnergyRepository nonEvacuatedEnergyRepository;

    private final ObjectMapper objectMapper;

    private final Map<UUID, CompletableFuture<String>> futures = new ConcurrentHashMap<>();

    private final Map<UUID, SensitivityAnalysisCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private final Set<UUID> runRequests = Sets.newConcurrentHashSet();

    private final Lock lockRunAndCancel = new ReentrantLock();

    private final NotificationService notificationService;

    private final SensitivityAnalysisExecutionService sensitivityAnalysisExecutionService;

    private final NonEvacuatedEnergyInputBuilderService nonEvacuatedEnergyInputBuilderService;

    private Function<String, SensitivityAnalysis.Runner> sensitivityAnalysisFactorySupplier;

    private static final double EPSILON = 0.0001;

    private static final int MAX_ITERATION_IN_STAGE = 4;

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

    public NonEvacuatedEnergyWorkerService(NetworkStoreService networkStoreService, ReportService reportService, NotificationService notificationService,
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
            String rootReporterId = context.getReporterId() == null ? NON_EVACUATED_ENERGY_TYPE_REPORT : context.getReporterId() + "@" + NON_EVACUATED_ENERGY_TYPE_REPORT;
            rootReporter = new ReporterModel(rootReporterId, rootReporterId);
            reporter = rootReporter.createSubReporter(NON_EVACUATED_ENERGY_TYPE_REPORT, NON_EVACUATED_ENERGY_TYPE_REPORT + " (${providerToUse})", "providerToUse", sensitivityAnalysisRunner.getName());
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
        SensitivityAnalysisParameters params = context.getInputData().getParameters() == null ?
            new SensitivityAnalysisParameters() : context.getInputData().getParameters();

        // set the flowFlowThreshold value
        params.setFlowFlowSensitivityValueThreshold(context.getInputData().getNonEvacuatedEnergyGeneratorsLimit().getSensitivityThreshold());

        if (context.getInputData().getLoadFlowSpecificParameters() == null
                || context.getInputData().getLoadFlowSpecificParameters().isEmpty()) {
            return params; // no specific LF params
        }
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
                .filter(p -> p.getName().equals(context.getProvider()))
                .findFirst().orElseThrow(() -> new PowsyblException("Load flow provider not found " + context.getProvider()));
        Extension<LoadFlowParameters> extension = lfProvider.loadSpecificParameters(context.getInputData().getLoadFlowSpecificParameters())
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
        for (int i = 0; i < stageSelection.getStagesDefinitonIndex().size(); ++i) {
            int stageDefinitionIndex = stageSelection.getStagesDefinitonIndex().get(i);
            NonEvacuatedEnergyStageDefinition stageDefinition = context.getInputData().getNonEvacuatedEnergyStagesDefinition().get(stageDefinitionIndex);
            float pMaxPercent = stageDefinition.getPMaxPercents().get(stageSelection.getPMaxPercentsIndex().get(i));
            List<EquipmentsContainer> generatorsFilters = stageDefinition.getGenerators();

            // get the generators id from the filters
            List<IdentifiableAttributes> generators = new ArrayList<>();
            generatorsFilters.forEach(generatorFilter -> {
                List<IdentifiableAttributes> identifiables = nonEvacuatedEnergyInputBuilderService
                    .getIdentifiablesFromContainer(context.getNetworkUuid(), context.getVariantId(), generatorFilter, List.of(IdentifiableType.GENERATOR), reporter).toList();
                generators.addAll(identifiables);
            });

            // for each generator, set targetP to a percent of maxP
            generators.forEach(identifiableAttributes -> {
                Generator generator = network.getGenerator(identifiableAttributes.getId());
                if (generator != null) {
                    generator.setTargetP((generator.getMaxP() * pMaxPercent) / 100);
                }
            });
        }
    }

    private double getLimitValueFromFactorAndSensitivityValue(SensitivityValue sensitivityValue,
                                                              SensitivityFactor factor,
                                                              MonitoredBranchThreshold monitoredBranchThreshold,
                                                              MonitoredBranchDetailResult monitoredBranchDetailResult) {
        double limitValue = Double.NaN;
        String limitName = null;

        int contingencyIndex = sensitivityValue.getContingencyIndex();

        Branch<?> branch = monitoredBranchThreshold.getBranch();
        Optional<CurrentLimits> currentLimits1 = branch.getCurrentLimits1();
        Optional<CurrentLimits> currentLimits2 = branch.getCurrentLimits2();

        // use contingencyIndex (-1 or not) to check istN/Nlimitname or istNm1/Nm1Limitname
        // limits in iidm are in A, so consider only SensitivityFunctionType BRANCH_CURRENT to return
        // the limit value multiplied by the input coefficient (nCoeff/nm1Coeff)
        if (contingencyIndex < 0) { // N
            if (monitoredBranchThreshold.isIstN()) {
                limitName = "IST";
                if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    limitValue = currentLimits1.map(l -> l.getPermanentLimit() * monitoredBranchThreshold.getNCoeff() / 100).orElse(Double.NaN);
                } else if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_2) {
                    limitValue = currentLimits2.map(l -> l.getPermanentLimit() * monitoredBranchThreshold.getNCoeff() / 100).orElse(Double.NaN);
                }
            } else if (!StringUtils.isEmpty(monitoredBranchThreshold.getNLimitName())) {
                limitName = monitoredBranchThreshold.getNLimitName();
                if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    Optional<LoadingLimits.TemporaryLimit> limit = currentLimits1.flatMap(currentLimits -> currentLimits.getTemporaryLimits().stream().filter(l -> l.getName().equals(monitoredBranchThreshold.getNLimitName())).findFirst());
                    limitValue = limit.map(temporaryLimit -> temporaryLimit.getValue() * monitoredBranchThreshold.getNCoeff() / 100).orElse(Double.NaN);
                } else if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_2) {
                    Optional<LoadingLimits.TemporaryLimit> limit = currentLimits2.flatMap(currentLimits -> currentLimits.getTemporaryLimits().stream().filter(l -> l.getName().equals(monitoredBranchThreshold.getNLimitName())).findFirst());
                    limitValue = limit.map(temporaryLimit -> temporaryLimit.getValue() * monitoredBranchThreshold.getNCoeff() / 100).orElse(Double.NaN);
                }
            }
        } else {  // N-1
            if (monitoredBranchThreshold.isIstNm1()) {
                limitName = "IST";
                if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    limitValue = currentLimits1.map(l -> l.getPermanentLimit() * monitoredBranchThreshold.getNm1Coeff() / 100).orElse(Double.NaN);
                } else if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_2) {
                    limitValue = currentLimits2.map(l -> l.getPermanentLimit() * monitoredBranchThreshold.getNm1Coeff() / 100).orElse(Double.NaN);
                }
            } else if (!StringUtils.isEmpty(monitoredBranchThreshold.getNm1LimitName())) {
                limitName = monitoredBranchThreshold.getNm1LimitName();
                if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_1) {
                    Optional<LoadingLimits.TemporaryLimit> limit = currentLimits1.flatMap(currentLimits -> currentLimits.getTemporaryLimits().stream().filter(l -> l.getName().equals(monitoredBranchThreshold.getNm1LimitName())).findFirst());
                    limitValue = limit.map(temporaryLimit -> temporaryLimit.getValue() * monitoredBranchThreshold.getNm1Coeff() / 100).orElse(Double.NaN);
                } else if (factor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT_2) {
                    Optional<LoadingLimits.TemporaryLimit> limit = currentLimits2.flatMap(currentLimits -> currentLimits.getTemporaryLimits().stream().filter(l -> l.getName().equals(monitoredBranchThreshold.getNm1LimitName())).findFirst());
                    limitValue = limit.map(temporaryLimit -> temporaryLimit.getValue() * monitoredBranchThreshold.getNm1Coeff() / 100).orElse(Double.NaN);
                }
            }
        }

        // update monitored branch detail result
        if (!Double.isNaN(limitValue)) {  // sensitivity in A/MW
            monitoredBranchDetailResult.setIntensity(sensitivityValue.getFunctionReference());
            monitoredBranchDetailResult.setLimitName(limitName);
            monitoredBranchDetailResult.setLimitValue(limitValue);
            monitoredBranchDetailResult.setPercentOverload((sensitivityValue.getFunctionReference() / limitValue) * 100.);
        } else {  // sensitivity in MW/MW
            monitoredBranchDetailResult.setP(sensitivityValue.getFunctionReference());
        }

        // limit value is set only for sensitivity in A/MW : it will be NaN for sensitivity in MW/MW
        return limitValue;
    }

    private double computeVariationForMonitoredBranch(Network network,
                                                      NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                                      SensitivityFactor factor,
                                                      MonitoredBranchThreshold monitoredBranchThreshold,
                                                      Map<String, Double> generatorsSensitivities,
                                                      SensitivityValue sensitivityValue,
                                                      Map<String, Double> generatorsCappingsForMonitoredBranch,
                                                      MonitoredBranchDetailResult monitoredBranchDetailResult) {
        double injectionVariation = Double.NaN;
        double functionReference = sensitivityValue.getFunctionReference();

        // get the limit value (in A) to consider from the monitored branch limits, using the factor function type and the monitored branch thresholds parameters
        double limitValue = getLimitValueFromFactorAndSensitivityValue(sensitivityValue, factor, monitoredBranchThreshold, monitoredBranchDetailResult);

        if (!Double.isNaN(limitValue)) {
            // compare the functionReference (monitored branch intensity in A) to the limit value (in A)
            double delta = limitValue - functionReference;
            if (delta < 0) {
                // monitored branch is over the limit :
                // we compute the generators cappings needed to set the monitored branch under the limit
                injectionVariation = computeInjectionVariationForMonitoredBranch(network, nonEvacuatedEnergyInputs, generatorsSensitivities, delta, generatorsCappingsForMonitoredBranch, monitoredBranchDetailResult);
            }
        }

        return injectionVariation;
    }

    private double computeInjectionVariationForMonitoredBranch(Network network,
                                                               NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                                               Map<String, Double> generatorsSensitivities,
                                                               double delta,
                                                               Map<String, Double> generatorsCappingsForMonitoredBranch,
                                                               MonitoredBranchDetailResult monitoredBranchDetailResult) {
        // sort the map in reverse order to get first the generator which have the most impact
        LinkedHashMap<String, Double> sensitivities = generatorsSensitivities.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        // define sensitivity coefficient for each generator
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

        // compute the generators cappings needed to set the monitored branch under the limit
        Map<EnergySource, Double> variationsByEnergySource = new EnumMap<>(EnergySource.class);
        Map<String, GeneratorCapping> generatorCappings = new HashMap<>();
        double injectionVariation = computeInjectionVariationFromGeneratorsSensitivities(network, nonEvacuatedEnergyInputs, sensitivities, sensitivityCoeff, delta, variationsByEnergySource, generatorCappings);

        // update monitored branch result
        monitoredBranchDetailResult.getCappingByEnergySource().putAll(variationsByEnergySource);
        monitoredBranchDetailResult.setOverallCapping(variationsByEnergySource.values().stream().mapToDouble(d -> d).sum());
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
        monitoredBranchDetailResult.getGeneratorsCapping().putAll(generatorCappings);

        generatorsCappingsForMonitoredBranch.putAll(generatorCappings.entrySet()
            .stream().collect(toMap(Map.Entry::getKey, e -> e.getValue().getCapping())));

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
        //double referenceGeneratorVariation = abs(100 * delta / coeffSum); ????
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
            generatorCappings.put(generatorId, generatorCapping);
            variationsByEnergySource.put(generator.getEnergySource(), generatorVariation);
        }

        // there is a remaining, and we still have generators to dispatch
        if (remaining >= EPSILON && !generatorsToDispatch.isEmpty()) {
            boolean finished = false;
            int i = 0;

            // loop until no more remaining or no more generators to dispatch or max iteration reached
            while (!finished) {
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

                if (remaining < EPSILON || generatorsToDispatch.isEmpty() || i > 10) {
                    finished = true;
                }
            }
        }

        return injectionVariation;
    }

    private boolean analyzeSensitivityResults(Network network,
                                              NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs,
                                              SensitivityAnalysisResult sensiResult,
                                              Map<String, Double> generatorsCappings,
                                              StageDetailResult stageDetailResult) {
        AtomicBoolean noMoreLimitViolation = new AtomicBoolean(true);

        Map<String, Double> maxGeneratorsCappings = new HashMap<>();
        double maxVariationForMonitoredBranch = -Double.MAX_VALUE;

        // collect, for each branch and then for each contingency, the sensitivity values for all generators who have an impact on the monitored branch
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

        // loop on all sensitivity result values of the sensitivity analysis computation
        // each sensitivity value will contain a sensitivity factor, a contingency and the delta and reference value for a monitored branch
        for (SensitivityValue sensitivityValue : sensiResult.getValues()) {
            int factorIndex = sensitivityValue.getFactorIndex();
            SensitivityFactor factor = sensiResult.getFactors().get(factorIndex);
            String functionId = factor.getFunctionId(); // the function id here is a branch id
            int contingencyIndex = sensitivityValue.getContingencyIndex();
            String contingencyId = null;  // N
            if (contingencyIndex >= 0) { // N-1
                contingencyId = factor.getContingencyContext().getContingencyId();
            }
            // create result associated with the contingency in the stage detail result
            ContingencyStageDetailResult contingencyStageDetailResult = stageDetailResult.getResultsbyContingency().computeIfAbsent(contingencyId, k -> new ContingencyStageDetailResult());

            // get monitored branch thresholds information from monitored branch id
            MonitoredBranchThreshold monitoredBranchThreshold = nonEvacuatedEnergyInputs.getBranchesThresholds().get(functionId);
            if (monitoredBranchThreshold != null) {
                // create the generators cappings data structure for the monitored branch
                Map<String, Double> generatorsCappingsForMonitoredBranch = new HashMap<>();

                // create result associated to the monitored branch
                MonitoredBranchDetailResult monitoredBranchDetailResult = contingencyStageDetailResult.getResultsbyMonitoredBranch().computeIfAbsent(functionId, k -> new MonitoredBranchDetailResult());

                // compute the variation for the monitored branch
                SensitivitiesByBranch sensitivitiesByBranch = sensitivitiesByBranches.get(functionId);
                Map<String, Double> generatorsSensitivities = sensitivitiesByBranch.getSensitivitiesByContingency().get(contingencyId);
                double variation = computeVariationForMonitoredBranch(network, nonEvacuatedEnergyInputs, factor, monitoredBranchThreshold, generatorsSensitivities, sensitivityValue, generatorsCappingsForMonitoredBranch, monitoredBranchDetailResult);

                // keeping only the max computed variation
                if (!Double.isNaN(variation) && maxVariationForMonitoredBranch < variation) {
                    maxVariationForMonitoredBranch = variation;
                    maxGeneratorsCappings = generatorsCappingsForMonitoredBranch;
                }
            }
        }

        if (maxVariationForMonitoredBranch < 0.3) {
            // the max varation for all monitored branches is small :
            // there is no limit violation detected and no further sensitivity analysis computation will be done for the current stage
            noMoreLimitViolation.set(true);
        } else {
            // the max varation for all monitored branches is not small :
            // there is a limit violation detected and a further sensitivity analysis computation will be done for the current stage, except if max iteration is reached
            // we memorize the generators cappings needed to eliminate this limit violation
            generatorsCappings.putAll(maxGeneratorsCappings);
            noMoreLimitViolation.set(false);
        }

        return noMoreLimitViolation.get();
    }

    private void applyGeneratorsCappings(Network network, Map<String, Double> generatorsCappings) {
        for (Map.Entry<String, Double> g : generatorsCappings.entrySet()) {
            Generator generator = network.getGenerator(g.getKey());
            if (generator != null) {
                // we use the capping value to reduce the generator targetP
                generator.setTargetP(generator.getTargetP() - g.getValue());
            }
        }
    }

    private void buildSummaryResults(NonEvacuatedEnergyResults nonEvacuatedEnergyResults) {
        // for each stage detail result
        for (Map.Entry<String, StageDetailResult> stageResultEntry : nonEvacuatedEnergyResults.getStagesDetail().entrySet()) {
            String stageName = stageResultEntry.getKey();
            StageDetailResult stageDetailResult = stageResultEntry.getValue();

            StageSummaryResult stageSummaryResult = new StageSummaryResult();
            stageSummaryResult.setPLimN(0.);
            stageSummaryResult.setPLimNm1(0.);

            nonEvacuatedEnergyResults.getStagesSummary().put(stageName, stageSummaryResult);
            stageSummaryResult.setPInitByEnergySource(stageDetailResult.getPInitByEnergySource());
            Double pInitTotal = stageDetailResult.getPInitByEnergySource().values().stream().mapToDouble(d -> d).sum();

            // for each contingency results
            for (Map.Entry<String, ContingencyStageDetailResult> contingencyResultEntry : stageDetailResult.getResultsbyContingency().entrySet()) {
                String contingencyName = contingencyResultEntry.getKey();  // null if N
                ContingencyStageDetailResult contingencyResult = contingencyResultEntry.getValue();

                StageSummaryContingencyResult stageSummaryContingencyResult = new StageSummaryContingencyResult();
                stageSummaryContingencyResult.setLimitViolated(false);
                stageSummaryContingencyResult.setPLim(0.);

                // for each monitored branch in contingency result
                for (Map.Entry<String, MonitoredBranchDetailResult> monitoredBranchResultEntry : contingencyResult.getResultsbyMonitoredBranch().entrySet()) {
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

                stageSummaryResult.getResultsByContingency().put(contingencyName, stageSummaryContingencyResult);
            }
        }
    }

    private void computeCappingsGeneratorsInitialP(Network network, NonEvacuatedEnergyRunContext context) {
        // memorize the initial targetP for the capping generators
        // compute and memorize the initial overall targetP for the capping generators by energy source
        context.getInputs().getGeneratorsPInit().clear();
        context.getInputs().getGeneratorsPInitByEnergySource().clear();

        Map<EnergySource, AtomicDouble> pInitByEnergySource = new EnumMap<>(EnergySource.class);
        context.getInputs().getCappingsGenerators().forEach((key, value) -> value.forEach(generatorId -> {
            Generator generator = network.getGenerator(generatorId);
            if (generator != null) {
                double targetP = generator.getTargetP();
                context.getInputs().getGeneratorsPInit().put(generatorId, targetP);
                AtomicDouble sum = pInitByEnergySource.computeIfAbsent(generator.getEnergySource(), k -> new AtomicDouble(0));
                sum.addAndGet(generator.getTargetP());
            }
        }));
        pInitByEnergySource.forEach((key, value) -> context.getInputs().getGeneratorsPInitByEnergySource().put(key, value.get()));
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

        List<NonEvacuatedEnergyStagesSelection> stages = context.getInputData().getNonEvacuatedEnergyStagesSelection();
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
                stageDetailResult.setPInitByEnergySource(context.getInputs().getGeneratorsPInitByEnergySource());

                int iterationCount = 1;
                boolean noMoreLimitViolation = true;

                // do sensitivity computation until no more limit violation detected on a monitored branch or max iteration reached
                do {
                    // launch sensitivity analysis
                    CompletableFuture<SensitivityAnalysisResult> future = sensitivityAnalysisRunner.runAsync(
                        network,
                        stageVariantId,
                        context.getInputs().getFactors(),
                        context.getInputs().getContingencies(),
                        context.getInputs().getVariablesSets(),
                        sensitivityAnalysisParameters,
                        computationManager,
                        subReporter);

                    SensitivityAnalysisResult sensiResult = future == null ? null : future.get();

                    // generators cappings needed to eliminate on eventually limit violation on a monitored branch
                    Map<String, Double> generatorsCappings = new HashMap<>();

                    if (sensiResult != null) {
                        // analyze sensi results and generate the generators cappings
                        noMoreLimitViolation = analyzeSensitivityResults(network, context.getInputs(), sensiResult, generatorsCappings, stageDetailResult);
                    }
                    if (!noMoreLimitViolation) {  // there is a limit violation on a monitored branch
                        // apply the generators cappings calculated just above to eliminate this limit violation
                        applyGeneratorsCappings(network, generatorsCappings);
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

    private void cleanResultsAndPublishCancel(UUID resultUuid, String receiver) {
        nonEvacuatedEnergyRepository.delete(resultUuid);
        notificationService.publishStop("publishNonEvacuatedEnergyStopped-out-0", resultUuid, receiver);
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

                nonEvacuatedEnergyRepository.insert(nonEvacuatedEnergyResultContext.getResultUuid(), result, SensitivityAnalysisStatus.COMPLETED.name());
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage("publishNonEvacuatedEnergyResult-out-0", nonEvacuatedEnergyResultContext.getResultUuid(), nonEvacuatedEnergyResultContext.getRunContext().getReceiver());
                    LOGGER.info("Non evacuated energy complete (resultUuid='{}')", nonEvacuatedEnergyResultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(nonEvacuatedEnergyResultContext.getResultUuid()) != null) {
                        cleanResultsAndPublishCancel(nonEvacuatedEnergyResultContext.getResultUuid(), cancelComputationRequests.get(nonEvacuatedEnergyResultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception | OutOfMemoryError e) {
                LOGGER.error(FAIL_MESSAGE, e);
                if (!(e instanceof CancellationException)) {
                    notificationService.publishFail("publishNonEvacuatedEnergyFailed-out-0", nonEvacuatedEnergyResultContext.getResultUuid(), nonEvacuatedEnergyResultContext.getRunContext().getReceiver(), e.getMessage());
                    nonEvacuatedEnergyRepository.delete(nonEvacuatedEnergyResultContext.getResultUuid());
                    nonEvacuatedEnergyRepository.insertStatus(List.of(nonEvacuatedEnergyResultContext.getResultUuid()), SensitivityAnalysisStatus.FAILED.name());
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

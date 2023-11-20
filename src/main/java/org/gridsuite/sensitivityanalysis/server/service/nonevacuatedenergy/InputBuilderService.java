/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.CurrentLimits;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.sensitivityanalysis.server.dto.EquipmentsContainer;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyContingencies;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyGeneratorLimitByType;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyMonitoredBranches;
import org.gridsuite.sensitivityanalysis.server.service.ActionsService;
import org.gridsuite.sensitivityanalysis.server.service.FilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class InputBuilderService {
    private static final String EXPECTED_TYPE = "expectedType";
    private static final Logger LOGGER = LoggerFactory.getLogger(InputBuilderService.class);
    private final ActionsService actionsService;
    private final FilterService filterService;

    public InputBuilderService(ActionsService actionsService, FilterService filterService) {
        this.actionsService = actionsService;
        this.filterService = filterService;
    }

    private List<Contingency> goGetContingencies(EquipmentsContainer contingencyListIdent, UUID networkUuid, String variantId, Reporter reporter) {
        try {
            return actionsService.getContingencyList(contingencyListIdent.getContainerId(), networkUuid, variantId);
        } catch (Exception ex) {
            LOGGER.error("Could not get contingencies from " + contingencyListIdent.getContainerName(), ex);
            reporter.report(Report.builder()
                .withKey("contingencyTranslationFailure")
                .withDefaultMessage("Could not get contingencies from contingencyListIdent ${name} : ${exception}")
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .withValue("exception", ex.getMessage())
                .withValue("name", contingencyListIdent.getContainerName())
                .build());
            return List.of();
        }
    }

    public List<Contingency> buildContingencies(UUID networkUuid, String variantId, List<EquipmentsContainer> contingencyListsContainerIdents, Reporter reporter) {
        return contingencyListsContainerIdents.stream()
            .flatMap(contingencyListIdent -> goGetContingencies(contingencyListIdent, networkUuid, variantId, reporter).stream())
            .collect(Collectors.toList());
    }

    private double getGeneratorWeight(Generator generator, SensitivityAnalysisInputData.DistributionType distributionType, Double distributionKey) {
        switch (distributionType) {
            case PROPORTIONAL:
                return !Double.isNaN(generator.getTerminal().getP()) ? generator.getTerminal().getP() : 0.;
            case PROPORTIONAL_MAXP:
                return generator.getMaxP();
            case REGULAR:
                return 1.;
            case VENTILATION:
                if (distributionKey == null) {
                    throw new PowsyblException("Distribution key required for VENTILATION distribution type !!");
                }
                return distributionKey;
            default:
                throw new UnsupportedOperationException("Distribution type not allowed for generator");
        }
    }

    private List<IdentifiableAttributes> goGetIdentifiables(EquipmentsContainer filter, UUID networkUuid, String variantId, Reporter reporter) {
        try {
            return filterService.getIdentifiablesFromFilter(filter.getContainerId(), networkUuid, variantId);
        } catch (Exception ex) {
            LOGGER.error("Could not get identifiables from filter " + filter.getContainerName(), ex);
            reporter.report(Report.builder()
                .withKey("filterTranslationFailure")
                .withDefaultMessage("Could not get identifiables from filter ${name} : ${exception}")
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .withValue("exception", ex.getMessage())
                .withValue("name", filter.getContainerName())
                .build());
            return List.of();
        }
    }

    public Stream<IdentifiableAttributes> getIdentifiablesFromContainer(UUID networkUuid, String variantId, EquipmentsContainer filter,
                                                                        List<IdentifiableType> equipmentsTypesAllowed, Reporter reporter) {

        List<IdentifiableAttributes> listIdentAttributes = goGetIdentifiables(filter, networkUuid, variantId, reporter);

        // check that monitored equipments type is allowed
        if (!listIdentAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            reporter.report(Report.builder()
                .withKey("badEquipmentType")
                .withDefaultMessage("Equipments type in filter with name=${name} should be ${expectedType} : filter is ignored")
                .withValue("name", filter.getContainerName())
                .withValue(EXPECTED_TYPE, equipmentsTypesAllowed.toString())
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
            return Stream.empty();
        }

        return listIdentAttributes.stream();
    }

    private Stream<IdentifiableAttributes> getMonitoredIdentifiablesFromContainer(UUID networkUuid, String variantId, EquipmentsContainer filter, List<IdentifiableType> equipmentsTypesAllowed, Reporter reporter) {
        List<IdentifiableAttributes> listIdentAttributes = goGetIdentifiables(filter, networkUuid, variantId, reporter);

        // check that monitored equipments type is allowed
        if (!listIdentAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            reporter.report(Report.builder()
                .withKey("badMonitoredEquipmentType")
                .withDefaultMessage("Monitored equipments type in filter with name=${name} should be ${expectedType} : filter is ignored")
                .withValue("name", filter.getContainerName())
                .withValue(EXPECTED_TYPE, equipmentsTypesAllowed.toString())
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
            return Stream.empty();
        }

        return listIdentAttributes.stream();
    }

    private List<SensitivityVariableSet> buildSensitivityVariableSets(UUID networkUuid, String variantId, Network network, Reporter reporter,
                                                                      List<IdentifiableType> variablesTypesAllowed,
                                                                      List<EquipmentsContainer> filters,
                                                                      SensitivityAnalysisInputData.DistributionType distributionType) {
        List<SensitivityVariableSet> result = new ArrayList<>();

        Stream<Pair<String, List<IdentifiableAttributes>>> variablesContainersLists = filters.stream()
            .map(filter -> Pair.of(filter.getContainerName(), getIdentifiablesFromContainer(networkUuid, variantId, filter, variablesTypesAllowed, reporter).collect(Collectors.toList())))
            .filter(list -> !list.getRight().isEmpty());

        variablesContainersLists.forEach(variablesList -> {
            List<WeightedSensitivityVariable> variables = new ArrayList<>();
            for (IdentifiableAttributes identifiableAttributes : variablesList.getRight()) {
                if (identifiableAttributes.getType() == IdentifiableType.GENERATOR) {
                    Generator generator = network.getGenerator(identifiableAttributes.getId());
                    if (generator == null) {
                        throw new PowsyblException("Generator '" + identifiableAttributes.getId() + "' not found !!");
                    }
                    double weight = getGeneratorWeight(generator, distributionType, identifiableAttributes.getDistributionKey());
                    variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                    break;
                }
            }
            result.add(new SensitivityVariableSet(variablesList.getLeft() + " (" + distributionType.name() + ")", variables));
        });

        return result;
    }

    private List<SensitivityFactor> buildSensitivityFactorsFromVariablesSets(RunContext context,
                                                                             Network network,
                                                                             Reporter reporter,
                                                                             List<IdentifiableType> monitoredEquipmentsTypesAllowed,
                                                                             NonEvacuatedEnergyMonitoredBranches branches,
                                                                             List<SensitivityVariableSet> variablesSets,
                                                                             List<Contingency> contingencies,
                                                                             SensitivityVariableType sensitivityVariableType) {
        if (variablesSets.isEmpty()) {
            return List.of();
        }

        List<IdentifiableAttributes> monitoredEquipments = branches.getBranches().stream()
            .flatMap(filter -> getMonitoredIdentifiablesFromContainer(context.getNetworkUuid(), context.getVariantId(), filter, monitoredEquipmentsTypesAllowed, reporter))
            .collect(Collectors.toList());

        return getSensitivityFactorsFromEquipments(context, network, variablesSets.stream().map(SensitivityVariableSet::getId).collect(Collectors.toList()),
            monitoredEquipments, branches, contingencies, sensitivityVariableType, reporter, true);
    }

    private List<SensitivityFactor> buildSensitivityFactors(RunContext context,
                                                            Network network,
                                                            Reporter reporter,
                                                            List<IdentifiableType> monitoredEquipmentsTypesAllowed,
                                                            NonEvacuatedEnergyMonitoredBranches branches,
                                                            List<EquipmentsContainer> injectionsFilters,
                                                            List<Contingency> contingencies,
                                                            SensitivityVariableType sensitivityVariableType) {

        List<IdentifiableAttributes> equipments = injectionsFilters.stream()
            .flatMap(filter -> getIdentifiablesFromContainer(context.getNetworkUuid(), context.getVariantId(), filter, List.of(IdentifiableType.GENERATOR), reporter))
            .toList();
        if (equipments.isEmpty()) {
            return List.of();
        }

        List<IdentifiableAttributes> monitoredEquipments = branches.getBranches().stream()
            .flatMap(filter -> getMonitoredIdentifiablesFromContainer(context.getNetworkUuid(), context.getVariantId(), filter, monitoredEquipmentsTypesAllowed, reporter))
            .collect(Collectors.toList());

        return getSensitivityFactorsFromEquipments(context, network, equipments.stream().map(IdentifiableAttributes::getId).toList(), monitoredEquipments, branches, contingencies, sensitivityVariableType, reporter, false);
    }

    private void genSensitivityFactorPerVariableId(List<String> variableIds,
                                                   SensitivityFunctionType functionType,
                                                   String monitoredEquipmentId,
                                                   SensitivityVariableType variableType,
                                                   boolean variableSet,
                                                   List<SensitivityFactor> result) {
        variableIds.forEach(varId -> result.add(new SensitivityFactor(functionType, monitoredEquipmentId, variableType, varId, variableSet, ContingencyContext.none())));
    }

    private void genSensitivityFactorPerVariableIdsAndContingencyIds(List<String> variableIds,
                                                                     List<Contingency> contingencies,
                                                                     SensitivityFunctionType functionType,
                                                                     String monitoredEquipmentId,
                                                                     SensitivityVariableType variableType,
                                                                     boolean variableSet,
                                                                     List<SensitivityFactor> result) {
        variableIds.forEach(varId ->
            contingencies.forEach(contingency ->
                result.add(new SensitivityFactor(functionType, monitoredEquipmentId, variableType, varId, variableSet, ContingencyContext.specificContingency(contingency.getId())))
            ));
    }

    private List<SensitivityFactor> getSensitivityFactorsFromEquipments(RunContext context,
                                                                        Network network,
                                                                        List<String> variableIds,
                                                                        List<IdentifiableAttributes> monitoredEquipments,
                                                                        NonEvacuatedEnergyMonitoredBranches branches,
                                                                        List<Contingency> contingencies,
                                                                        SensitivityVariableType sensitivityVariableType,
                                                                        Reporter reporter,
                                                                        boolean variableSet) {
        List<SensitivityFactor> result = new ArrayList<>();

        monitoredEquipments.forEach(monitoredEquipment -> {
            // get branch from network
            Branch branch = network.getBranch(monitoredEquipment.getId());
            if (branch == null) {
                reporter.report(Report.builder()
                    .withKey("monitoredBranchNotFound")
                    .withDefaultMessage("Could not find the monitored branch ${id}")
                    .withSeverity(TypedValue.ERROR_SEVERITY)
                    .withValue("id", monitoredEquipment.getId())
                    .build());
                return;
            }
            Optional<CurrentLimits> currentLimits1 = branch.getCurrentLimits1();
            Optional<CurrentLimits> currentLimits2 = branch.getCurrentLimits2();
            if (currentLimits1.isEmpty() && currentLimits2.isEmpty()) {  // no limits
                reporter.report(Report.builder()
                    .withKey("monitoredBranchNoCurrentLimits")
                    .withDefaultMessage("No current lilits for the monitored branch ${id}")
                    .withSeverity(TypedValue.ERROR_SEVERITY)
                    .withValue("id", monitoredEquipment.getId())
                    .build());
                return;
            }

            MonitoredBranchThreshold monitoredBranchThreshold = new MonitoredBranchThreshold(branch);
            boolean sensitivityFactorGenerated = false;

            if (branches.isIstN() || branches.isIstNm1()) {  // ist activated : we consider the permanent limit
                // limits on side 1
                if (currentLimits1.isPresent() && !Double.isNaN(currentLimits1.get().getPermanentLimit())) {
                    if (branches.isIstN()) {  // just one SensitivityFactor per variable id
                        genSensitivityFactorPerVariableId(variableIds, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_CURRENT_1, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                        monitoredBranchThreshold.setIstN(true);
                        monitoredBranchThreshold.setNCoeff(branches.getNCoefficient());
                        sensitivityFactorGenerated = true;
                    }
                    if (branches.isIstNm1()) {  // one SensitivityFactor per variable id and per contingency id
                        genSensitivityFactorPerVariableIdsAndContingencyIds(variableIds, contingencies, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_CURRENT_1, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                        monitoredBranchThreshold.setIstNm1(true);
                        monitoredBranchThreshold.setNm1Coeff(branches.getNm1Coefficient());
                        sensitivityFactorGenerated = true;
                    }
                }

                // limits on side 2
                if (currentLimits2.isPresent() && !Double.isNaN(currentLimits2.get().getPermanentLimit())) {
                    if (branches.isIstN()) {  // just one SensitivityFactor per variable id
                        genSensitivityFactorPerVariableId(variableIds, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_2 : SensitivityFunctionType.BRANCH_CURRENT_2, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                        monitoredBranchThreshold.setIstN(true);
                        monitoredBranchThreshold.setNCoeff(branches.getNCoefficient());
                        sensitivityFactorGenerated = true;
                    }
                    if (branches.isIstNm1()) {  // one SensitivityFactor per variable id and per contingency id
                        genSensitivityFactorPerVariableIdsAndContingencyIds(variableIds, contingencies, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_2 : SensitivityFunctionType.BRANCH_CURRENT_2, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                        monitoredBranchThreshold.setIstNm1(true);
                        monitoredBranchThreshold.setNm1Coeff(branches.getNm1Coefficient());
                        sensitivityFactorGenerated = true;
                    }
                }
            }

            if (StringUtils.isNotEmpty(branches.getLimitNameN())) {  // here we consider the temporary limit
                // We must check if limit name provided appears in the branch temporary limits on side 1
                if (currentLimits1.isPresent() &&
                    !CollectionUtils.isEmpty(currentLimits1.get().getTemporaryLimits()) &&
                    currentLimits1.get().getTemporaryLimits().stream().anyMatch(l -> l.getName().equals(branches.getLimitNameN()))) {
                    genSensitivityFactorPerVariableId(variableIds, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_CURRENT_1, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                    monitoredBranchThreshold.setIstN(true);
                    monitoredBranchThreshold.setNLimitName(branches.getLimitNameN());
                    monitoredBranchThreshold.setNCoeff(branches.getNCoefficient());
                    sensitivityFactorGenerated = true;
                }

                // We must check if limit name provided appears in the branch temporary limits on side 2
                if (currentLimits2.isPresent() &&
                    !CollectionUtils.isEmpty(currentLimits2.get().getTemporaryLimits()) &&
                    currentLimits2.get().getTemporaryLimits().stream().anyMatch(l -> l.getName().equals(branches.getLimitNameN()))) {
                    genSensitivityFactorPerVariableId(variableIds, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_2 : SensitivityFunctionType.BRANCH_CURRENT_2, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                    monitoredBranchThreshold.setIstN(true);
                    monitoredBranchThreshold.setNLimitName(branches.getLimitNameN());
                    monitoredBranchThreshold.setNCoeff(branches.getNCoefficient());
                    sensitivityFactorGenerated = true;
                }
            }

            if (StringUtils.isNotEmpty(branches.getLimitNameNm1())) {  // here we consider the temporary limit
                // We must check if limit name provided appears in the branch temporary limits on side 1
                if (currentLimits1.isPresent() &&
                    !CollectionUtils.isEmpty(currentLimits1.get().getTemporaryLimits()) &&
                    currentLimits1.get().getTemporaryLimits().stream().anyMatch(l -> l.getName().equals(branches.getLimitNameNm1()))) {
                    genSensitivityFactorPerVariableIdsAndContingencyIds(variableIds, contingencies, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_CURRENT_1, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                    monitoredBranchThreshold.setIstNm1(true);
                    monitoredBranchThreshold.setNm1LimitName(branches.getLimitNameNm1());
                    monitoredBranchThreshold.setNm1Coeff(branches.getNm1Coefficient());
                    sensitivityFactorGenerated = true;
                }
                // We must check if limit name provided appears in the branch temporary limits on side 2
                if (currentLimits2.isPresent() &&
                    !CollectionUtils.isEmpty(currentLimits2.get().getTemporaryLimits()) &&
                    currentLimits2.get().getTemporaryLimits().stream().anyMatch(l -> l.getName().equals(branches.getLimitNameNm1()))) {
                    genSensitivityFactorPerVariableIdsAndContingencyIds(variableIds, contingencies, variableSet ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_2 : SensitivityFunctionType.BRANCH_CURRENT_2, monitoredEquipment.getId(), sensitivityVariableType, variableSet, result);
                    monitoredBranchThreshold.setIstNm1(true);
                    monitoredBranchThreshold.setNm1LimitName(branches.getLimitNameNm1());
                    monitoredBranchThreshold.setNm1Coeff(branches.getNm1Coefficient());
                    sensitivityFactorGenerated = true;
                }
            }

            if (sensitivityFactorGenerated) {
                context.getInputs().getBranchesThresholds().put(monitoredEquipment.getId(), monitoredBranchThreshold);
            }
        });

        return result;
    }

    private void buildSensitivityFactorsAndVariableSets(RunContext context,
                                                        Network network,
                                                        Reporter reporter) {
        List<NonEvacuatedEnergyMonitoredBranches> monitoredBranches = context.getInputData().getNonEvacuatedEnergyMonitoredBranches();
        List<NonEvacuatedEnergyGeneratorLimitByType> generatorsLimitByType = context.getInputData().getNonEvacuatedEnergyGeneratorsLimit().getGenerators();

        // build inputs for the sensitivities in MW per generation kind (similar to sensitivity analysis computation with injections set)
        generatorsLimitByType.stream()
            .filter(NonEvacuatedEnergyGeneratorLimitByType::isActivated) // we keep only activated generators limits
            .forEach(generatorLimitByType -> {
                // build sensitivity variable sets from generators limits in input data
                List<SensitivityVariableSet> vInjectionsSets = buildSensitivityVariableSets(
                    context.getNetworkUuid(), context.getVariantId(), network, reporter,
                    List.of(IdentifiableType.GENERATOR),
                    generatorLimitByType.getGenerators(),
                    SensitivityAnalysisInputData.DistributionType.PROPORTIONAL_MAXP);
                context.getInputs().addSensitivityVariableSets(vInjectionsSets);

                // build sensitivity factors from the variable sets (=set of generators), the contingencies and the monitored branches sets
                monitoredBranches.stream()
                    .filter(NonEvacuatedEnergyMonitoredBranches::isActivated)  // we keep only activated monitored branches set
                    .forEach(branches -> {
                        List<SensitivityFactor> fInjectionsSet = buildSensitivityFactorsFromVariablesSets(
                            context, network, reporter,
                            List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                            branches,
                            vInjectionsSets,
                            context.getInputs().getContingencies(),
                            SensitivityVariableType.INJECTION_ACTIVE_POWER);
                        context.getInputs().addSensitivityFactors(fInjectionsSet);
                    });
            });

        // build inputs for the sensitivities in A for each generator (similar to sensitivity analysis computation with injections)
        generatorsLimitByType.stream()
            .filter(NonEvacuatedEnergyGeneratorLimitByType::isActivated)  // we keep only activated generators limits
            .forEach(generatorLimitByType -> {
                // build sensitivity factors from the variables (=generators), the contingencies and the monitored branches sets
                monitoredBranches.stream()
                    .filter(NonEvacuatedEnergyMonitoredBranches::isActivated) // we keep only activated monitored branches set
                    .forEach(branches -> {
                        // build the sensitivity factors for each monitored branch
                        List<SensitivityFactor> fInjectionsSet = buildSensitivityFactors(
                            context, network, reporter,
                            List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                            branches,
                            generatorLimitByType.getGenerators(),
                            context.getInputs().getContingencies(),
                            SensitivityVariableType.INJECTION_ACTIVE_POWER);
                        context.getInputs().addSensitivityFactors(fInjectionsSet);

                        // memorize initial targetP for the capping generators
                        // (used later to init the generator capping info when computing the injection variation from sensitivities results)
                        fInjectionsSet.forEach(f -> {
                            Generator generator = network.getGenerator(f.getVariableId());
                            if (generator != null) {
                                context.getInputs().getGeneratorsPInit().put(f.getVariableId(), generator.getTargetP());
                            }
                        });
                    });
            });
    }

    public void build(RunContext context, Network network, Reporter reporter) {
        try {
            // build all contingencies from the input data
            context.getInputs().addContingencies(buildContingencies(context.getNetworkUuid(),
                    context.getVariantId(),
                    context.getInputData().getNonEvacuatedEnergyContingencies().stream()
                        .filter(NonEvacuatedEnergyContingencies::isActivated)  // we keep only the activated contingencies
                        .map(NonEvacuatedEnergyContingencies::getContingencies)
                        .flatMap(List::stream)
                        .toList(),
                    reporter));

            // build all sensitivity variables and factors from the input data
            buildSensitivityFactorsAndVariableSets(context, network, reporter);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg == null) {
                msg = ex.getClass().getName();
            }
            reporter.report(Report.builder()
                .withKey("NonEvacuatedEnergyInputParametersTranslationFailure")
                .withDefaultMessage("Failure while building inputs, exception : ${exception}")
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .withValue("exception", msg)
                .build());
            LOGGER.error("Running non evacuated energy context translation failure, report added");
            throw ex;
        }
    }
}

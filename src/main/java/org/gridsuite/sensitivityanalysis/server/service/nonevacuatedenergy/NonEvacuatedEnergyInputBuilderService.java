/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeAdder;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.*;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.sensitivityanalysis.server.dto.Contingencies;
import org.gridsuite.sensitivityanalysis.server.dto.EquipmentsContainer;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyContingencies;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsCappingsByType;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyMonitoredBranches;
import org.gridsuite.sensitivityanalysis.server.service.ActionsService;
import org.gridsuite.sensitivityanalysis.server.service.FilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyInputBuilderService {
    private static final String EXPECTED_TYPE = "expectedType";
    private static final Logger LOGGER = LoggerFactory.getLogger(NonEvacuatedEnergyInputBuilderService.class);
    private final ActionsService actionsService;
    private final FilterService filterService;

    public NonEvacuatedEnergyInputBuilderService(ActionsService actionsService, FilterService filterService) {
        this.actionsService = actionsService;
        this.filterService = filterService;
    }

    private static void addReport(ReportNode parent, String messageKey, String messageTemplate, Map<String, String> values, TypedValue severity) {
        ReportNodeAdder adder = parent.newReportNode().withMessageTemplate(messageKey, messageTemplate);
        values.entrySet().forEach(v -> adder.withUntypedValue(v.getKey(), v.getValue()));
        adder.withSeverity(TypedValue.ERROR_SEVERITY).add();
    }

    private List<Contingency> goGetContingencies(List<EquipmentsContainer> contingencyListIdent, UUID networkUuid, String variantId, ReportNode reporter) {
        List<UUID> ids = contingencyListIdent.stream().map(EquipmentsContainer::getContainerId).toList();
        Contingencies contingencies = actionsService.getContingencyList(ids, networkUuid, variantId);
        if (contingencies == null) {
            return List.of();
        }

        if (contingencies.getContingenciesNotFound() != null) {
            contingencies.getContingenciesNotFound().forEach(id -> {
                EquipmentsContainer container = contingencyListIdent.stream().filter(c -> c.getContainerId().equals(id)).findFirst().orElseThrow();
                LOGGER.error("Could not get contingencies from {}", container.getContainerName());
                addReport(reporter,
                        "contingencyTranslationFailure",
                        "Could not get contingencies from contingencyListIdent ${name} : Not found",
                        Map.of("name", container.getContainerName()),
                        TypedValue.ERROR_SEVERITY
                );
            });
        }

        return contingencies.getContingenciesFound() == null ? List.of() : contingencies.getContingenciesFound();
    }

    public List<Contingency> buildContingencies(UUID networkUuid, String variantId, List<EquipmentsContainer> contingencyListsContainerIdents, ReportNode reporter) {
        return goGetContingencies(contingencyListsContainerIdents, networkUuid, variantId, reporter);
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

    private List<IdentifiableAttributes> goGetIdentifiables(EquipmentsContainer filter, UUID networkUuid, String variantId, ReportNode reporter) {
        try {
            return filterService.getIdentifiablesFromFilter(filter.getContainerId(), networkUuid, variantId);
        } catch (Exception ex) {
            LOGGER.error("Could not get identifiables from filter " + filter.getContainerName(), ex);
            addReport(reporter,
                    "filterTranslationFailure",
                    "Could not get identifiables from filter ${name} : ${exception}",
                    Map.of("exception", ex.getMessage(), "name", filter.getContainerName()),
                    TypedValue.ERROR_SEVERITY
            );
            return List.of();
        }
    }

    public Stream<IdentifiableAttributes> getIdentifiablesFromContainer(UUID networkUuid, String variantId, EquipmentsContainer filter,
                                                                        List<IdentifiableType> equipmentsTypesAllowed, ReportNode reporter) {

        List<IdentifiableAttributes> listIdentAttributes = goGetIdentifiables(filter, networkUuid, variantId, reporter);

        // check that monitored equipments type is allowed
        if (!listIdentAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            addReport(reporter,
                    "badEquipmentType",
                    "Equipments type in filter with name=${name} should be ${expectedType} : filter is ignored",
                    Map.of("name", filter.getContainerName(), EXPECTED_TYPE, equipmentsTypesAllowed.toString()),
                    TypedValue.WARN_SEVERITY
            );
            return Stream.empty();
        }

        return listIdentAttributes.stream();
    }

    private Stream<IdentifiableAttributes> getMonitoredIdentifiablesFromContainer(UUID networkUuid, String variantId, EquipmentsContainer filter, List<IdentifiableType> equipmentsTypesAllowed, ReportNode reporter) {
        List<IdentifiableAttributes> listIdentAttributes = goGetIdentifiables(filter, networkUuid, variantId, reporter);

        // check that monitored equipments type is allowed
        if (!listIdentAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            addReport(reporter,
                    "badMonitoredEquipmentType",
                    "Monitored equipments type in filter with name=${name} should be ${expectedType} : filter is ignored",
                    Map.of("name", filter.getContainerName(), EXPECTED_TYPE, equipmentsTypesAllowed.toString()),
                    TypedValue.WARN_SEVERITY
            );
            return Stream.empty();
        }

        return listIdentAttributes.stream();
    }

    private List<SensitivityVariableSet> buildSensitivityVariableSets(UUID networkUuid, String variantId, Network network, ReportNode reporter,
                                                                      List<IdentifiableType> variablesTypesAllowed,
                                                                      List<EquipmentsContainer> filters,
                                                                      EnergySource energySource,
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
                    if (generator.getEnergySource() != energySource) {
                        addReport(reporter,
                                "BadGeneratorEnergySource",
                                "Generator ${generatorId} is not of the required energy source : ${energySource}",
                                Map.of("generatorId", generator.getId(), "energySource", energySource.name()),
                                TypedValue.WARN_SEVERITY
                        );
                    } else {
                        double weight = getGeneratorWeight(generator, distributionType, identifiableAttributes.getDistributionKey());
                        variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                    }
                }
            }
            result.add(new SensitivityVariableSet(variablesList.getLeft() + " (" + distributionType.name() + ")", variables));
        });

        return result;
    }

    private List<SensitivityFactor> buildSensitivityFactorsFromVariablesSets(NonEvacuatedEnergyRunContext context,
                                                                             Network network,
                                                                             ReportNode reporter,
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

    private List<SensitivityFactor> buildSensitivityFactors(NonEvacuatedEnergyRunContext context,
                                                            Network network,
                                                            ReportNode reporter,
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

    private boolean genFactorForPermanentLimit(Branch branch,
                                            TwoSides side,
                                            Optional<CurrentLimits> currentLimits,
                                            List<String> variableIds,
                                            boolean variableSet,
                                            SensitivityVariableType sensitivityVariableType,
                                            NonEvacuatedEnergyMonitoredBranches branches,
                                            MonitoredBranchThreshold monitoredBranchThreshold,
                                            List<Contingency> contingencies,
                                            List<SensitivityFactor> result,
                                               ReportNode reporter) {
        if (currentLimits.isEmpty()) {
            return false;
        }
        if (Double.isNaN(currentLimits.get().getPermanentLimit())) {
            // no permanent limit on side found : report and throw exception
            addReport(reporter,
                    "monitoredBranchNoCurrentOrPermanentLimitsOnSide",
                    "No permanent limit for the monitored branch ${id} on side ${side}",
                    Map.of("id", branch.getId(), "side", side.name()),
                    TypedValue.ERROR_SEVERITY
            );
            throw new PowsyblException("Branch '" + branch.getId() + "' has no permanent limit on side '" + side.name() + "' !!");
        }

        SensitivityFunctionType functionTypeActivePower = side == TwoSides.ONE ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_ACTIVE_POWER_2;
        SensitivityFunctionType functionTypeCurrent = side == TwoSides.ONE ? SensitivityFunctionType.BRANCH_CURRENT_1 : SensitivityFunctionType.BRANCH_CURRENT_2;

        if (branches.isIstN()) {  // just one SensitivityFactor per variable id
            genSensitivityFactorPerVariableId(variableIds, variableSet ? functionTypeActivePower : functionTypeCurrent, branch.getId(), sensitivityVariableType, variableSet, result);
            monitoredBranchThreshold.setIstN(true);
            monitoredBranchThreshold.setNCoeff(branches.getNCoefficient());
        }
        if (branches.isIstNm1()) {  // one SensitivityFactor per variable id and per contingency id
            genSensitivityFactorPerVariableIdsAndContingencyIds(variableIds, contingencies, variableSet ? functionTypeActivePower : functionTypeCurrent, branch.getId(), sensitivityVariableType, variableSet, result);
            monitoredBranchThreshold.setIstNm1(true);
            monitoredBranchThreshold.setNm1Coeff(branches.getNm1Coefficient());
        }
        return true;
    }

    private boolean genFactorForTemporaryLimit(Branch branch,
                                            TwoSides side,
                                            Optional<CurrentLimits> currentLimits,
                                            String limitName,
                                            boolean inN,
                                            List<String> variableIds,
                                            boolean variableSet,
                                            SensitivityVariableType sensitivityVariableType,
                                            NonEvacuatedEnergyMonitoredBranches branches,
                                            MonitoredBranchThreshold monitoredBranchThreshold,
                                            List<Contingency> contingencies,
                                            List<SensitivityFactor> result) {
        if (currentLimits.isEmpty() || currentLimits.get().getTemporaryLimits().stream().noneMatch(l -> l.getName().equals(limitName))) {
            return false;
        }

        SensitivityFunctionType functionTypeActivePower = side == TwoSides.ONE ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_ACTIVE_POWER_2;
        SensitivityFunctionType functionTypeCurrent = side == TwoSides.ONE ? SensitivityFunctionType.BRANCH_CURRENT_1 : SensitivityFunctionType.BRANCH_CURRENT_2;

        if (inN) {
            genSensitivityFactorPerVariableId(variableIds, variableSet ? functionTypeActivePower : functionTypeCurrent, branch.getId(), sensitivityVariableType, variableSet, result);
            monitoredBranchThreshold.setIstN(false);
            monitoredBranchThreshold.setNLimitName(limitName);
            monitoredBranchThreshold.setNCoeff(branches.getNCoefficient());
        } else {
            genSensitivityFactorPerVariableIdsAndContingencyIds(variableIds, contingencies, variableSet ? functionTypeActivePower : functionTypeCurrent, branch.getId(), sensitivityVariableType, variableSet, result);
            monitoredBranchThreshold.setIstNm1(false);
            monitoredBranchThreshold.setNm1LimitName(limitName);
            monitoredBranchThreshold.setNm1Coeff(branches.getNm1Coefficient());
        }
        return true;
    }

    private List<SensitivityFactor> getSensitivityFactorsFromEquipments(NonEvacuatedEnergyRunContext context,
                                                                        Network network,
                                                                        List<String> variableIds,
                                                                        List<IdentifiableAttributes> monitoredEquipments,
                                                                        NonEvacuatedEnergyMonitoredBranches branches,
                                                                        List<Contingency> contingencies,
                                                                        SensitivityVariableType sensitivityVariableType,
                                                                        ReportNode reporter,
                                                                        boolean variableSet) {
        List<SensitivityFactor> result = new ArrayList<>();

        monitoredEquipments.forEach(monitoredEquipment -> {
            // get branch from network
            Branch branch = network.getBranch(monitoredEquipment.getId());
            if (branch == null) {  // branch not found : just report and ignore the branch
                addReport(reporter,
                        "monitoredBranchNotFound",
                        "Could not find the monitored branch ${id}",
                        Map.of("id", monitoredEquipment.getId()),
                        TypedValue.ERROR_SEVERITY
                );
                return;
            }
            Optional<CurrentLimits> currentLimits1 = branch.getCurrentLimits1();
            Optional<CurrentLimits> currentLimits2 = branch.getCurrentLimits2();
            if (currentLimits1.isEmpty() && currentLimits2.isEmpty()) {
                // no current limits on both sides found : report and throw exception
                addReport(reporter,
                        "monitoredBranchNoCurrentLimits",
                        "No current limits for the monitored branch ${id}",
                        Map.of("id", monitoredEquipment.getId()),
                        TypedValue.ERROR_SEVERITY
                );
                throw new PowsyblException("Branch '" + branch.getId() + "' has no current limits !!");
            }

            MonitoredBranchThreshold monitoredBranchThreshold = new MonitoredBranchThreshold(branch);

            if (branches.isIstN() || branches.isIstNm1()) {  // Ist activated : we consider the permanent limit
                // permanent limit on side 1
                boolean factors1Generated = genFactorForPermanentLimit(branch, TwoSides.ONE, currentLimits1, variableIds, variableSet, sensitivityVariableType, branches, monitoredBranchThreshold, contingencies, result, reporter);

                // permanent limit on side 2
                boolean factors2Generated = genFactorForPermanentLimit(branch, TwoSides.TWO, currentLimits2, variableIds, variableSet, sensitivityVariableType, branches, monitoredBranchThreshold, contingencies, result, reporter);

                if (!factors1Generated && !factors2Generated) {
                    // no temporary limit on one side : report and throw exception
                    addReport(reporter,
                            "monitoredBranchNoPermanentLimits",
                            "No permanent limits for the monitored branch ${id}",
                            Map.of("id", monitoredEquipment.getId()),
                            TypedValue.ERROR_SEVERITY
                    );
                    throw new PowsyblException("Branch '" + branch.getId() + "' has no permanent limits !!");
                }
            }

            if (!branches.isIstN() && StringUtils.isNotEmpty(branches.getLimitNameN())) {  // here we consider the temporary limits
                // temporary limits on side 1
                boolean factors1Generated = genFactorForTemporaryLimit(branch, TwoSides.ONE, currentLimits1, branches.getLimitNameN(), true, variableIds, variableSet, sensitivityVariableType, branches, monitoredBranchThreshold, contingencies, result);

                // temporary limits on side 2
                boolean factors2Generated = genFactorForTemporaryLimit(branch, TwoSides.TWO, currentLimits2, branches.getLimitNameN(), true, variableIds, variableSet, sensitivityVariableType, branches, monitoredBranchThreshold, contingencies, result);

                if (!factors1Generated && !factors2Generated) {
                    addReport(reporter,
                            "monitoredBranchTemporaryLimitNotFound",
                            "Temporary limit ${limitName} not found for the monitored branch ${id}",
                            Map.of("limitName", branches.getLimitNameN(), "id", branch.getId()),
                            TypedValue.ERROR_SEVERITY
                    );
                    throw new PowsyblException("Temporary limit '" + branches.getLimitNameN() + "' not found for branch '" + branch.getId() + "' !!");
                }
            }

            if (!branches.isIstNm1() && StringUtils.isNotEmpty(branches.getLimitNameNm1())) {  // here we consider the temporary limit
                // temporary limits on side 1
                boolean factors1Generated = genFactorForTemporaryLimit(branch, TwoSides.ONE, currentLimits1, branches.getLimitNameNm1(), false, variableIds, variableSet, sensitivityVariableType, branches, monitoredBranchThreshold, contingencies, result);

                // temporary limits on side 2
                boolean factors2Generated = genFactorForTemporaryLimit(branch, TwoSides.TWO, currentLimits2, branches.getLimitNameNm1(), false, variableIds, variableSet, sensitivityVariableType, branches, monitoredBranchThreshold, contingencies, result);

                if (!factors1Generated && !factors2Generated) {
                    addReport(reporter,
                            "monitoredBranchTemporaryLimitNotFound",
                            "Temporary limit ${limitName} not found for the monitored branch ${id}",
                            Map.of("limitName", branches.getLimitNameNm1(), "id", branch.getId()),
                            TypedValue.ERROR_SEVERITY
                    );
                    throw new PowsyblException("Temporary limit '" + branches.getLimitNameNm1() + "' not found for branch '" + branch.getId() + "' !!");
                }
            }

            if (!result.isEmpty()) {
                context.getNonEvacuatedEnergyInputs().getBranchesThresholds().put(monitoredEquipment.getId(), monitoredBranchThreshold);
            }
        });

        return result;
    }

    private void buildSensitivityFactorsAndVariableSets(NonEvacuatedEnergyRunContext context,
                                                        Network network,
                                                        ReportNode reporter) {
        List<NonEvacuatedEnergyMonitoredBranches> monitoredBranches = context.getNonEvacuatedEnergyInputData().getNonEvacuatedEnergyMonitoredBranches();
        List<NonEvacuatedEnergyGeneratorsCappingsByType> generatorsCappingsByType = context.getNonEvacuatedEnergyInputData().getNonEvacuatedEnergyGeneratorsCappings().getGenerators();

        // build inputs for the sensitivities in MW per generation kind (similar to sensitivity analysis computation with injections set)
        generatorsCappingsByType.stream()
            .filter(NonEvacuatedEnergyGeneratorsCappingsByType::isActivated) // we keep only activated generators limits
            .forEach(generatorLimitByType -> {
                // build sensitivity variable sets from generators limits in input data
                List<SensitivityVariableSet> vInjectionsSets = buildSensitivityVariableSets(
                    context.getNetworkUuid(), context.getVariantId(), network, reporter,
                    List.of(IdentifiableType.GENERATOR),
                    generatorLimitByType.getGenerators(),
                    generatorLimitByType.getEnergySource(),
                    SensitivityAnalysisInputData.DistributionType.PROPORTIONAL_MAXP);
                context.getNonEvacuatedEnergyInputs().addSensitivityVariableSets(vInjectionsSets);

                // we store the cappings generators by energy source
                context.getNonEvacuatedEnergyInputs().getCappingsGenerators().put(generatorLimitByType.getEnergySource(),
                    vInjectionsSets.stream().flatMap(v -> v.getVariablesById().keySet().stream()).toList());

                // build sensitivity factors from the variable sets (=set of generators), the contingencies and the monitored branches sets
                monitoredBranches.stream()
                    .filter(NonEvacuatedEnergyMonitoredBranches::isActivated)  // we keep only activated monitored branches set
                    .forEach(branches -> {
                        List<SensitivityFactor> fInjectionsSet = buildSensitivityFactorsFromVariablesSets(
                            context, network, reporter,
                            List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                            branches,
                            vInjectionsSets,
                            context.getNonEvacuatedEnergyInputs().getContingencies(),
                            SensitivityVariableType.INJECTION_ACTIVE_POWER);
                        context.getNonEvacuatedEnergyInputs().addSensitivityFactors(fInjectionsSet);
                    });
            });

        // build inputs for the sensitivities in A for each generator (similar to sensitivity analysis computation with injections)
        generatorsCappingsByType.stream()
            .filter(NonEvacuatedEnergyGeneratorsCappingsByType::isActivated)  // we keep only activated generators limits
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
                            context.getNonEvacuatedEnergyInputs().getContingencies(),
                            SensitivityVariableType.INJECTION_ACTIVE_POWER);
                        context.getNonEvacuatedEnergyInputs().addSensitivityFactors(fInjectionsSet);
                    });
            });
    }

    public void build(NonEvacuatedEnergyRunContext context, Network network, ReportNode reporter) {
        try {
            // build all contingencies from the input data
            context.getNonEvacuatedEnergyInputs().addContingencies(buildContingencies(context.getNetworkUuid(),
                    context.getVariantId(),
                    context.getNonEvacuatedEnergyInputData().getNonEvacuatedEnergyContingencies().stream()
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
            addReport(reporter,
                    "NonEvacuatedEnergyInputParametersTranslationFailure",
                    "Failure while building inputs, exception : ${exception}",
                    Map.of("exception", msg),
                    TypedValue.ERROR_SEVERITY
            );
            LOGGER.error("Running non evacuated energy context translation failure, report added");
            throw ex;
        }
    }
}

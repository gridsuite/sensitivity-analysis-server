/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.*;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.sensitivityanalysis.server.dto.*;
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
public class SensitivityAnalysisInputBuilderService {
    private static final String EXPECTED_TYPE = "expectedType";
    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalysisInputBuilderService.class);
    public static final String INJECTIONS = "injections";
    public static final String CONTINGENCIES = "contingencies";
    private final ActionsService actionsService;
    private final FilterService filterService;

    private static final String NAMES = "names";

    public SensitivityAnalysisInputBuilderService(ActionsService actionsService, FilterService filterService) {
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

    private List<Contingency> buildContingencies(SensitivityAnalysisRunContext context, List<EquipmentsContainer> contingencyListsContainerIdents, Reporter reporter) {
        return contingencyListsContainerIdents.stream()
            .flatMap(contingencyListIdent -> goGetContingencies(contingencyListIdent, context.getNetworkUuid(), context.getVariantId(), reporter).stream())
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

    private double getLoadWeight(Load load, SensitivityAnalysisInputData.DistributionType distributionType, Double distributionKey) {
        switch (distributionType) {
            case PROPORTIONAL:
            case PROPORTIONAL_MAXP: // simpler to use the same enum for generator and load
                return load.getP0();
            case REGULAR:
                return 1.;
            case VENTILATION:
                if (distributionKey == null) {
                    throw new PowsyblException("Distribution key required for VENTILATION distribution type !!");
                }
                return distributionKey;
            default:
                throw new UnsupportedOperationException("Distribution type not allowed for load");
        }
    }

    private List<IdentifiableAttributes> goGetIdentifiables(List<EquipmentsContainer> filters, UUID networkUuid, String variantId, Reporter reporter) {
        String containersNames = getContainerNames(filters);
        try {
            //extract container id from filters
            List<UUID> filterIds = filters.stream().map(EquipmentsContainer::getContainerId).toList();
            return filterService.getIdentifiablesFromFilters(filterIds, networkUuid, variantId);
        } catch (Exception ex) {
            LOGGER.error("Could not get identifiables from filter " + containersNames, ex);
            reporter.report(Report.builder()
                .withKey("filterTranslationFailure")
                .withDefaultMessage("Could not get identifiables from filters ${names} : ${exception}")
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .withValue("exception", ex.getMessage())
                .withValue(NAMES, containersNames)
                .build());
            return List.of();
        }
    }

    private Integer getContingenciesCount(List<UUID> ids, UUID networkUuid, String variantId) {
        return ids.stream()
                .mapToInt(uuid -> actionsService.getContingencyList(uuid, networkUuid, variantId).size())
                .sum();
    }

    private Long getFactorsCount(Map<String, List<UUID>> ids, UUID networkUuid, String variantId, Long containersAttributesCount) {
        Long contAttributesCountTemp = containersAttributesCount;
        if (ids.containsKey(CONTINGENCIES) && !ids.get(CONTINGENCIES).isEmpty()) {
            int sumContingencyListSizes = getContingenciesCount(ids.get(CONTINGENCIES), networkUuid, variantId);
            sumContingencyListSizes = Math.max(sumContingencyListSizes, 1);
            contAttributesCountTemp *= sumContingencyListSizes;
            ids.remove(CONTINGENCIES);
        }
        ids.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));
        Map<String, List<Long>> map = filterService.getIdentifiablesCount(ids, networkUuid, null);
        for (List<Long> valueList : map.values()) {
            int sensiFactorCount = valueList.stream().mapToInt(Long::intValue).sum();
            if (sensiFactorCount != 0) {
                contAttributesCountTemp *= sensiFactorCount;
            }
        }

        return contAttributesCountTemp;
    }

    private Stream<IdentifiableAttributes> getIdentifiablesFromContainers(SensitivityAnalysisRunContext context, List<EquipmentsContainer> filters,
                                                                          List<IdentifiableType> equipmentsTypesAllowed, Reporter reporter) {
        String containersNames = getContainerNames(filters);
        List<IdentifiableAttributes> listIdentifiableAttributes = goGetIdentifiables(filters, context.getNetworkUuid(), context.getVariantId(), reporter);

        // check that monitored equipments type is allowed
        if (!listIdentifiableAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            reporter.report(Report.builder()
                .withKey("badEquipmentType")
                .withDefaultMessage("Equipments type in filter with name=${names} should be ${expectedType} : filter is ignored")
                .withValue(NAMES, containersNames)
                .withValue(EXPECTED_TYPE, equipmentsTypesAllowed.toString())
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
            return Stream.empty();
        }

        return listIdentifiableAttributes.stream();
    }

    private String getContainerNames(List<EquipmentsContainer> filters) {
        List<String> containerNamesList = filters.stream().map(EquipmentsContainer::getContainerName).toList();
        return "[" + String.join(", ", containerNamesList) + "]";
    }

    private Stream<IdentifiableAttributes> getMonitoredIdentifiablesFromContainers(SensitivityAnalysisRunContext context, Network network, List<EquipmentsContainer> filters, List<IdentifiableType> equipmentsTypesAllowed, Reporter reporter) {
        String containersNames = getContainerNames(filters);
        List<IdentifiableAttributes> listIdentAttributes = goGetIdentifiables(filters, context.getNetworkUuid(), context.getVariantId(), reporter);

        // check that monitored equipments type is allowed
        if (!listIdentAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            reporter.report(Report.builder()
                .withKey("badMonitoredEquipmentType")
                .withDefaultMessage("Monitored equipments type in filter with name=${names} should be ${expectedType} : filter is ignored")
                .withValue(NAMES, containersNames)
                .withValue(EXPECTED_TYPE, equipmentsTypesAllowed.toString())
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
            return Stream.empty();
        }

        if (listIdentAttributes.isEmpty() || listIdentAttributes.get(0).getType() != IdentifiableType.VOLTAGE_LEVEL) {
            return listIdentAttributes.stream();
        }

        // for voltage levels, get the list of all buses or busbar sections
        return listIdentAttributes.stream().flatMap(voltageLevel -> {
            VoltageLevel vl = network.getVoltageLevel(voltageLevel.getId());
            if (vl == null) {
                throw new PowsyblException("Voltage level '" + voltageLevel.getId() + "' not found !!");
            }
            return vl.getTopologyKind() == TopologyKind.NODE_BREAKER ?
                vl.getNodeBreakerView().getBusbarSectionStream().filter(bbs -> bbs.getTerminal().getBusView().getBus() != null).map(bbs -> new IdentifiableAttributes(bbs.getId(), bbs.getType(), null)) :
                vl.getBusBreakerView().getBusStream().filter(bus -> bus.getConnectedTerminalStream().map(t -> t.getBusView().getBus()).anyMatch(Objects::nonNull)).map(bus -> new IdentifiableAttributes(bus.getId(), bus.getType(), null));
        });
    }

    private List<SensitivityFactor> getSensitivityFactorsFromEquipments(List<String> variableIds,
                                                                        List<IdentifiableAttributes> monitoredEquipments,
                                                                        List<Contingency> contingencies,
                                                                        SensitivityFunctionType sensitivityFunctionType,
                                                                        SensitivityVariableType sensitivityVariableType,
                                                                        boolean variableSet) {
        List<SensitivityFactor> result = new ArrayList<>();

        monitoredEquipments.forEach(monitoredEquipment -> variableIds.forEach(varId -> {
            result.add(new SensitivityFactor(
                sensitivityFunctionType,
                monitoredEquipment.getId(),
                sensitivityVariableType,
                varId,
                variableSet,
                ContingencyContext.none()));

            // if contingencies are given: creation, for each monitored equipment, of one sensitivity factor for each contingency
            contingencies.forEach(contingency ->
                result.add(new SensitivityFactor(
                    sensitivityFunctionType,
                    monitoredEquipment.getId(),
                    sensitivityVariableType,
                    varId,
                    variableSet,
                    ContingencyContext.specificContingency(contingency.getId())))
            );
        }));

        return result;
    }

    private List<SensitivityVariableSet> buildSensitivityVariableSets(SensitivityAnalysisRunContext context, Network network, Reporter reporter,
                                                                      List<IdentifiableType> variablesTypesAllowed,
                                                                      List<EquipmentsContainer> filters,
                                                                      SensitivityAnalysisInputData.DistributionType distributionType) {
        List<SensitivityVariableSet> result = new ArrayList<>();
        List<IdentifiableAttributes> monitoredVariablesContainersLists = getIdentifiablesFromContainers(context, filters, variablesTypesAllowed, reporter)
                .toList();
        String containerNames = Arrays.toString(filters.stream().map(EquipmentsContainer::getContainerName).toList().toArray());
        Stream<Pair<String, List<IdentifiableAttributes>>> variablesContainersLists = Stream.of(Pair.of(containerNames, monitoredVariablesContainersLists))
                .filter(list -> !list.getRight().isEmpty());

        variablesContainersLists.forEach(variablesList -> {
            List<WeightedSensitivityVariable> variables = new ArrayList<>();
            if (variablesList.getRight().get(0).getType() == IdentifiableType.LOAD && distributionType == SensitivityAnalysisInputData.DistributionType.PROPORTIONAL_MAXP) {
                reporter.report(Report.builder()
                    .withKey("distributionTypeNotAllowedForLoadsContainer")
                    .withDefaultMessage("Distribution type ${distributionType} is not allowed for loads filter : filter is ignored")
                    .withValue("distributionType", distributionType.name())
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .build());
                return;
            }
            if (variablesList.getRight().get(0).getDistributionKey() == null && distributionType == SensitivityAnalysisInputData.DistributionType.VENTILATION) {
                reporter.report(Report.builder()
                    .withKey("distributionTypeAllowedOnlyForManualContainer")
                    .withDefaultMessage("Distribution type ${distributionType} is allowed only for manual filter : filter is ignored")
                    .withValue("distributionType", distributionType.name())
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .build());
                return;
            }
            for (IdentifiableAttributes identifiableAttributes : variablesList.getRight()) {
                switch (identifiableAttributes.getType()) {
                    case GENERATOR: {
                        Generator generator = network.getGenerator(identifiableAttributes.getId());
                        if (generator == null) {
                            throw new PowsyblException("Generator '" + identifiableAttributes.getId() + "' not found !!");
                        }
                        double weight = getGeneratorWeight(generator, distributionType, identifiableAttributes.getDistributionKey());
                        variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                        break;
                    }
                    case LOAD: {
                        Load load = network.getLoad(identifiableAttributes.getId());
                        if (load == null) {
                            throw new PowsyblException("Load '" + identifiableAttributes.getId() + "' not found !!");
                        }
                        double weight = getLoadWeight(load, distributionType, identifiableAttributes.getDistributionKey());
                        variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                        break;
                    }
                    default:
                        break;
                }
            }
            result.add(new SensitivityVariableSet(variablesList.getLeft() + " (" + distributionType.name() + ")", variables));
        });

        return result;
    }

    private List<SensitivityFactor> buildSensitivityFactorsFromVariablesSets(SensitivityAnalysisRunContext context, Network network, Reporter reporter,
                                                                             List<IdentifiableType> monitoredEquipmentsTypesAllowed,
                                                                             List<EquipmentsContainer> monitoredEquipmentsContainers,
                                                                             List<SensitivityVariableSet> variablesSets,
                                                                             List<Contingency> contingencies,
                                                                             SensitivityFunctionType sensitivityFunctionType,
                                                                             SensitivityVariableType sensitivityVariableType) {
        if (variablesSets.isEmpty()) {
            return List.of();
        }

        List<IdentifiableAttributes> monitoredEquipments = getMonitoredIdentifiablesFromContainers(context, network, monitoredEquipmentsContainers, monitoredEquipmentsTypesAllowed, reporter).collect(Collectors.toList());

        return getSensitivityFactorsFromEquipments(variablesSets.stream().map(SensitivityVariableSet::getId).collect(Collectors.toList()),
            monitoredEquipments, contingencies, sensitivityFunctionType, sensitivityVariableType, true);
    }

    private List<SensitivityFactor> buildSensitivityFactorsFromEquipments(SensitivityAnalysisRunContext context, Network network, Reporter reporter,
                                                                          List<IdentifiableType> monitoredEquipmentsTypesAllowed,
                                                                          List<EquipmentsContainer> monitoredEquipmentsContainers,
                                                                          List<IdentifiableType> equipmentsTypesAllowed,
                                                                          List<EquipmentsContainer> filters,
                                                                          List<Contingency> contingencies,
                                                                          SensitivityFunctionType sensitivityFunctionType,
                                                                          SensitivityVariableType sensitivityVariableType) {
        List<IdentifiableAttributes> equipments = getIdentifiablesFromContainers(context, filters, equipmentsTypesAllowed, reporter).toList();

        if (equipments.isEmpty()) {
            return List.of();
        }
        List<EquipmentsContainer> monitoredEquipementContainer = monitoredEquipmentsContainers.stream().toList();
        List<IdentifiableAttributes> monitoredEquipments = getMonitoredIdentifiablesFromContainers(context, network, monitoredEquipementContainer, monitoredEquipmentsTypesAllowed, reporter).toList();

        return getSensitivityFactorsFromEquipments(equipments.stream().map(IdentifiableAttributes::getId).collect(Collectors.toList()),
                monitoredEquipments, contingencies, sensitivityFunctionType, sensitivityVariableType, false);
    }

    private void buildSensitivityInjectionsSets(SensitivityAnalysisRunContext context, Network network, Reporter reporter) {
        List<SensitivityInjectionsSet> sensitivityInjectionsSets = context.getSensitivityAnalysisInputData().getSensitivityInjectionsSets();
        sensitivityInjectionsSets.forEach(sensitivityInjectionsSet -> {
            List<Contingency> cInjectionsSet = buildContingencies(context, sensitivityInjectionsSet.getContingencies(), reporter);
            List<SensitivityVariableSet> vInjectionsSets = buildSensitivityVariableSets(
                context, network, reporter,
                List.of(IdentifiableType.GENERATOR, IdentifiableType.LOAD),
                sensitivityInjectionsSet.getInjections(),
                sensitivityInjectionsSet.getDistributionType());
            List<SensitivityFactor> fInjectionsSet = buildSensitivityFactorsFromVariablesSets(
                context, network, reporter,
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityInjectionsSet.getMonitoredBranches(),
                vInjectionsSets,
                cInjectionsSet,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                SensitivityVariableType.INJECTION_ACTIVE_POWER);

            context.getSensitivityAnalysisInputs().addContingencies(cInjectionsSet);
            context.getSensitivityAnalysisInputs().addSensitivityVariableSets(vInjectionsSets);
            context.getSensitivityAnalysisInputs().addSensitivityFactors(fInjectionsSet);
        });
    }

    private void buildSensitivityInjections(SensitivityAnalysisRunContext context, Network network, Reporter reporter) {
        List<SensitivityInjection> sensitivityInjections = context.getSensitivityAnalysisInputData().getSensitivityInjections();
        sensitivityInjections.forEach(sensitivityInjection -> {
            List<Contingency> cInjections = buildContingencies(context, sensitivityInjection.getContingencies(), reporter);
            List<SensitivityFactor> fInjections = buildSensitivityFactorsFromEquipments(
                context, network, reporter,
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityInjection.getMonitoredBranches(),
                List.of(IdentifiableType.GENERATOR, IdentifiableType.LOAD),
                sensitivityInjection.getInjections(),
                cInjections,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                SensitivityVariableType.INJECTION_ACTIVE_POWER);

            context.getSensitivityAnalysisInputs().addContingencies(cInjections);
            context.getSensitivityAnalysisInputs().addSensitivityFactors(fInjections);
        });
    }

    private void buildSensitivityHVDCs(SensitivityAnalysisRunContext context, Network network, Reporter reporter) {
        List<SensitivityHVDC> sensitivityHVDCs = context.getSensitivityAnalysisInputData().getSensitivityHVDCs();
        sensitivityHVDCs.forEach(sensitivityHVDC -> {
            List<Contingency> cHVDC = buildContingencies(context, sensitivityHVDC.getContingencies(), reporter);
            SensitivityFunctionType sensitivityFunctionType = sensitivityHVDC.getSensitivityType() == SensitivityAnalysisInputData.SensitivityType.DELTA_MW
                    ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                    : SensitivityFunctionType.BRANCH_CURRENT_1;
            // TODO : SensitivityType.DELTA_A is not yet supported with OpenLoadFlow
            // check to be removed further ...
            if (sensitivityHVDC.getSensitivityType() == SensitivityAnalysisInputData.SensitivityType.DELTA_A &&
                StringUtils.equals("OpenLoadFlow", context.getProvider())) {
                reporter.report(Report.builder()
                    .withKey("sensitivityTypeNotYetSupported")
                    .withDefaultMessage("Sensitivity type ${sensitivityType} is not yet supported with OpenLoadFlow : type forced to ${replacingSensitivityType}")
                    .withValue("sensitivityType", sensitivityHVDC.getSensitivityType().name())
                    .withValue("replacingSensitivityType", SensitivityAnalysisInputData.SensitivityType.DELTA_MW.name())
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .build());
                sensitivityFunctionType = SensitivityFunctionType.BRANCH_ACTIVE_POWER_1;
            }

            List<SensitivityFactor> fHVDC = buildSensitivityFactorsFromEquipments(
                context, network, reporter,
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityHVDC.getMonitoredBranches(),
                List.of(IdentifiableType.HVDC_LINE),
                sensitivityHVDC.getHvdcs(),
                cHVDC,
                sensitivityFunctionType,
                SensitivityVariableType.HVDC_LINE_ACTIVE_POWER);

            context.getSensitivityAnalysisInputs().addContingencies(cHVDC);
            context.getSensitivityAnalysisInputs().addSensitivityFactors(fHVDC);
        });
    }

    private void buildSensitivityPSTs(SensitivityAnalysisRunContext context, Network network, Reporter reporter) {
        List<SensitivityPST> sensitivityPSTs = context.getSensitivityAnalysisInputData().getSensitivityPSTs();
        sensitivityPSTs.forEach(sensitivityPST -> {
            List<Contingency> cPST = buildContingencies(context, sensitivityPST.getContingencies(), reporter);
            List<SensitivityFactor> fPST = buildSensitivityFactorsFromEquipments(
                context, network, reporter,
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityPST.getMonitoredBranches(),
                List.of(IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityPST.getPsts(),
                cPST,
                sensitivityPST.getSensitivityType() == SensitivityAnalysisInputData.SensitivityType.DELTA_MW
                    ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                    : SensitivityFunctionType.BRANCH_CURRENT_1,
                SensitivityVariableType.TRANSFORMER_PHASE);

            context.getSensitivityAnalysisInputs().addContingencies(cPST);
            context.getSensitivityAnalysisInputs().addSensitivityFactors(fPST);
        });
    }

    private void buildSensitivityNodes(SensitivityAnalysisRunContext context, Network network, Reporter reporter) {
        List<SensitivityNodes> sensitivityNodes = context.getSensitivityAnalysisInputData().getSensitivityNodes();
        // TODO: nodes sensitivity is only available with OpenLoadFlow
        // check to be removed further ...
        if (!sensitivityNodes.isEmpty() && !StringUtils.equals("OpenLoadFlow", context.getProvider())) {
            reporter.report(Report.builder()
                .withKey("sensitivityNodesComputationNotSupported")
                .withDefaultMessage("Sensitivity nodes computation is only supported with OpenLoadFlow : computation ignored")
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
            return;
        }
        sensitivityNodes.forEach(sensitivityNode -> {
            List<Contingency> cNodes = buildContingencies(context, sensitivityNode.getContingencies(), reporter);
            List<SensitivityFactor> fNodes = buildSensitivityFactorsFromEquipments(
                context, network, reporter,
                List.of(IdentifiableType.VOLTAGE_LEVEL),
                sensitivityNode.getMonitoredVoltageLevels(),
                List.of(IdentifiableType.GENERATOR, IdentifiableType.TWO_WINDINGS_TRANSFORMER,
                    IdentifiableType.HVDC_CONVERTER_STATION, IdentifiableType.STATIC_VAR_COMPENSATOR, IdentifiableType.SHUNT_COMPENSATOR),
                sensitivityNode.getEquipmentsInVoltageRegulation(),
                cNodes,
                SensitivityFunctionType.BUS_VOLTAGE,
                SensitivityVariableType.BUS_TARGET_VOLTAGE);

            context.getSensitivityAnalysisInputs().addContingencies(cNodes);
            context.getSensitivityAnalysisInputs().addSensitivityFactors(fNodes);
        });
    }

    public Long getFactorsCount(Map<String, List<UUID>> ids, UUID networkUuid, String variantId, Boolean isInjectionsSet) {
        Long containersAttributesCount = 1L;
        if (Boolean.TRUE.equals(isInjectionsSet)) {
            containersAttributesCount *= ids.get(INJECTIONS).size();
            ids.remove(INJECTIONS);
        }
        containersAttributesCount = getFactorsCount(ids, networkUuid, variantId, containersAttributesCount);
        return containersAttributesCount;
    }

    public void build(SensitivityAnalysisRunContext context, Network network, Reporter reporter) {
        try {
            buildSensitivityInjectionsSets(context, network, reporter);
            buildSensitivityInjections(context, network, reporter);
            buildSensitivityHVDCs(context, network, reporter);
            buildSensitivityPSTs(context, network, reporter);
            buildSensitivityNodes(context, network, reporter);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg == null) {
                msg = ex.getClass().getName();
            }
            LOGGER.error("Could not translate running context, got exception", ex);
            reporter.report(Report.builder()
                .withKey("sensitivityInputParametersTranslationFailure")
                .withDefaultMessage("Failure while building inputs, exception : ${exception}")
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .withValue("exception", msg)
                .build());
            LOGGER.error("Running context translation failure, report added");
            throw ex;
        }
    }
}

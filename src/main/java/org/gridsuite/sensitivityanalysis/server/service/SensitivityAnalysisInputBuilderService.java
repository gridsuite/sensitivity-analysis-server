/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
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
    private final ActionsService actionsService;
    private final FilterService filterService;

    private static final String NAMES = "names";

    public SensitivityAnalysisInputBuilderService(ActionsService actionsService, FilterService filterService) {
        this.actionsService = actionsService;
        this.filterService = filterService;
    }

    private List<Contingency> goGetContingencies(List<EquipmentsContainer> contingencyListIdent, UUID networkUuid, String variantId, ReportNode reporter) {
        List<UUID> ids = contingencyListIdent.stream().map(EquipmentsContainer::getContainerId).toList();
        Contengencies contengencies = actionsService.getContingencyList(ids, networkUuid, variantId);
        if (contengencies == null) {
            return List.of();
        }

        contengencies.getContingenciesNotFound().forEach(id -> {
            EquipmentsContainer container = contingencyListIdent.stream().filter(c -> c.getContainerId().equals(id)).findFirst().orElseThrow();
            LOGGER.error("Could not get contingencies from {}", container.getContainerName());
            reporter.newReportNode()
                .withMessageTemplate("contingencyTranslationFailure", "Could not get contingencies from contingencyListIdent ${name} : not found")
                .withUntypedValue("name", container.getContainerName())
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
        });
        return contengencies.getContingenciesFound() == null ? List.of() : contengencies.getContingenciesFound();
    }

    private List<Contingency> buildContingencies(UUID networkUuid, String variantId, List<EquipmentsContainer> contingencyListsContainerIdents, ReportNode reporter) {
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

    private List<IdentifiableAttributes> goGetIdentifiables(List<EquipmentsContainer> filters, UUID networkUuid, String variantId, ReportNode reporter) {
        String containersNames = getContainerNames(filters);
        try {
            //extract container id from filters
            List<UUID> filterIds = filters.stream().map(EquipmentsContainer::getContainerId).toList();
            return filterService.getIdentifiablesFromFilters(filterIds, networkUuid, variantId);
        } catch (Exception ex) {
            LOGGER.error("Could not get identifiables from filter " + containersNames, ex);
            reporter.newReportNode()
                .withMessageTemplate("filterTranslationFailure", "Could not get identifiables from filters ${names} : ${exception}")
                .withUntypedValue("exception", ex.getMessage())
                .withUntypedValue(NAMES, containersNames)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
            return List.of();
        }
    }

    private Stream<IdentifiableAttributes> getIdentifiablesFromContainers(SensitivityAnalysisRunContext context, List<EquipmentsContainer> filters,
                                                                          List<IdentifiableType> equipmentsTypesAllowed, ReportNode reporter) {
        String containersNames = getContainerNames(filters);
        List<IdentifiableAttributes> listIdentifiableAttributes = goGetIdentifiables(filters, context.getNetworkUuid(), context.getVariantId(), reporter);

        // check that monitored equipments type is allowed
        if (!listIdentifiableAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            reporter.newReportNode()
                .withMessageTemplate("badEquipmentType", "Equipments type in filter with name=${names} should be ${expectedType} : filter is ignored")
                .withUntypedValue(NAMES, containersNames)
                .withUntypedValue(EXPECTED_TYPE, equipmentsTypesAllowed.toString())
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
            return Stream.empty();
        }

        return listIdentifiableAttributes.stream();
    }

    private String getContainerNames(List<EquipmentsContainer> filters) {
        List<String> containerNamesList = filters.stream().map(EquipmentsContainer::getContainerName).toList();
        return "[" + String.join(", ", containerNamesList) + "]";
    }

    private Stream<IdentifiableAttributes> getMonitoredIdentifiablesFromContainers(SensitivityAnalysisRunContext context, Network network, List<EquipmentsContainer> filters, List<IdentifiableType> equipmentsTypesAllowed, ReportNode reporter) {
        String containersNames = getContainerNames(filters);
        List<IdentifiableAttributes> listIdentAttributes = goGetIdentifiables(filters, context.getNetworkUuid(), context.getVariantId(), reporter);

        // check that monitored equipments type is allowed
        if (!listIdentAttributes.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
            reporter.newReportNode()
                .withMessageTemplate("badMonitoredEquipmentType", "Monitored equipments type in filter with name=${names} should be ${expectedType} : filter is ignored")
                .withUntypedValue(NAMES, containersNames)
                .withUntypedValue(EXPECTED_TYPE, equipmentsTypesAllowed.toString())
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
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

    private List<List<SensitivityFactor>> getSensitivityFactorsFromEquipments(List<String> variableIds,
                                                                              List<IdentifiableAttributes> monitoredEquipments,
                                                                              List<Contingency> contingencies,
                                                                              SensitivityFunctionType sensitivityFunctionType,
                                                                              SensitivityVariableType sensitivityVariableType,
                                                                              boolean variableSet) {
        return monitoredEquipments
            .stream()
            .flatMap(monitoredEquipment -> variableIds
                .stream()
                .map(varId -> {
                    List<SensitivityFactor> factors = new ArrayList<>(contingencies.size() + 1);
                    factors.add(new SensitivityFactor(
                        sensitivityFunctionType,
                        monitoredEquipment.getId(),
                        sensitivityVariableType,
                        varId,
                        variableSet,
                        ContingencyContext.none())
                    );

                    // if contingencies are given: creation, for each monitored equipment, of one sensitivity factor for each contingency
                    contingencies.forEach(contingency ->
                        factors.add(new SensitivityFactor(
                            sensitivityFunctionType,
                            monitoredEquipment.getId(),
                            sensitivityVariableType,
                            varId,
                            variableSet,
                            ContingencyContext.specificContingency(contingency.getId())))
                    );
                    return factors;
                }))
            .toList();
    }

    private List<SensitivityVariableSet> buildSensitivityVariableSets(SensitivityAnalysisRunContext context, Network network, ReportNode reporter,
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
                reporter.newReportNode()
                    .withMessageTemplate("distributionTypeNotAllowedForLoadsContainer", "Distribution type ${distributionType} is not allowed for loads filter : filter is ignored")
                    .withUntypedValue("distributionType", distributionType.name())
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .add();
                return;
            }
            if (variablesList.getRight().get(0).getDistributionKey() == null && distributionType == SensitivityAnalysisInputData.DistributionType.VENTILATION) {
                reporter.newReportNode()
                    .withMessageTemplate("distributionTypeAllowedOnlyForManualContainer", "Distribution type ${distributionType} is allowed only for manual filter : filter is ignored")
                    .withUntypedValue("distributionType", distributionType.name())
                    .withSeverity(TypedValue.WARN_SEVERITY)
                    .add();
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

    private List<List<SensitivityFactor>> buildSensitivityFactorsFromVariablesSets(SensitivityAnalysisRunContext context,
                                                                                   Network network, ReportNode reporter,
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

    private List<List<SensitivityFactor>> buildSensitivityFactorsFromEquipments(SensitivityAnalysisRunContext context,
                                                                                Network network, ReportNode reporter,
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

    private void buildSensitivityInjectionsSets(SensitivityAnalysisRunContext context, Network network, ReportNode reporter) {
        List<SensitivityInjectionsSet> sensitivityInjectionsSets = context.getSensitivityAnalysisInputData().getSensitivityInjectionsSets();
        sensitivityInjectionsSets.forEach(sensitivityInjectionsSet -> {
            List<Contingency> cInjectionsSet = buildContingencies(context.getNetworkUuid(), context.getVariantId(), sensitivityInjectionsSet.getContingencies(), reporter);
            List<SensitivityVariableSet> vInjectionsSets = buildSensitivityVariableSets(context,
                network, reporter,
                List.of(IdentifiableType.GENERATOR, IdentifiableType.LOAD),
                sensitivityInjectionsSet.getInjections(),
                sensitivityInjectionsSet.getDistributionType());
            List<List<SensitivityFactor>> fInjectionsSet = buildSensitivityFactorsFromVariablesSets(
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

    private void buildSensitivityInjections(SensitivityAnalysisRunContext context, Network network, ReportNode reporter) {
        List<SensitivityInjection> sensitivityInjections = context.getSensitivityAnalysisInputData().getSensitivityInjections();
        sensitivityInjections.forEach(sensitivityInjection -> {
            List<Contingency> cInjections = buildContingencies(context.getNetworkUuid(), context.getVariantId(), sensitivityInjection.getContingencies(), reporter);
            List<List<SensitivityFactor>> fInjections = buildSensitivityFactorsFromEquipments(
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

    private void buildSensitivityHVDCs(SensitivityAnalysisRunContext context, Network network, ReportNode reporter) {
        List<SensitivityHVDC> sensitivityHVDCs = context.getSensitivityAnalysisInputData().getSensitivityHVDCs();
        sensitivityHVDCs.forEach(sensitivityHVDC -> {
            List<Contingency> cHVDC = buildContingencies(context.getNetworkUuid(), context.getVariantId(), sensitivityHVDC.getContingencies(), reporter);
            SensitivityFunctionType sensitivityFunctionType = sensitivityHVDC.getSensitivityType() == SensitivityAnalysisInputData.SensitivityType.DELTA_MW
                ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                : SensitivityFunctionType.BRANCH_CURRENT_1;

            List<List<SensitivityFactor>> fHVDC = buildSensitivityFactorsFromEquipments(
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

    private void buildSensitivityPSTs(SensitivityAnalysisRunContext context, Network network, ReportNode reporter) {
        List<SensitivityPST> sensitivityPSTs = context.getSensitivityAnalysisInputData().getSensitivityPSTs();
        sensitivityPSTs.forEach(sensitivityPST -> {
            List<Contingency> cPST = buildContingencies(context.getNetworkUuid(), context.getVariantId(), sensitivityPST.getContingencies(), reporter);
            List<List<SensitivityFactor>> fPST = buildSensitivityFactorsFromEquipments(
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

    private void buildSensitivityNodes(SensitivityAnalysisRunContext context, Network network, ReportNode reporter) {
        List<SensitivityNodes> sensitivityNodes = context.getSensitivityAnalysisInputData().getSensitivityNodes();
        // TODO: nodes sensitivity is only available with OpenLoadFlow
        // check to be removed further ...
        if (!sensitivityNodes.isEmpty() && !StringUtils.equals("OpenLoadFlow", context.getProvider())) {
            reporter.newReportNode()
                .withMessageTemplate("sensitivityNodesComputationNotSupported", "Sensitivity nodes computation is only supported with OpenLoadFlow : computation ignored")
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
            return;
        }
        sensitivityNodes.forEach(sensitivityNode -> {
            List<Contingency> cNodes = buildContingencies(context.getNetworkUuid(), context.getVariantId(), sensitivityNode.getContingencies(), reporter);
            List<List<SensitivityFactor>> fNodes = buildSensitivityFactorsFromEquipments(
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

    public void build(SensitivityAnalysisRunContext context, Network network, ReportNode reporter) {
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
            reporter.newReportNode()
                .withMessageTemplate("sensitivityInputParametersTranslationFailure", "Failure while building inputs, exception : ${exception}")
                .withUntypedValue("exception", msg)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
            LOGGER.error("Running context translation failure, report added");
            throw ex;
        }
    }
}

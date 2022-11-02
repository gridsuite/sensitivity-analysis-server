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
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class SensitivityAnalysisInputBuilder {
    private static final String EXPECTED_TYPE = "expectedType";

    private final Network network;
    private final SensitivityAnalysisRunContext context;
    private final ActionsService actionsService;
    private final FilterService filterService;

    private List<Contingency> contingencies = new ArrayList<>();
    private List<SensitivityFactor> factors = new ArrayList<>();
    private List<SensitivityVariableSet> variablesSets = new ArrayList<>();

    public SensitivityAnalysisInputBuilder(Network network, SensitivityAnalysisRunContext context, ActionsService actionsService, FilterService filterService) {
        this.network = network;
        this.context = context;
        this.actionsService = actionsService;
        this.filterService = filterService;
    }

    private List<Contingency> buildContingencies(List<SensitivityAnalysisInputData.Ident> contingencyListsIdents) {
        return contingencyListsIdents.stream()
            .flatMap(contingencyListIdent -> actionsService.getContingencyList(contingencyListIdent.getId(), context.getNetworkUuid(), context.getVariantId()).stream())
            .collect(Collectors.toList());
    }

    private double getGeneratorWeight(Generator generator, SensitivityAnalysisInputData.DistributionType distributionType, Double distributionKey) {
        double weight;
        switch (distributionType) {
            case PROPORTIONAL:
                weight = !Double.isNaN(generator.getTerminal().getP()) ? generator.getTerminal().getP() : 0.;
                break;
            case PROPORTIONAL_MAXP:
                weight = generator.getMaxP();
                break;
            case REGULAR:
                weight = 1.;
                break;
            case VENTILATION:
                if (distributionKey == null) {
                    throw new PowsyblException("Distribution key required for VENTILATION distribution type !!");
                }
                return distributionKey;
            default:
                throw new UnsupportedOperationException("Distribution type not allowed for generator");
        }
        return weight;
    }

    private double getLoadWeight(Load load, SensitivityAnalysisInputData.DistributionType distributionType, Double distributionKey) {
        double weight;
        switch (distributionType) {
            case PROPORTIONAL:
            case PROPORTIONAL_MAXP: // simpler to use the same enum for generator and load
                weight = load.getP0();
                break;
            case REGULAR:
                weight = 1.;
                break;
            case VENTILATION:
                if (distributionKey == null) {
                    throw new PowsyblException("Distribution key required for VENTILATION distribution type !!");
                }
                return distributionKey;
            default:
                throw new UnsupportedOperationException("Distribution type not allowed for load");
        }
        return weight;
    }

    private List<SensitivityVariableSet> buildSensitivityVariableSets(List<IdentifiableType> variablesTypesAllowed,
                                                                      List<SensitivityAnalysisInputData.Ident> variablesFiltersListIdents,
                                                                      SensitivityAnalysisInputData.DistributionType distributionType,
                                                                      Reporter reporter) {
        List<SensitivityVariableSet> result = new ArrayList<>();

        List<List<IdentifiableAttributes>> variablesFiltersLists = variablesFiltersListIdents.stream()
            .map(variablesFilterIdent -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(variablesFilterIdent.getId(), context.getNetworkUuid(), context.getVariantId());
                if (list.stream().allMatch(i -> variablesTypesAllowed.contains(i.getType()))) {
                    return list;
                } else {
                    reporter.report(Report.builder()
                        .withKey("badEquipmentType")
                        .withDefaultMessage("Equipments type in filter with name=${name} should be ${expectedType} : filter is ignored")
                        .withValue("name", variablesFilterIdent.getName())
                        .withValue(EXPECTED_TYPE, variablesTypesAllowed.toString())
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .build());
                    return Collections.<IdentifiableAttributes>emptyList();
                }
            })
            .collect(Collectors.toList());

        variablesFiltersLists.forEach(variablesList -> {
            List<WeightedSensitivityVariable> variables = new ArrayList<>();
            if (!variablesList.isEmpty()) {
                for (IdentifiableAttributes identifiableAttributes : variablesList) {
                    if (identifiableAttributes.getType() == IdentifiableType.GENERATOR) {
                        Generator generator = network.getGenerator(identifiableAttributes.getId());
                        if (generator != null) {
                            double weight = getGeneratorWeight(generator, distributionType, identifiableAttributes.getDistributionKey());
                            variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                        } else {
                            throw new PowsyblException("Generator '" + identifiableAttributes.getId() + "' not found !!");
                        }
                    } else if (identifiableAttributes.getType() == IdentifiableType.LOAD) {
                        Load load = network.getLoad(identifiableAttributes.getId());
                        if (load != null) {
                            double weight = getLoadWeight(load, distributionType, identifiableAttributes.getDistributionKey());
                            variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                        } else {
                            throw new PowsyblException("Load '" + identifiableAttributes.getId() + "' not found !!");
                        }
                    }
                }
                result.add(new SensitivityVariableSet(UUID.randomUUID().toString(), variables));
            }
        });

        return result;
    }

    private List<SensitivityFactor> buildSensitivityFactorsFromVariablesSets(List<IdentifiableType> monitoredEquipmentsTypesAllowed,
                                                                             List<SensitivityAnalysisInputData.Ident> monitoredEquipmentsListIdents,
                                                                             List<SensitivityVariableSet> variablesSets,
                                                                             List<Contingency> contingencies,
                                                                             SensitivityFunctionType sensitivityFunctionType,
                                                                             SensitivityVariableType sensitivityVariableType,
                                                                             Reporter reporter) {
        List<SensitivityFactor> result = new ArrayList<>();

        List<IdentifiableAttributes> monitoredEquipments = monitoredEquipmentsListIdents.stream()
            .flatMap(equimentsListIdent -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(equimentsListIdent.getId(), context.getNetworkUuid(), context.getVariantId());
                // check that monitored equipments type is allowed
                if (list.stream().allMatch(i -> monitoredEquipmentsTypesAllowed.contains(i.getType()))) {
                    return list.stream();
                } else {
                    reporter.report(Report.builder()
                        .withKey("badMonitoredEquipmentType")
                        .withDefaultMessage("Monitored equipments type in filter with name=${name} should be ${expectedType} : filter is ignored")
                        .withValue("name", equimentsListIdent.getName())
                        .withValue(EXPECTED_TYPE, monitoredEquipmentsTypesAllowed.toString())
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .build());
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());

        monitoredEquipments.forEach(monitoredEquipment -> {
            if (!variablesSets.isEmpty()) {
                if (contingencies.isEmpty()) {
                    // if no contingency are given: creation, for each equipment, of one sensitivity factor for each variable set
                    variablesSets.forEach(variableSet ->
                        result.add(new SensitivityFactor(
                            sensitivityFunctionType,
                            monitoredEquipment.getId(),
                            sensitivityVariableType,
                            variableSet.getId(),
                            true,
                            ContingencyContext.none()))
                    );
                } else {
                    // if contingencies are given: creation, for each equipment, of one sensitivity factor for each variable set and for each contingency
                    contingencies.forEach(contingency ->
                        variablesSets.forEach(variableSet ->
                            result.add(new SensitivityFactor(
                                sensitivityFunctionType,
                                monitoredEquipment.getId(),
                                sensitivityVariableType,
                                variableSet.getId(),
                                true,
                                ContingencyContext.specificContingency(contingency.getId())))
                        ));
                }
            }
        });

        return result;
    }

    private List<SensitivityFactor> buildSensitivityFactorsFromEquipments(List<IdentifiableType> monitoredEquipmentsTypesAllowed,
                                                                          List<SensitivityAnalysisInputData.Ident> monitoredEquipmentsListIdents,
                                                                          List<IdentifiableType> equipmentsTypesAllowed,
                                                                          List<SensitivityAnalysisInputData.Ident> equipmentsListIdents,
                                                                          List<Contingency> contingencies,
                                                                          SensitivityFunctionType sensitivityFunctionType,
                                                                          SensitivityVariableType sensitivityVariableType,
                                                                          Reporter reporter) {
        List<SensitivityFactor> result = new ArrayList<>();

        List<IdentifiableAttributes> equipments = equipmentsListIdents.stream()
            .flatMap(equipmentsListIdent -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(equipmentsListIdent.getId(), context.getNetworkUuid(), context.getVariantId());
                if (list.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
                    return list.stream();
                } else {
                    reporter.report(Report.builder()
                        .withKey("badEquipmentType")
                        .withDefaultMessage("Equipments type in filter with name=${name} should be ${expectedType} : filter is ignored")
                        .withValue("name", equipmentsListIdent.getName())
                        .withValue(EXPECTED_TYPE, equipmentsTypesAllowed.toString())
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .build());
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());

        List<IdentifiableAttributes> monitoredEquipments = monitoredEquipmentsListIdents.stream()
            .flatMap(monitoredEquimentsListIdent -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(monitoredEquimentsListIdent.getId(), context.getNetworkUuid(), context.getVariantId());
                // check that monitored equipments type is allowed
                if (list.stream().allMatch(i -> monitoredEquipmentsTypesAllowed.contains(i.getType()))) {
                    return list.stream();
                } else {
                    reporter.report(Report.builder()
                        .withKey("badMonitoredEquipmentType")
                        .withDefaultMessage("Monitored equipments type in filter with name=${name} should be ${expectedType} : filter is ignored")
                        .withValue("name", monitoredEquimentsListIdent.getName())
                        .withValue(EXPECTED_TYPE, monitoredEquipmentsTypesAllowed.toString())
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .build());
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());

        monitoredEquipments.forEach(monitoredEquipment -> {
            if (!equipments.isEmpty()) {
                if (contingencies.isEmpty()) {
                    // if no contingency are given: creation, for each monitored equipment, of one sensitivity factor for each equipment
                    equipments.forEach(equipment ->
                        result.add(new SensitivityFactor(
                            sensitivityFunctionType,
                            monitoredEquipment.getId(),
                            sensitivityVariableType,
                            equipment.getId(),
                            false,
                            ContingencyContext.none()))
                    );
                } else {
                    // if contingencies are given: creation, for each monitored equipment, of one sensitivity factor for each contingency
                    contingencies.forEach(contingency ->
                        equipments.forEach(equipment ->
                            result.add(new SensitivityFactor(
                                sensitivityFunctionType,
                                monitoredEquipment.getId(),
                                sensitivityVariableType,
                                equipment.getId(),
                                false,
                                ContingencyContext.specificContingency(contingency.getId())))
                        ));
                }
            }
        });

        return result;
    }

    private void buildSensitivityInjectionsSets(Reporter reporter) {
        List<SensitivityAnalysisInputData.SensitivityInjectionsSet> sensitivityInjectionsSets = context.getSensitivityAnalysisInputData().getSensitivityInjectionsSets();
        sensitivityInjectionsSets.forEach(sensitivityInjectionsSet -> {
            List<Contingency> cInjectionsSet = buildContingencies(sensitivityInjectionsSet.getContingencies());
            List<SensitivityVariableSet> vInjectionsSets = buildSensitivityVariableSets(
                List.of(IdentifiableType.GENERATOR, IdentifiableType.LOAD),
                sensitivityInjectionsSet.getInjections(),
                sensitivityInjectionsSet.getDistributionType(),
                reporter);
            List<SensitivityFactor> fInjectionsSet = buildSensitivityFactorsFromVariablesSets(
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityInjectionsSet.getMonitoredBranches(),
                vInjectionsSets,
                cInjectionsSet,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                SensitivityVariableType.INJECTION_ACTIVE_POWER,
                reporter);

            contingencies.addAll(cInjectionsSet);
            variablesSets.addAll(vInjectionsSets);
            factors.addAll(fInjectionsSet);
        });
    }

    private void buildSensitivityInjections(Reporter reporter) {
        List<SensitivityAnalysisInputData.SensitivityInjection> sensitivityInjections = context.getSensitivityAnalysisInputData().getSensitivityInjections();
        sensitivityInjections.forEach(sensitivityInjection -> {
            List<Contingency> cInjections = buildContingencies(sensitivityInjection.getContingencies());
            List<SensitivityFactor> fInjections = buildSensitivityFactorsFromEquipments(
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityInjection.getMonitoredBranches(),
                List.of(IdentifiableType.GENERATOR, IdentifiableType.LOAD),
                sensitivityInjection.getInjections(),
                cInjections,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                SensitivityVariableType.INJECTION_ACTIVE_POWER,
                reporter);

            contingencies.addAll(cInjections);
            factors.addAll(fInjections);
        });
    }

    private void buildSensitivityHVDCs(Reporter reporter) {
        List<SensitivityAnalysisInputData.SensitivityHVDC> sensitivityHVDCs = context.getSensitivityAnalysisInputData().getSensitivityHVDCs();
        sensitivityHVDCs.forEach(sensitivityHVDC -> {
            List<Contingency> cHVDC = buildContingencies(sensitivityHVDC.getContingencies());
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
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityHVDC.getMonitoredBranches(),
                List.of(IdentifiableType.HVDC_LINE),
                sensitivityHVDC.getHvdcs(),
                cHVDC,
                sensitivityFunctionType,
                SensitivityVariableType.HVDC_LINE_ACTIVE_POWER,
                reporter);

            contingencies.addAll(cHVDC);
            factors.addAll(fHVDC);
        });
    }

    private void buildSensitivityPSTs(Reporter reporter) {
        List<SensitivityAnalysisInputData.SensitivityPST> sensitivityPSTs = context.getSensitivityAnalysisInputData().getSensitivityPSTs();
        sensitivityPSTs.forEach(sensitivityPST -> {
            List<Contingency> cPST = buildContingencies(sensitivityPST.getContingencies());
            List<SensitivityFactor> fPST = buildSensitivityFactorsFromEquipments(
                List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityPST.getMonitoredBranches(),
                List.of(IdentifiableType.TWO_WINDINGS_TRANSFORMER),
                sensitivityPST.getPsts(),
                cPST,
                sensitivityPST.getSensitivityType() == SensitivityAnalysisInputData.SensitivityType.DELTA_MW
                    ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                    : SensitivityFunctionType.BRANCH_CURRENT_1,
                SensitivityVariableType.TRANSFORMER_PHASE,
                reporter);

            contingencies.addAll(cPST);
            factors.addAll(fPST);
        });
    }

    private void buildSensitivityNodes(Reporter reporter) {
        List<SensitivityAnalysisInputData.SensitivityNodes> sensitivityNodes = context.getSensitivityAnalysisInputData().getSensitivityNodes();
        // nodes sensitivity is only available with OpenLoadFlow
        if (!sensitivityNodes.isEmpty() && !StringUtils.equals("OpenLoadFlow", context.getProvider())) {
            reporter.report(Report.builder()
                .withKey("sensitivityNodesComputationNotSupported")
                .withDefaultMessage("Sensitivity nodes computation is only supported with OpenLoadFlow : computation ignored")
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
            return;
        }
        sensitivityNodes.forEach(sensitivityNode -> {
            List<Contingency> cNodes = buildContingencies(sensitivityNode.getContingencies());
            List<SensitivityFactor> fNodes = buildSensitivityFactorsFromEquipments(
                List.of(IdentifiableType.VOLTAGE_LEVEL),
                sensitivityNode.getMonitoredVoltageLevels(),
                List.of(IdentifiableType.GENERATOR, IdentifiableType.TWO_WINDINGS_TRANSFORMER,
                    IdentifiableType.HVDC_CONVERTER_STATION, IdentifiableType.STATIC_VAR_COMPENSATOR, IdentifiableType.SHUNT_COMPENSATOR),
                sensitivityNode.getEquipmentsInVoltageRegulation(),
                cNodes,
                SensitivityFunctionType.BUS_VOLTAGE,
                SensitivityVariableType.BUS_TARGET_VOLTAGE,
                reporter);

            contingencies.addAll(cNodes);
            factors.addAll(fNodes);
        });
    }

    public void build(Reporter reporter) {
        buildSensitivityInjectionsSets(reporter);
        buildSensitivityInjections(reporter);
        buildSensitivityHVDCs(reporter);
        buildSensitivityPSTs(reporter);
        buildSensitivityNodes(reporter);
    }

    public List<Contingency> getContingencies() {
        return contingencies.stream().distinct().collect(Collectors.toList());
    }

    public List<SensitivityFactor> getFactors() {
        return factors;
    }

    public List<SensitivityVariableSet> getVariableSets() {
        return variablesSets;
    }
}

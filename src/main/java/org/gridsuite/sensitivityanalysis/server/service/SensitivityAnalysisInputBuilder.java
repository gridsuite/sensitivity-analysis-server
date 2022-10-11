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

    private List<Contingency> buildContingencies(List<UUID> contingencyListUuids) {
        return contingencyListUuids.stream()
            .flatMap(contingencyListUuid -> actionsService.getContingencyList(contingencyListUuid, context.getNetworkUuid(), context.getVariantId()).stream())
            .collect(Collectors.toList());
    }

    private double getGeneratorWeight(Generator generator, SensitivityAnalysisInputData.DistributionType distributionType) {
        double weight;
        switch (distributionType) {
            case PROPORTIONAL:
                weight = generator.getTerminal().getP();
                break;
            case PROPORTIONAL_MAXP:
                weight = generator.getMaxP();
                break;
            case REGULAR:
                weight = 1.;
                break;
            case VENTILATION:
                // TODO : manual filter not yet implemented
                // when this will be the case, IdentifiableAttributes should contain the manual coefficient associated to the equipment id
                throw new UnsupportedOperationException("Not yet implemented");
            default:
                throw new UnsupportedOperationException("Distribution type not allowed for generator");
        }
        return weight;
    }

    private double getLoadWeight(Load load, SensitivityAnalysisInputData.DistributionType distributionType) {
        double weight;
        switch (distributionType) {
            case PROPORTIONAL:
                weight = load.getP0();
                break;
            case REGULAR:
                weight = 1.;
                break;
            case VENTILATION:
                // TODO : manual filter not yet implemented
                // when this will be the case, IdentifiableAttributes should contain the manual coefficient associated to the equipment id
                throw new UnsupportedOperationException("Not yet implemented");
            default:
                throw new UnsupportedOperationException("Distribution type not allowed for load");
        }
        return weight;
    }

    private List<SensitivityVariableSet> buildSensitivityVariableSets(List<IdentifiableType> variablesTypesAllowed,
                                                                      List<UUID> variablesFiltersListUuids,
                                                                      SensitivityAnalysisInputData.DistributionType distributionType,
                                                                      Reporter reporter) {
        List<SensitivityVariableSet> result = new ArrayList<>();

        List<List<IdentifiableAttributes>> variablesFiltersLists = variablesFiltersListUuids.stream()
            .map(variablesFilterUuid -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(variablesFilterUuid, context.getNetworkUuid(), context.getVariantId());
                if (list.stream().allMatch(i -> variablesTypesAllowed.contains(i.getType()))) {
                    return list;
                } else {
                    reporter.report(Report.builder()
                        .withKey("badEquipmentType")
                        .withDefaultMessage("Equipments type in filter with id=${id} should be ${expectedType} : filter is ignored")
                        .withValue("id", variablesFilterUuid.toString())
                        .withValue("expectedType", variablesTypesAllowed.toString())
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
                            double weight = getGeneratorWeight(generator, distributionType);
                            variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                        } else {
                            throw new PowsyblException("Generator '" + identifiableAttributes.getId() + "' not found !!");
                        }
                    } else if (identifiableAttributes.getType() == IdentifiableType.LOAD) {
                        Load load = network.getLoad(identifiableAttributes.getId());
                        if (load != null) {
                            double weight = getLoadWeight(load, distributionType);
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
                                                                             List<UUID> monitoredEquipmentsListUuids,
                                                                             List<SensitivityVariableSet> variablesSets,
                                                                             List<Contingency> contingencies,
                                                                             SensitivityFunctionType sensitivityFunctionType,
                                                                             SensitivityVariableType sensitivityVariableType,
                                                                             Reporter reporter) {
        List<SensitivityFactor> result = new ArrayList<>();

        List<IdentifiableAttributes> monitoredEquipments = monitoredEquipmentsListUuids.stream()
            .flatMap(equimentsListUuid -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(equimentsListUuid, context.getNetworkUuid(), context.getVariantId());
                // check that monitored equipments type is allowed
                if (list.stream().allMatch(i -> monitoredEquipmentsTypesAllowed.contains(i.getType()))) {
                    return list.stream();
                } else {
                    reporter.report(Report.builder()
                        .withKey("badMonitoredEquipmentType")
                        .withDefaultMessage("Monitored equipments type in filter with id=${id} should be ${expectedType} : filter is ignored")
                        .withValue("id", equimentsListUuid.toString())
                        .withValue("expectedType", monitoredEquipmentsTypesAllowed.toString())
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
                                                                          List<UUID> monitoredEquipmentsListUuids,
                                                                          List<IdentifiableType> equipmentsTypesAllowed,
                                                                          List<UUID> equipmentsListUuids,
                                                                          List<Contingency> contingencies,
                                                                          SensitivityFunctionType sensitivityFunctionType,
                                                                          SensitivityVariableType sensitivityVariableType,
                                                                          Reporter reporter) {
        List<SensitivityFactor> result = new ArrayList<>();

        List<IdentifiableAttributes> equipments = equipmentsListUuids.stream()
            .flatMap(equipmentsListUuid -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(equipmentsListUuid, context.getNetworkUuid(), context.getVariantId());
                if (list.stream().allMatch(i -> equipmentsTypesAllowed.contains(i.getType()))) {
                    return list.stream();
                } else {
                    reporter.report(Report.builder()
                        .withKey("badEquipmentType")
                        .withDefaultMessage("Equipments type in filter with id=${id} should be ${expectedType} : filter is ignored")
                        .withValue("id", equipmentsListUuid.toString())
                        .withValue("expectedType", equipmentsTypesAllowed.toString())
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .build());
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());

        List<IdentifiableAttributes> monitoredEquipments = monitoredEquipmentsListUuids.stream()
            .flatMap(monitoredEquimentsListUuid -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(monitoredEquimentsListUuid, context.getNetworkUuid(), context.getVariantId());
                // check that monitored equipments type is allowed
                if (list.stream().allMatch(i -> monitoredEquipmentsTypesAllowed.contains(i.getType()))) {
                    return list.stream();
                } else {
                    reporter.report(Report.builder()
                        .withKey("badMonitoredEquipmentType")
                        .withDefaultMessage("Monitored equipments type in filter with id=${id} should be ${expectedType} : filter is ignored")
                        .withValue("id", monitoredEquimentsListUuid.toString())
                        .withValue("expectedType", monitoredEquipmentsTypesAllowed.toString())
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

    private void buildSensitivityInjectionsSet(Reporter reporter) {
        SensitivityAnalysisInputData.SensitivityInjectionsSet sensitivityInjectionsSet = context.getSensitivityAnalysisInputData().getSensitivityInjectionsSet();

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
    }

    private void buildSensitivityInjection(Reporter reporter) {
        SensitivityAnalysisInputData.SensitivityInjection sensitivityInjection = context.getSensitivityAnalysisInputData().getSensitivityInjection();

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
    }

    private void buildSensitivityHVDC(Reporter reporter) {
        SensitivityAnalysisInputData.SensitivityHVDC sensitivityHVDC = context.getSensitivityAnalysisInputData().getSensitivityHVDC();

        List<Contingency> cHVDC = buildContingencies(sensitivityHVDC.getContingencies());
        List<SensitivityFactor> fHVDC = buildSensitivityFactorsFromEquipments(
            List.of(IdentifiableType.LINE, IdentifiableType.TWO_WINDINGS_TRANSFORMER),
            sensitivityHVDC.getMonitoredBranches(),
            List.of(IdentifiableType.HVDC_LINE),
            sensitivityHVDC.getHvdcs(),
            cHVDC,
            sensitivityHVDC.getSensitivityType() == SensitivityAnalysisInputData.SensitivityType.DELTA_MW
                ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                : SensitivityFunctionType.BRANCH_CURRENT_1,
            SensitivityVariableType.HVDC_LINE_ACTIVE_POWER,
            reporter);

        contingencies.addAll(cHVDC);
        factors.addAll(fHVDC);
    }

    private void buildSensitivityPST(Reporter reporter) {
        SensitivityAnalysisInputData.SensitivityPST sensitivityPST = context.getSensitivityAnalysisInputData().getSensitivityPST();

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
    }

    private void buildSensitivityNodes(Reporter reporter) {
        SensitivityAnalysisInputData.SensitivityNodes sensitivityNodes = context.getSensitivityAnalysisInputData().getSensitivityNodes();

        List<Contingency> cNodes = buildContingencies(sensitivityNodes.getContingencies());
        List<SensitivityFactor> fNodes = buildSensitivityFactorsFromEquipments(
            List.of(IdentifiableType.VOLTAGE_LEVEL),
            sensitivityNodes.getMonitoredVoltageLevels(),
            List.of(IdentifiableType.GENERATOR, IdentifiableType.TWO_WINDINGS_TRANSFORMER,
                IdentifiableType.HVDC_CONVERTER_STATION, IdentifiableType.STATIC_VAR_COMPENSATOR, IdentifiableType.SHUNT_COMPENSATOR),
            sensitivityNodes.getEquipmentsInVoltageRegulation(),
            cNodes,
            SensitivityFunctionType.BUS_VOLTAGE,
            SensitivityVariableType.BUS_TARGET_VOLTAGE,
            reporter);

        contingencies.addAll(cNodes);
        factors.addAll(fNodes);
    }

    public void build(Reporter reporter) {
        buildSensitivityInjectionsSet(reporter);
        buildSensitivityInjection(reporter);
        buildSensitivityHVDC(reporter);
        buildSensitivityPST(reporter);
        buildSensitivityNodes(reporter);
    }

    public List<Contingency> getContingencies() {
        return contingencies;
    }

    public List<SensitivityFactor> getFactors() {
        return factors;
    }

    public List<SensitivityVariableSet> getVariableSets() {
        return variablesSets;
    }
}

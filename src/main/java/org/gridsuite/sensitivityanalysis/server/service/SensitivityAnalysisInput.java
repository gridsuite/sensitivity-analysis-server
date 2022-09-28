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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class SensitivityAnalysisInput {
    private final Network network;
    private final SensitivityAnalysisRunContext context;
    private final ActionsService actionsService;
    private final FilterService filterService;

    private List<Contingency> contingencies = new ArrayList<>();
    private List<SensitivityFactor> factors = new ArrayList<>();
    private List<SensitivityVariableSet> variableSets = new ArrayList<>();
    private List<IdentifiableAttributes> identifiables = new ArrayList<>();

    public SensitivityAnalysisInput(Network network, SensitivityAnalysisRunContext context, ActionsService actionsService, FilterService filterService) {
        this.network = network;
        this.context = context;
        this.actionsService = actionsService;
        this.filterService = filterService;
    }

    private void buildContingencies() {
        contingencies = context.getContingencyListUuids().stream()
            .flatMap(contingencyListUuid -> actionsService.getContingencyList(contingencyListUuid, context.getNetworkUuid(), context.getVariantId()).stream())
            .collect(Collectors.toList());
    }

    private void buildSensitivityVariableSets() {
        List<List<IdentifiableAttributes>> variablesFiltersLists = context.getVariablesFiltersListUuids().stream()
            .map(variablesFilterUuid -> filterService.getIdentifiablesFromFilter(variablesFilterUuid, context.getNetworkUuid(), context.getVariantId()))
            .collect(Collectors.toList());

        variablesFiltersLists.forEach(variablesList -> {
            List<WeightedSensitivityVariable> variables = new ArrayList<>();
            if (!variablesList.isEmpty()) {
                boolean createVariablesSet = true;
                for (IdentifiableAttributes identifiableAttributes : variablesList) {
                    if (identifiableAttributes.getType() == IdentifiableType.GENERATOR) {
                        Generator generator = network.getGenerator(identifiableAttributes.getId());
                        if (generator != null) {
                            variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), generator.getMaxP()));
                        } else {
                            throw new PowsyblException("Generator '" + identifiableAttributes.getId() + "' not found !!");
                        }
                    } else if (identifiableAttributes.getType() == IdentifiableType.LOAD) {
                        Load load = network.getLoad(identifiableAttributes.getId());
                        if (load != null) {
                            variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), load.getP0()));
                        } else {
                            throw new PowsyblException("Load '" + identifiableAttributes.getId() + "' not found !!");
                        }
                    } else if (identifiableAttributes.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER ||
                               identifiableAttributes.getType() == IdentifiableType.THREE_WINDINGS_TRANSFORMER ||
                               identifiableAttributes.getType() == IdentifiableType.HVDC_LINE) {
                        // no variableSet generated for TD or HVDC : we keep the identifiables, to be used further
                        // when generating the sensitivity factors
                        createVariablesSet = false;
                        identifiables.add(identifiableAttributes);
                    } else {
                        createVariablesSet = false;
                    }
                }
                if (createVariablesSet) {
                    SensitivityVariableSet sensitivityVariableSet = new SensitivityVariableSet(UUID.randomUUID().toString(), variables);
                    variableSets.add(sensitivityVariableSet);
                }
            }
        });
    }

    private void buildSensitivityFactors(Reporter reporter) {
        List<IdentifiableAttributes> branchFilters = context.getBranchFiltersListUuids().stream()
            .flatMap(branchFilterUuid -> {
                List<IdentifiableAttributes> list = filterService.getIdentifiablesFromFilter(branchFilterUuid, context.getNetworkUuid(), context.getVariantId());
                // check that filter is effectively a branch filter
                if (list.stream().allMatch(i -> i.getType() == IdentifiableType.LINE ||
                    i.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER ||
                    i.getType() == IdentifiableType.THREE_WINDINGS_TRANSFORMER)) {
                    return list.stream();
                } else {
                    reporter.report(Report.builder()
                        .withKey("notBranchFilter")
                        .withDefaultMessage("Filter for monitored branches with id=${id} is not a branch filter : it is ignored")
                        .withValue("id", branchFilterUuid.toString())
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .build());
                    return Stream.empty();
                }
            })
            .collect(Collectors.toList());

        branchFilters.forEach(identifiableAttributes -> {
            // for each branch, generation of one sensitivity factor for each variable set generated before in buildSensitivityVariableSets method
            variableSets.forEach(variableSet ->
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                    identifiableAttributes.getId(),
                    SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableSet.getId(),
                    true,
                    contingencies.isEmpty() ? ContingencyContext.none() : ContingencyContext.all()))
            );

            // for each branch, one sensitivity factor for each identifiable (TD or HVDC) memorized before in buildSensitivityVariableSets method
            identifiables.forEach(identifiable -> factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                identifiableAttributes.getId(),
                identifiable.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER || identifiable.getType() == IdentifiableType.THREE_WINDINGS_TRANSFORMER
                    ? SensitivityVariableType.TRANSFORMER_PHASE : SensitivityVariableType.HVDC_LINE_ACTIVE_POWER,
                identifiable.getId(),
                false,
                contingencies.isEmpty() ? ContingencyContext.none() : ContingencyContext.all())));
        });
    }

    public void build(Reporter reporter) {
        buildContingencies();
        buildSensitivityVariableSets();
        buildSensitivityFactors(reporter);
    }

    public List<Contingency> getContingencies() {
        return contingencies;
    }

    public List<SensitivityFactor> getFactors() {
        return factors;
    }

    public List<SensitivityVariableSet> getVariableSets() {
        return variableSets;
    }
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.commons.PowsyblException;
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
                // compute the sum for generators or loads
                Double sum = 0D;
                for (IdentifiableAttributes identifiableAttributes : variablesList) {
                    if (identifiableAttributes.getType() == IdentifiableType.GENERATOR) {
                        Generator generator = network.getGenerator(identifiableAttributes.getId());
                        if (generator != null) {
                            sum += generator.getMaxP();
                        } else {
                            throw new PowsyblException("Generator '" + identifiableAttributes.getId() + "' not found !!");
                        }
                    } else if (identifiableAttributes.getType() == IdentifiableType.LOAD) {
                        Load load = network.getLoad(identifiableAttributes.getId());
                        if (load != null) {
                            sum += load.getP0();
                        } else {
                            throw new PowsyblException("Load '" + identifiableAttributes.getId() + "' not found !!");
                        }
                    }
                }

                boolean createVariablesSet = true;
                for (IdentifiableAttributes identifiableAttributes : variablesList) {
                    if (identifiableAttributes.getType() == IdentifiableType.GENERATOR) {
                        double weight = sum != 0D ? network.getGenerator(identifiableAttributes.getId()).getMaxP() / sum : 0D;
                        variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                    } else if (identifiableAttributes.getType() == IdentifiableType.LOAD) {
                        double weight = sum != 0D ? network.getLoad(identifiableAttributes.getId()).getP0() / sum : 0D;
                        variables.add(new WeightedSensitivityVariable(identifiableAttributes.getId(), weight));
                    } else {
                        // no variableSet generated for TD or HVDC : we keep the identifiables, to be used further
                        // when generating the sensitivity factors
                        createVariablesSet = false;
                        identifiables.add(identifiableAttributes);
                    }
                }
                if (createVariablesSet) {
                    SensitivityVariableSet sensitivityVariableSet = new SensitivityVariableSet(UUID.randomUUID().toString(), variables);
                    variableSets.add(sensitivityVariableSet);
                }
            }
        });
    }

    private void buildSensitivityFactors() {
        List<IdentifiableAttributes> quadFilters = context.getQuadFiltersListUuids().stream()
            .flatMap(quadsFilterUuid -> filterService.getIdentifiablesFromFilter(quadsFilterUuid, context.getNetworkUuid(), context.getVariantId()).stream())
            .collect(Collectors.toList());

        quadFilters.forEach(identifiableAttributes -> {
            // for each quad, generation of one sensitivity factor for each variable set generated before in buildSensitivityVariableSets method
            variableSets.forEach(variableSet ->
                factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                    identifiableAttributes.getId(),
                    SensitivityVariableType.INJECTION_ACTIVE_POWER,
                    variableSet.getId(),
                    true,
                    contingencies.isEmpty() ? ContingencyContext.none() : ContingencyContext.all()))
            );

            // for each quad, one sensitivity factor for each identifiable (TD or HVDC) memorized before in buildSensitivityVariableSets method
            identifiables.forEach(identifiable -> factors.add(new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                identifiableAttributes.getId(),
                identifiable.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER || identifiable.getType() == IdentifiableType.THREE_WINDINGS_TRANSFORMER
                    ? SensitivityVariableType.TRANSFORMER_PHASE : SensitivityVariableType.HVDC_LINE_ACTIVE_POWER,
                identifiable.getId(),
                false,
                contingencies.isEmpty() ? ContingencyContext.none() : ContingencyContext.all())));
        });
    }

    public void build() {
        buildContingencies();
        buildSensitivityVariableSets();
        buildSensitivityFactors();
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

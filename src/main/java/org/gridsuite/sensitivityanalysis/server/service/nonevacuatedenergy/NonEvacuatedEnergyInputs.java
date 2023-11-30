/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityVariableSet;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
public class NonEvacuatedEnergyInputs {
    private final List<Contingency> contingencies = new ArrayList<>();

    private final List<SensitivityFactor> factors = new ArrayList<>();

    private final List<SensitivityVariableSet> variablesSets = new ArrayList<>();

    // intermediate data used during the computation process
    private final Map<String, MonitoredBranchThreshold> branchesThresholds = new HashMap<>();  // monitored branches information
    private final Map<EnergySource, List<String>> cappingsGenerators = new EnumMap<>(EnergySource.class);  // list of capping generators by energy source
    private final Map<String, Double> generatorsPInit = new HashMap<>();  // initial targetP for the capping generators
    private final Map<EnergySource, Double> generatorsPInitByEnergySource = new EnumMap<>(EnergySource.class);  // initial overall targetP for the capping generators by energy source

    public void addContingencies(List<Contingency> contingencies) {
        this.contingencies.addAll(contingencies);
    }

    public void addSensitivityFactors(List<SensitivityFactor> factors) {
        this.factors.addAll(factors);
    }

    public void addSensitivityVariableSets(List<SensitivityVariableSet> variablesSets) {
        this.variablesSets.addAll(variablesSets);
    }
}

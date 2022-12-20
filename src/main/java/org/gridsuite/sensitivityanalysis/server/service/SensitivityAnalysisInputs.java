/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.contingency.Contingency;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityVariableSet;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.*;

@NoArgsConstructor
@Getter
public class SensitivityAnalysisInputs {
    private Set<Contingency> contingencies = new HashSet<>();

    private List<SensitivityFactor> factors = new ArrayList<>();

    private List<SensitivityVariableSet> variablesSets = new ArrayList<>();

    void addContingencies(List<Contingency> contingencies) {
        this.contingencies.addAll(contingencies);
    }

    void addSensitivityFactors(List<SensitivityFactor> factors) {
        this.factors.addAll(factors);
    }

    void addSensitivityVariableSets(List<SensitivityVariableSet> variablesSets) {
        this.variablesSets.addAll(variablesSets);
    }
}

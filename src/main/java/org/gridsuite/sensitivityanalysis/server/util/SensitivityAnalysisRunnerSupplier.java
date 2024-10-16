/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.sensitivity.SensitivityAnalysis.Runner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.powsybl.sensitivity.SensitivityAnalysis;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisRunnerSupplier {
    private final String defaultProvider;

    public SensitivityAnalysisRunnerSupplier(@Value("${sensitivity-analysis.default-provider}") final String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Runner getRunner(String provider) {
        String findProvider = provider != null ? provider : defaultProvider;
        return SensitivityAnalysis.find(findProvider);
    }
}

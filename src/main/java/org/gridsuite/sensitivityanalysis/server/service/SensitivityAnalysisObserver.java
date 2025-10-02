/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.gridsuite.computation.service.AbstractComputationObserver;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.springframework.stereotype.Service;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
@Service
public class SensitivityAnalysisObserver extends AbstractComputationObserver<Boolean, SensitivityAnalysisInputData> {
    private static final String COMPUTATION_TYPE = "sensitivityanalysis";

    public SensitivityAnalysisObserver(@NonNull ObservationRegistry observationRegistry,
                                       @NonNull MeterRegistry meterRegistry) {
        super(observationRegistry, meterRegistry);
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    @Override
    protected String getResultStatus(Boolean res) {
        return res ? "OK" : "NOK";
    }
}

/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.gridsuite.sensitivityanalysis.server.computation.service.AbstractComputationObserver;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results.NonEvacuatedEnergyResults;
import org.springframework.stereotype.Service;

/**
 * @author Mathieu DEHARBE <mathieu.deharbe at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyObserver extends AbstractComputationObserver<NonEvacuatedEnergyResults, NonEvacuatedEnergyInputData> {
    private static final String COMPUTATION_TYPE = "nonEvacuatedEnergy";

    public NonEvacuatedEnergyObserver(@NonNull ObservationRegistry observationRegistry,
                                      @NonNull MeterRegistry meterRegistry) {
        super(observationRegistry, meterRegistry);
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    @Override
    protected String getResultStatus(NonEvacuatedEnergyResults res) {
        return res == null ? "NOK" : "OK";
    }
}

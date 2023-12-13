/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
@Service
public class SensitivityAnalysisObserver {

    private final ObservationRegistry observationRegistry;
    private final String defaultProvider;
    private static final String OBSERVATION_PREFIX = "app.sensitivity.analysis.";
    private static final String PROVIDER_TAG_NAME = "provider";

    public SensitivityAnalysisObserver(@NonNull ObservationRegistry observationRegistry,
                                       @Value("${sensitivity-analysis.default-provider}") String defaultProvider) {
        this.observationRegistry = observationRegistry;
        this.defaultProvider = defaultProvider;
    }

    public <E extends Throwable> void observe(String name, SensitivityAnalysisRunContext runContext, Observation.CheckedRunnable<E> callable) throws E {
        createSensitivityAnalysisObservation(name, runContext).observeChecked(callable);
    }

    public <T, E extends Throwable> T observe(String name, SensitivityAnalysisRunContext runContext, Observation.CheckedCallable<T, E> callable) throws E {
        return createSensitivityAnalysisObservation(name, runContext).observeChecked(callable);
    }

    private Observation createSensitivityAnalysisObservation(String name, SensitivityAnalysisRunContext runContext) {
        String provider = runContext.getProvider() != null ? runContext.getProvider() : defaultProvider;
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(PROVIDER_TAG_NAME, provider);
    }
}

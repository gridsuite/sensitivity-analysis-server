/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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

    private static final String OBSERVATION_PREFIX = "app.computation.";
    private static final String PROVIDER_TAG_NAME = "provider";
    private static final String TYPE_TAG_NAME = "type";
    private static final String STATUS_TAG_NAME = "status";
    private static final String COMPUTATION_TYPE = "sensi";
    private static final String COMPUTATION_COUNTER_NAME = OBSERVATION_PREFIX + "count";

    private final String defaultProvider;
    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public SensitivityAnalysisObserver(@Value("${sensitivity-analysis.default-provider}") String defaultProvider,
                                       @NonNull ObservationRegistry observationRegistry,
                                       @NonNull MeterRegistry meterRegistry) {
        this.defaultProvider = defaultProvider;
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public <E extends Throwable> void observe(String name, SensitivityAnalysisRunContext runContext, Observation.CheckedRunnable<E> callable) throws E {
        createObservation(name, runContext).observeChecked(callable);
    }

    public <T, E extends Throwable> T observe(String name, SensitivityAnalysisRunContext runContext, Observation.CheckedCallable<T, E> callable) throws E {
        return createObservation(name, runContext).observeChecked(callable);
    }

    public <T extends SensitivityAnalysisResult, E extends Throwable> T observeRun(String name, SensitivityAnalysisRunContext runContext, Observation.CheckedCallable<T, E> callable) throws E {
        T result = createObservation(name, runContext).observeChecked(callable);
        incrementCount(runContext, result);
        return result;
    }

    private Observation createObservation(String name, SensitivityAnalysisRunContext runContext) {
        String provider = runContext.getProvider() != null ? runContext.getProvider() : defaultProvider;
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
            .lowCardinalityKeyValue(PROVIDER_TAG_NAME, provider)
            .lowCardinalityKeyValue(TYPE_TAG_NAME, COMPUTATION_TYPE);
    }

    private void incrementCount(SensitivityAnalysisRunContext runContext, SensitivityAnalysisResult result) {
        Counter.builder(COMPUTATION_COUNTER_NAME)
            .tag(PROVIDER_TAG_NAME, runContext.getProvider())
            .tag(TYPE_TAG_NAME, COMPUTATION_TYPE)
            .tag(STATUS_TAG_NAME, getStatusFromResult(result))
            .register(meterRegistry)
            .increment();
    }

    private static String getStatusFromResult(SensitivityAnalysisResult result) {
        if (result == null) {
            return "NOK";
        }
        return result.getContingencyStatuses().stream()
            .map(SensitivityAnalysisResult.SensitivityContingencyStatus::getStatus)
            .allMatch(status1 -> status1 == SensitivityAnalysisResult.Status.SUCCESS) ? "OK" : "NOK";
    }
}

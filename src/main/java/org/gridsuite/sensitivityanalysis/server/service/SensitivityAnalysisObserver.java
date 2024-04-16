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
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.gridsuite.sensitivityanalysis.server.computation.service.AbstractComputationObserver;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.springframework.stereotype.Service;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
@Service
public class SensitivityAnalysisObserver extends AbstractComputationObserver<SensitivityAnalysisResult, SensitivityAnalysisInputData> {
    private static final String COMPUTATION_TYPE = "sensi";

    public SensitivityAnalysisObserver(@NonNull ObservationRegistry observationRegistry,
                                       @NonNull MeterRegistry meterRegistry) {
        super(observationRegistry, meterRegistry);
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    @Override
    protected String getResultStatus(SensitivityAnalysisResult res) {
        return res == null ? "NOK" : "OK";
    }

    /** TODO DU JORIS :
     * public SensitivityAnalysisObserver(@Value("${sensitivity-analysis.default-provider}") String defaultProvider,
     *                                        @NonNull ObservationRegistry observationRegistry,
     *                                        @NonNull MeterRegistry meterRegistry) {
     *         this.defaultProvider = defaultProvider;
     *         this.observationRegistry = observationRegistry;
     *         this.meterRegistry = meterRegistry;
     *     }
     *
     *     public <E extends Throwable> void observe(String name, SensitivityAnalysisRunContext runContext, Observation.CheckedRunnable<E> runnable) throws E {
     *         createObservation(name, runContext).observeChecked(runnable);
     *     }
     *
     *     public <T, E extends Throwable> T observeRun(String name, SensitivityAnalysisRunContext runContext, Observation.CheckedCallable<T, E> callable) throws E {
     *         T result = createObservation(name, runContext).observeChecked(callable);
     *         incrementCount(runContext, result);
     *         return result;
     *     }
     *
     *     private Observation createObservation(String name, SensitivityAnalysisRunContext runContext) {
     *         String provider = runContext.getProvider() != null ? runContext.getProvider() : defaultProvider;
     *         return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
     *             .lowCardinalityKeyValue(PROVIDER_TAG_NAME, provider)
     *             .lowCardinalityKeyValue(TYPE_TAG_NAME, COMPUTATION_TYPE);
     *     }
     *
     *     private <T> void incrementCount(SensitivityAnalysisRunContext runContext, T result) {
     *         String provider = runContext.getProvider() != null ? runContext.getProvider() : defaultProvider;
     *         Counter.builder(COMPUTATION_COUNTER_NAME)
     *             .tag(PROVIDER_TAG_NAME, provider)
     *             .tag(TYPE_TAG_NAME, COMPUTATION_TYPE)
     *             .tag(STATUS_TAG_NAME, result == null ? "NOK" : "OK")
     *             .register(meterRegistry)
     *             .increment();
     *     }
     */
}

package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.gridsuite.computation.service.AbstractComputationObserver;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.springframework.stereotype.Service;

@Service
public class SensitivityAnalysisInMemoryObserver extends AbstractComputationObserver<SensitivityAnalysisResult, SensitivityAnalysisInputData> {
    private static final String COMPUTATION_TYPE = "sensiInMemory";

    public SensitivityAnalysisInMemoryObserver(@NonNull ObservationRegistry observationRegistry,
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
}

/*
  Copyright (c) 2026, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.RawSensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultId;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class SensitivityResultIdTest {
    @Test
    void testSensitivityResultGetId() {
        UUID analysisResultId = UUID.randomUUID();
        int factorIndex = 42;
        var analysisResult = new AnalysisResultEntity(analysisResultId, Instant.now());
        Persistable<SensitivityResultId> sensitivityResultEntity = new SensitivityResultEntity(
                factorIndex,
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                "function",
                SensitivityVariableType.INJECTION_ACTIVE_POWER,
                "variable",
                false,
                analysisResult,
                null,
                null
        );

        assertThat(sensitivityResultEntity.getId()).isEqualTo(new SensitivityResultId(analysisResultId, factorIndex));
    }

    @Test
    void testRawSensitivityResultGetId() {
        UUID analysisResultId = UUID.randomUUID();
        int factorIndex = 42;
        var analysisResult = new AnalysisResultEntity(analysisResultId, Instant.now());
        Persistable<SensitivityResultId> rawSensitivityResultEntity =
                new RawSensitivityResultEntity(factorIndex, 1.5, 2.5, analysisResult);

        assertThat(rawSensitivityResultEntity.getId()).isEqualTo(new SensitivityResultId(analysisResultId, factorIndex));
    }
}

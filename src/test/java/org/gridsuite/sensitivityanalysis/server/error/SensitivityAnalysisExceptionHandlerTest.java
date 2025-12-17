/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.error;

import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.sensitivityanalysis.server.error.SensitivityAnalysisBusinessErrorCode.TOO_MANY_FACTORS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
class SensitivityAnalysisExceptionHandlerTest {

    private SensitivityAnalysisExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SensitivityAnalysisExceptionHandler(() -> "sensitivityAnalysis");
    }

    @Test
    void mapsInteralErrorBusinessErrorToStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/results-endpoint/uuid");
        SensitivityAnalysisException exception = new SensitivityAnalysisException(TOO_MANY_FACTORS, "Too many factors to run sensitivity analysis");
        ResponseEntity<PowsyblWsProblemDetail> response = handler.handleSensitivityAnalysisException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertEquals("sensitivityAnalysis.tooManyFactors", response.getBody().getBusinessErrorCode());
    }
}

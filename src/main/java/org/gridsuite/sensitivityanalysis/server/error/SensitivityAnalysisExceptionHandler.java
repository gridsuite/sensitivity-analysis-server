/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessExceptionHandler;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import com.powsybl.ws.commons.error.ServerNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@ControllerAdvice
public class SensitivityAnalysisExceptionHandler
    extends AbstractBusinessExceptionHandler<SensitivityAnalysisException, SensitivityAnalysisBusinessErrorCode> {

    public SensitivityAnalysisExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @NonNull
    @Override
    protected SensitivityAnalysisBusinessErrorCode getBusinessCode(SensitivityAnalysisException ex) {
        return ex.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(SensitivityAnalysisBusinessErrorCode errorCode) {
        return switch (errorCode) {
            case TOO_MANY_FACTORS -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    @ExceptionHandler(SensitivityAnalysisException.class)
    protected ResponseEntity<PowsyblWsProblemDetail> handleSensitivityAnalysisException(
            SensitivityAnalysisException exception, HttpServletRequest request) {
        return super.handleDomainException(exception, request);
    }
}

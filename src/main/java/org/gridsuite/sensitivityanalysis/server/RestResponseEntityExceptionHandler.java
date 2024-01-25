/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

@ControllerAdvice
public class RestResponseEntityExceptionHandler {
    @ExceptionHandler(SensibilityAnalysisException.class)
    protected ResponseEntity<Object> handleSensibilityAnalysisException(SensibilityAnalysisException exception) {
        return switch (exception.getType()) {
            case RESULT_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
            case FILE_EXPORT_ERROR, INVALID_EXPORT_PARAMS -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        };
    }
}

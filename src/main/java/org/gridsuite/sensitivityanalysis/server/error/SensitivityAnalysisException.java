/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Map;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@Getter
public class SensitivityAnalysisException extends AbstractBusinessException {

    private final SensitivityAnalysisBusinessErrorCode errorCode;
    private final transient Map<String, Object> businessErrorValues;

    public SensitivityAnalysisException(SensitivityAnalysisBusinessErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public SensitivityAnalysisException(SensitivityAnalysisBusinessErrorCode errorCode, String message, Map<String, Object> businessErrorValues) {
        super(message);
        this.errorCode = errorCode;
        this.businessErrorValues = businessErrorValues != null ? Map.copyOf(businessErrorValues) : Map.of();
    }

    @NotNull
    @Override
    public SensitivityAnalysisBusinessErrorCode getBusinessErrorCode() {
        return errorCode;
    }
}

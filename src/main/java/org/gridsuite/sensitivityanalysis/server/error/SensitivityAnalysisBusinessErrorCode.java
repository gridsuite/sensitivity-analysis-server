/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * @author Antoine Bouhours {@literal <antoine.bouhours at rte-france.com>}
 */
public enum SensitivityAnalysisBusinessErrorCode implements BusinessErrorCode {
    TOO_MANY_FACTORS("sensitivityAnalysis.tooManyFactors"),;

    private final String code;

    SensitivityAnalysisBusinessErrorCode(String code) {
        this.code = code;
    }

    public String value() {
        return code;
    }
}

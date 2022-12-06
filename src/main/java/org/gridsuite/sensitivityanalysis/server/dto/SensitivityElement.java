/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import javax.annotation.Nullable;

import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@Getter
@AllArgsConstructor
public class SensitivityElement {
    SensitivityFunctionType functionType;
    SensitivityVariableType variableType;

    private String functionId;
    private String variableId;

    @Nullable
    private String contingencyId;

    private double valueN;
    private double functionReferenceN;

    private double valueNK;
    private double functionReferenceNK;
}

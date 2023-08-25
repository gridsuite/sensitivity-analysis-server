/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class SensitivityWithContingency extends SensitivityOfTo {

    public static SensitivityWithContingencyBuilder<?, ?> toBuilder(SensitivityOfTo base, String functionId, String variableId) {
        if (base == null) {
            return builder().funcId(functionId).varId(variableId);
        }

        return builder().funcId(base.getFuncId()).varId(base.getVarId()).value(base.getValue()).functionReference(
            base.getFunctionReference()).varIsAFilter(base.isVarIsAFilter());
    }

    @NonNull
    private String contingencyId;

    private double valueAfter;
    private double functionReferenceAfter;

    // ObjectMapper.readValue to deserialize a list of, for tests
    @SuppressWarnings("unused") protected SensitivityWithContingency() {
        contingencyId = "sonar thinks it should be initialized though not used";
    }
}

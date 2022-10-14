package org.gridsuite.sensitivityanalysis.server.dto;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class SensitivityWithContingency extends SensitivityOfTo {

    public static SensitivityWithContingencyBuilder<?, ?> toBuilder(SensitivityOfTo base) {
        if (base == null) {
            return builder();
        }

        return builder().funcId(base.getFuncId()).varId(base.getVarId()).value(base.getValue()).functionReference(
            base.getFunctionReference()).varIsAFilter(base.isVarIsAFilter());
    }

    private String contingencyId;

    private double valueAfter;
    private double functionReferenceAfter;
}

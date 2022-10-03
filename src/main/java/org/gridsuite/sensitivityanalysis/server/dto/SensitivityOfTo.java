package org.gridsuite.sensitivityanalysis.server.dto;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class SensitivityOfTo {
    private String  funcId;
    private String  varId;
    private boolean varIsAFilter;

    private double value;
    private double functionReference;
}

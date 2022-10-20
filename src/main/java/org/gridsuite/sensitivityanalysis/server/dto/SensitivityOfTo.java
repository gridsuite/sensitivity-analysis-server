package org.gridsuite.sensitivityanalysis.server.dto;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
public class SensitivityOfTo {
    @NonNull
    private String  funcId;
    @NonNull
    private String  varId;
    private boolean varIsAFilter;

    private double value;
    private double functionReference;

    // ObjectMapper.readValue to deserialize a list of, for tests
    @SuppressWarnings("unused") protected SensitivityOfTo() {
    }
}

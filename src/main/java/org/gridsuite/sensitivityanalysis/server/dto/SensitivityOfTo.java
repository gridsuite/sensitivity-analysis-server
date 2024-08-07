/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SensitivityWithContingency.class, name = "WITH_CONTINGENCY"),
})
public class SensitivityOfTo {
    @NonNull
    private String funcId;
    @NonNull
    private String varId;
    private boolean varIsAFilter;

    private double value;
    private double functionReference;

    // ObjectMapper.readValue to deserialize a list of, for tests
    @SuppressWarnings("unused") protected SensitivityOfTo() {
        funcId = "sonar thinks";
        varId = "these are bugs";
    }
}

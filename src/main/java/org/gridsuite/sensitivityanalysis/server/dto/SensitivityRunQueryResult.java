/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import java.util.List;

import com.powsybl.sensitivity.SensitivityFunctionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class SensitivityRunQueryResult {
    @NonNull
    Boolean                 isJustBefore;
    @NonNull
    SensitivityFunctionType functionType;

    @NonNull
    Integer requestedChunkSize;
    @NonNull
    Integer chunkOffset;

    @NonNull
    Integer totalSensitivitiesCount;
    @NonNull
    Integer filteredSensitivitiesCount;

    @NonNull
    List<? extends SensitivityOfTo> sensitivities;

    List<String> allContingenciesUuid;
    List<String> allFunctionIds;
    List<String> allVariablesUuids;
}

/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.dto.parameters;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
@Schema(description = "Sensitivity analysis parameters")
public class SensitivityAnalysisParametersInfos {

    @Schema(description = "Provider")
    private String provider;

    @Schema(description = "Parameters ID")
    private UUID uuid;

    @Schema(description = "Parameters date")
    private OffsetDateTime date;

    @Schema(description = "Parameters name")
    private String name;

    @Builder.Default
    private double flowFlowSensitivityValueThreshold = 0.0;

    @Builder.Default
    private double angleFlowSensitivityValueThreshold = 0.0;

    @Builder.Default
    private double flowVoltageSensitivityValueThreshold = 0.0;

    @Builder.Default
    List<SensitivityInjectionsSet> sensitivityInjectionsSet = List.of();

    @Builder.Default
    List<SensitivityInjection> sensitivityInjection = List.of();

    @Builder.Default
    List<SensitivityHVDC> sensitivityHVDC = List.of();

    @Builder.Default
    List<SensitivityPST> sensitivityPST = List.of();

    @Builder.Default
    List<SensitivityNodes> sensitivityNodes = List.of();

    public SensitivityAnalysisParametersEntity toEntity() {
        return new SensitivityAnalysisParametersEntity(this);
    }
}

/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.sensitivityanalysis.server.dto.EquipmentsContainer;

import java.util.List;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "Sensitivity analysis non evacuated energy monitored branches")
public class NonEvacuatedEnergyMonitoredBranches {
    List<EquipmentsContainer> branches;

    boolean activated;

    boolean istN;

    String limitNameN;

    @JsonProperty("nCoefficient")
    float nCoefficient;

    boolean istNm1;

    String limitNameNm1;

    float nm1Coefficient;
}

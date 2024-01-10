/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.powsybl.iidm.network.EnergySource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "Sensitivity analysis non evacuated energy stage detail result")
public class StageDetailResult {
    @Builder.Default
    @JsonProperty("pInitByEnergySource")
    private Map<EnergySource, Double> pInitByEnergySource = new EnumMap<>(EnergySource.class);

    @Builder.Default
    private Map<String, ContingencyStageDetailResult> resultsByContingency = new HashMap<>();  // key is null for N
}

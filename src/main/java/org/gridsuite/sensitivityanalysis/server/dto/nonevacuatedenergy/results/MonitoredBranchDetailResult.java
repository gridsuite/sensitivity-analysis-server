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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Sensitivity analysis non evacuated energy monitored branch detail result")
public class MonitoredBranchDetailResult {
    private Double intensity;

    private String limitName;

    private Double limitValue;

    private Double percentOverload;

    @JsonProperty("p")
    private Double p;

    @Builder.Default
    Map<EnergySource, Double> cappingByEnergySource = new EnumMap<>(EnergySource.class);

    private Double overallCapping;

    @Builder.Default
    Map<EnergySource, Double> sensitivityByEnergySource = new EnumMap<>(EnergySource.class);

    @Builder.Default
    Map<String, GeneratorCapping> generatorsCapping = new HashMap<>();
}

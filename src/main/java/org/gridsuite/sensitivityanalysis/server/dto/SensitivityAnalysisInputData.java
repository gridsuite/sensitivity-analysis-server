/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Sensitivity analysis input data")
public class SensitivityAnalysisInputData {
    public enum DistributionType {
        PROPORTIONAL,
        PROPORTIONAL_MAXP,
        REGULAR,
        VENTILATION
    }

    public enum SensitivityType {
        DELTA_MW,
        DELTA_A
    }

    @Schema(description = "Results threshold")
    private double resultsThreshold;

    @Schema(description = "Sensitivity relatively to injections set")
    private List<SensitivityInjectionsSet> sensitivityInjectionsSets;

    @Schema(description = "Sensitivity relatively to each injection")
    private List<SensitivityInjection> sensitivityInjections;

    @Schema(description = "Sensitivity relatively to each HVDC")
    private List<SensitivityHVDC> sensitivityHVDCs;

    @Schema(description = "Sensitivity relatively to each PST")
    private List<SensitivityPST> sensitivityPSTs;

    @Schema(description = "Sensitivity relatively to nodes")
    private List<SensitivityNodes> sensitivityNodes;

    @Schema(description = "Sensitivity parameters")
    private SensitivityAnalysisParameters parameters;

    @Schema(description = "Loadflow model-specific parameters")
    private Map<String, String> loadFlowSpecificParameters;
}

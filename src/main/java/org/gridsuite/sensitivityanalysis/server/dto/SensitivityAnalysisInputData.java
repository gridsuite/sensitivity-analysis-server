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
import java.util.UUID;

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

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to injections set")
    public static class SensitivityInjectionsSet {
        List<UUID> monitoredBranches;
        List<UUID> injections;
        DistributionType distributionType;
        List<UUID> contingencies;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to each injection")
    public static class SensitivityInjection {
        List<UUID> monitoredBranches;
        List<UUID> injections;
        List<UUID> contingencies;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to each HVDC")
    public static class SensitivityHVDC {
        List<UUID> monitoredBranches;
        SensitivityType sensitivityType;
        List<UUID> hvdcs;
        List<UUID> contingencies;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to each PST")
    public static class SensitivityPST {
        List<UUID> monitoredBranches;
        SensitivityType sensitivityType;
        List<UUID> psts;
        List<UUID> contingencies;
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "Sensitivity relatively to nodes")
    public static class SensitivityNodes {
        List<UUID> monitoredVoltageLevels;
        List<UUID> equipmentsInVoltageRegulation;
        List<UUID> contingencies;
    }

    @Schema(description = "Results threshold")
    private double resultsThreshold;

    @Schema(description = "Sensitivity relatively to injections set")
    private SensitivityInjectionsSet sensitivityInjectionsSet;

    @Schema(description = "Sensitivity relatively to each injection")
    private SensitivityInjection sensitivityInjection;

    @Schema(description = "Sensitivity relatively to each HVDC")
    private SensitivityHVDC sensitivityHVDC;

    @Schema(description = "Sensitivity relatively to each PST")
    private SensitivityPST sensitivityPST;

    @Schema(description = "Sensitivity relatively to nodes")
    private SensitivityNodes sensitivityNodes;

    @Schema(description = "Sensitivity parameters")
    private SensitivityAnalysisParameters parameters;
}

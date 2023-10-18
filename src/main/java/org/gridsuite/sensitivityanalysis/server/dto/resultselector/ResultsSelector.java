/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.dto.resultselector;

import java.util.Collection;
import java.util.Map;

import com.powsybl.sensitivity.SensitivityFunctionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Schema(description = "Results selector")
public class ResultsSelector {
    @Schema(description = "function type (/MW /A /kV)")
    @NonNull
    private SensitivityFunctionType functionType;

    @Schema(description = "Tab selected")
    private ResultTab tabSelection;

    @Schema(description = "sorting by [{key, rank * (ascending ? 1 : -1) }], with rank > 0 and exclusive")
    private Map<SortKey, Integer> sortKeysWithWeightAndDirection;

    @Schema(description = "ids of the functions (branches) to limit to")
    private Collection<String> functionIds;

    @Schema(description = "ids of the variables to limit to")
    private Collection<String> variableIds;

    @Schema(description = "ids of the contingencies to limit to")
    private Collection<String> contingencyIds;

    @Schema(description = "page number")
    private Integer pageNumber;

    @Schema(description = "row number")
    private Integer pageSize;

    @Schema(description = "in case pageSize is > 0, the offset in total sensitivities")
    private Integer offset;
}

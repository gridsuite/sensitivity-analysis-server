package org.gridsuite.sensitivityanalysis.server;

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
    SensitivityFunctionType functionType;
    @Schema(description = "true for N, false for N-k", name = "isJustBefore")
    // object Boolean, not type boolean or else json round trip is broken
    // as a "justBefore" property would be added when a "isJustBefore" would be looked after
    @NonNull
    Boolean                 isJustBefore;

    public enum SortKey { FUNCTION, VARIABLE, CONTINGENCY, REFERENCE, SENSITIVITY, POST_REFERENCE, POST_SENSITIVITY }

    @Schema(description = "sorting by [{key, rank * (ascending ? 1 : -1) }], with rank > 0 and exclusive")
    Map<SortKey, Integer> sortKeysWithWeightAndDirection;

    @Schema(description = "ids of the functions (branches) to limit to")
    Collection<String> functionIds;
    @Schema(description = "ids of the variables to limit to")
    Collection<String> variableIds;
    @Schema(description = "ids of the contingencies to limit to")
    Collection<String> contingencyIds;

    @Schema(description = "maximum number of sensitivities to return, if > 0")
    Integer chunkSize;
    @Schema(description = "in case chunkSize is > 0, the offset in total sensitivities")
    Integer offset;
}

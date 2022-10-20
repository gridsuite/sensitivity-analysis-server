package org.gridsuite.sensitivityanalysis.server.dto;

import java.util.List;

import com.powsybl.sensitivity.SensitivityFunctionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

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

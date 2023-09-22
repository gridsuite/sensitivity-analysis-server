package org.gridsuite.sensitivityanalysis.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class SensitivityResultOptions {
    List<String> allContingencyIds;
    List<String> allFunctionIds;
    List<String> allVariableIds;
}

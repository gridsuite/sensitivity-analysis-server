package org.gridsuite.sensitivityanalysis.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class SensitivityAnalysisCsvFileInfos {
    private String selector;
    private List<String> csvHeaders;
}

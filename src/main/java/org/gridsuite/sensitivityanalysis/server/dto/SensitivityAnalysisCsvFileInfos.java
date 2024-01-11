package org.gridsuite.sensitivityanalysis.server.dto;

import com.powsybl.sensitivity.SensitivityFunctionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;

import java.util.List;

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class SensitivityAnalysisCsvFileInfos {
    private SensitivityFunctionType sensitivityFunctionType;
    private ResultTab tabSelection;
    private List<String> csvHeaders;
}

/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.dto;

import com.powsybl.sensitivity.SensitivityFunctionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;

import java.util.List;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
public class SensitivityAnalysisCsvFileInfos {
    private SensitivityFunctionType sensitivityFunctionType;
    private ResultTab resultTab;
    private List<String> csvHeaders;
    private String language;
}

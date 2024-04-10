/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import lombok.Getter;
import org.gridsuite.sensitivityanalysis.server.computation.service.AbstractComputationRunContext;
import org.gridsuite.sensitivityanalysis.server.computation.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
public class SensitivityAnalysisRunContext extends AbstractComputationRunContext<SensitivityAnalysisInputData> {

    private final SensitivityAnalysisInputs sensitivityAnalysisInputs;

    public SensitivityAnalysisRunContext(UUID networkUuid,
                                         String variantId,
                                         String receiver,
                                         ReportInfos reportInfos,
                                         String userId,
                                         @Value("${sensitivity-analysis.default-provider}") String provider,
                                         SensitivityAnalysisInputData sensitivityAnalysisInputData) {
        super(networkUuid,
                variantId,
                receiver,
                reportInfos == null ?
                        new ReportInfos(null, null, null) :
                        reportInfos,
                userId,
                provider,
                sensitivityAnalysisInputData,
                null);
        this.sensitivityAnalysisInputs = new SensitivityAnalysisInputs();
    }

    SensitivityAnalysisInputData getSensitivityAnalysisInputData() {
        return parameters;
    }
}

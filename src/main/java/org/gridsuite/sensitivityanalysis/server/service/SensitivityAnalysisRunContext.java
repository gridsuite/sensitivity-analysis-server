/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import lombok.Getter;
import org.gridsuite.sensitivityanalysis.server.computation.service.AbstractComputationRunContext;
import org.gridsuite.sensitivityanalysis.server.computation.service.ReportContext;
import org.gridsuite.sensitivityanalysis.server.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
public class SensitivityAnalysisRunContext extends AbstractComputationRunContext<SensitivityAnalysisInputData> {

    private final SensitivityAnalysisInputs sensitivityAnalysisInputs;

    public SensitivityAnalysisRunContext(UUID networkUuid, String variantId,
                                         SensitivityAnalysisInputData sensitivityAnalysisInputData,
                                         String receiver, String provider,
                                         ReportInfos reportInfos, String userId) {
        super(networkUuid,
                variantId,
                receiver,
                new ReportContext(reportInfos == null ? null : reportInfos.reportUuid(),
                        reportInfos == null ? null : reportInfos.reporterId(),
                        reportInfos == null ? null : reportInfos.reportType()),
                userId,
                provider,
                sensitivityAnalysisInputData);
        this.sensitivityAnalysisInputs = new SensitivityAnalysisInputs();
    }

    SensitivityAnalysisInputData getSensitivityAnalysisInputData() {
        return parameters;
    }
}

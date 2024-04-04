/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import lombok.Getter;
import org.gridsuite.sensitivityanalysis.server.computation.service.ReportContext;
import org.gridsuite.sensitivityanalysis.server.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
public class SensitivityAnalysisRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final SensitivityAnalysisInputData sensitivityAnalysisInputData;

    private final SensitivityAnalysisInputs sensitivityAnalysisInputs;

    private final String receiver;

    private final String provider;

    private final ReportContext reportContext;

    private final String userId;

    public SensitivityAnalysisRunContext(UUID networkUuid, String variantId,
                                         SensitivityAnalysisInputData sensitivityAnalysisInputData,
                                         String receiver, String provider,
                                         ReportInfos reportInfos, String userId) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.sensitivityAnalysisInputData = Objects.requireNonNull(sensitivityAnalysisInputData);
        this.sensitivityAnalysisInputs = new SensitivityAnalysisInputs();
        this.receiver = receiver;
        this.provider = provider;
        this.reportContext = new ReportContext(reportInfos == null ? null : reportInfos.reportUuid(),
                reportInfos == null ? null : reportInfos.reporterId(),
                reportInfos == null ? null : reportInfos.reportType());
        this.userId = userId;
    }
}

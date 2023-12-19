/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import lombok.Getter;
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

    private final UUID reportUuid;

    private final String reporterId;

    private final String userId;

    private final String reportType;

    public SensitivityAnalysisRunContext(UUID networkUuid, String variantId,
                                         SensitivityAnalysisInputData sensitivityAnalysisInputData,
                                         String receiver, String provider, UUID reportUuid,
                                         String reporterId, String reportType, String userId) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.sensitivityAnalysisInputData = Objects.requireNonNull(sensitivityAnalysisInputData);
        this.sensitivityAnalysisInputs = new SensitivityAnalysisInputs();
        this.receiver = receiver;
        this.provider = provider;
        this.reportUuid = reportUuid;
        this.reporterId = reporterId;
        this.reportType = reportType;
        this.userId = userId;
    }
}

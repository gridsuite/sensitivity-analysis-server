/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class SensitivityAnalysisRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final List<UUID> otherNetworkUuids;

    private final SensitivityAnalysisInputData sensitivityAnalysisInputData;

    private final SensitivityAnalysisInputs sensitivityAnalysisInputs;

    private final String receiver;

    private final String provider;

    private final UUID reportUuid;

    private final String reporterId;

    public SensitivityAnalysisRunContext(UUID networkUuid, String variantId, List<UUID> otherNetworkUuids,
                                         SensitivityAnalysisInputData sensitivityAnalysisInputData,
                                         String receiver, String provider, UUID reportUuid, String reporterId) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.otherNetworkUuids = Objects.requireNonNull(otherNetworkUuids);
        this.sensitivityAnalysisInputData = Objects.requireNonNull(sensitivityAnalysisInputData);
        this.sensitivityAnalysisInputs = new SensitivityAnalysisInputs();
        this.receiver = receiver;
        this.provider = provider;
        this.reportUuid = reportUuid;
        this.reporterId = reporterId;
    }

    public UUID getNetworkUuid() {
        return networkUuid;
    }

    public String getVariantId() {
        return variantId;
    }

    public List<UUID> getOtherNetworkUuids() {
        return otherNetworkUuids;
    }

    public SensitivityAnalysisInputData getSensitivityAnalysisInputData() {
        return sensitivityAnalysisInputData;
    }

    public SensitivityAnalysisInputs getSensitivityAnalysisInputs() {
        return sensitivityAnalysisInputs;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getProvider() {
        return provider;
    }

    public UUID getReportUuid() {
        return reportUuid;
    }

    public String getReporterId() {
        return reporterId;
    }
}

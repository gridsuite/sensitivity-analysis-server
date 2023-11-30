/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class NonEvacuatedEnergyRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData;

    private final NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs;

    private final String receiver;

    private final UUID reportUuid;

    private final String reporterId;

    public NonEvacuatedEnergyRunContext(UUID networkUuid, String variantId, NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData, String receiver, UUID reportUuid, String reporterId) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.nonEvacuatedEnergyInputData = Objects.requireNonNull(nonEvacuatedEnergyInputData);
        this.nonEvacuatedEnergyInputs = new NonEvacuatedEnergyInputs();
        this.receiver = receiver;
        this.reportUuid = reportUuid;
        this.reporterId = reporterId;
    }

    public UUID getNetworkUuid() {
        return networkUuid;
    }

    public String getVariantId() {
        return variantId;
    }

    public NonEvacuatedEnergyInputData getInputData() {
        return nonEvacuatedEnergyInputData;
    }

    public NonEvacuatedEnergyInputs getInputs() {
        return nonEvacuatedEnergyInputs;
    }

    public String getReceiver() {
        return receiver;
    }

    public UUID getReportUuid() {
        return reportUuid;
    }

    public String getReporterId() {
        return reporterId;
    }
}

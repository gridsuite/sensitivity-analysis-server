/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.InputData;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class RunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final InputData inputData;

    private final Inputs inputs;

    private final String receiver;

    private final String provider;

    private final UUID reportUuid;

    private final String reporterId;

    public RunContext(UUID networkUuid, String variantId, InputData inputData, String receiver, String provider, UUID reportUuid, String reporterId) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.inputData = Objects.requireNonNull(inputData);
        this.inputs = new Inputs();
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

    public InputData getInputData() {
        return inputData;
    }

    public Inputs getInputs() {
        return inputs;
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

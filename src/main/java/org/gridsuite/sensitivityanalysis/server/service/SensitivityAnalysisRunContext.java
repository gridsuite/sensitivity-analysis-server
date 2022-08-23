/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;

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

    private final List<UUID> variablesFiltersListUuids;

    private final List<UUID> contingencyListUuids;

    private final List<UUID> quadFiltersListUuids;

    private final String receiver;

    private final String provider;

    private final SensitivityAnalysisParameters parameters;

    private final UUID reportUuid;

    public SensitivityAnalysisRunContext(UUID networkUuid, String variantId, List<UUID> otherNetworkUuids,
                                         List<UUID> variablesFiltersListUuids, List<UUID> contingencyListUuids,
                                         List<UUID> quadFiltersListUuids,
                                         String receiver, String provider, SensitivityAnalysisParameters parameters, UUID reportUuid) {
        this.networkUuid = Objects.requireNonNull(networkUuid);
        this.variantId = variantId;
        this.otherNetworkUuids = Objects.requireNonNull(otherNetworkUuids);
        this.variablesFiltersListUuids = Objects.requireNonNull(variablesFiltersListUuids);
        this.contingencyListUuids = Objects.requireNonNull(contingencyListUuids);
        this.quadFiltersListUuids = Objects.requireNonNull(quadFiltersListUuids);
        this.receiver = receiver;
        this.provider = provider;
        this.parameters = Objects.requireNonNull(parameters);
        this.reportUuid = reportUuid;
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

    public List<UUID> getVariablesFiltersListUuids() {
        return variablesFiltersListUuids;
    }

    public List<UUID> getContingencyListUuids() {
        return contingencyListUuids;
    }

    public List<UUID> getQuadFiltersListUuids() {
        return quadFiltersListUuids;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getProvider() {
        return provider;
    }

    public SensitivityAnalysisParameters getParameters() {
        return parameters;
    }

    public UUID getReportUuid() {
        return reportUuid;
    }
}

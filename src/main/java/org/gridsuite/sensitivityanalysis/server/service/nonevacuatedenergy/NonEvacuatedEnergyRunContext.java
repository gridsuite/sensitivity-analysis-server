/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import lombok.Getter;
import org.gridsuite.sensitivityanalysis.server.computation.service.AbstractComputationRunContext;
import org.gridsuite.sensitivityanalysis.server.computation.service.ReportContext;
import org.gridsuite.sensitivityanalysis.server.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
public class NonEvacuatedEnergyRunContext extends AbstractComputationRunContext<NonEvacuatedEnergyInputData> {

    private final NonEvacuatedEnergyInputs nonEvacuatedEnergyInputs;

    public NonEvacuatedEnergyRunContext(UUID networkUuid,
                                        String variantId,
                                        NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData,
                                        String receiver,
                                        String provider,
                                        ReportInfos reportInfos,
                                        String userId) {
        super(networkUuid,
                variantId,
                receiver,
                new ReportContext(reportInfos == null ? null : reportInfos.reportUuid(),
                        reportInfos == null ? null : reportInfos.reporterId(),
                        reportInfos == null ? null : reportInfos.reportType()),
                userId,
                provider,
                nonEvacuatedEnergyInputData);
        this.nonEvacuatedEnergyInputs = new NonEvacuatedEnergyInputs();
    }

    public NonEvacuatedEnergyInputData getNonEvacuatedEnergyInputData() {
        return parameters;
    }
}

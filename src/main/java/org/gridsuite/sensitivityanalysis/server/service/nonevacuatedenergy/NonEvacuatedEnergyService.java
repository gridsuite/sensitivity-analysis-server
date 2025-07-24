/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.computation.service.AbstractComputationService;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStatus;
import org.gridsuite.computation.service.UuidGeneratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyService extends AbstractComputationService<NonEvacuatedEnergyRunContext, NonEvacuatedEnergyResultService, NonEvacuatedEnergyStatus> {

    public NonEvacuatedEnergyService(@Value("${non-evacuated-energy.default-provider}") String defaultProvider,
                                     NonEvacuatedEnergyResultService resultService,
                                     UuidGeneratorService uuidGeneratorService,
                                     NonEvacuatedNotificationService notificationService,
                                     ObjectMapper objectMapper) {
        super(notificationService, resultService, objectMapper, uuidGeneratorService, defaultProvider);
    }

    @Override
    public List<String> getProviders() {
        return Collections.singletonList(getDefaultProvider());
    }

    @Override
    public UUID runAndSaveResult(NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext) {
        Objects.requireNonNull(nonEvacuatedEnergyRunContext);

        var resultUuid = uuidGeneratorService.generate();
        // update status to running status
        setStatus(List.of(resultUuid), NonEvacuatedEnergyStatus.RUNNING);
        notificationService.sendRunMessage(new NonEvacuatedEnergyResultContext(resultUuid, nonEvacuatedEnergyRunContext).toMessage(objectMapper));
        return resultUuid;
    }

    public String getRunResult(UUID resultUuid) {
        return resultService.getRunResult(resultUuid);
    }
}

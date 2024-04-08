/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.sensitivityanalysis.server.computation.service.AbstractResultContext;
import org.gridsuite.sensitivityanalysis.server.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.sensitivityanalysis.server.computation.service.NotificationService.HEADER_USER_ID;
import static org.gridsuite.sensitivityanalysis.server.computation.utils.MessageUtils.getNonNullHeader;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class NonEvacuatedEnergyResultContext extends AbstractResultContext<NonEvacuatedEnergyRunContext> {

    public NonEvacuatedEnergyResultContext(UUID resultUuid, NonEvacuatedEnergyRunContext runContext) {
        super(resultUuid, runContext);
    }

    public static NonEvacuatedEnergyResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, "resultUuid"));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, "networkUuid"));
        String variantId = (String) headers.get("variantId");

        String receiver = (String) headers.get("receiver");
        String provider = (String) headers.get("provider");
        String userId = (String) headers.get(HEADER_USER_ID);
        NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData;
        try {
            nonEvacuatedEnergyInputData = objectMapper.readValue(message.getPayload(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) Objects.requireNonNull(headers.get(REPORT_UUID_HEADER))) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext = new NonEvacuatedEnergyRunContext(networkUuid, variantId, nonEvacuatedEnergyInputData, receiver, provider, new ReportInfos(reportUuid, reporterId, reportType), userId);
        return new NonEvacuatedEnergyResultContext(resultUuid, nonEvacuatedEnergyRunContext);
    }
}

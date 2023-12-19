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
import com.powsybl.commons.PowsyblException;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.HEADER_USER_ID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class NonEvacuatedEnergyResultContext {

    private static final String REPORT_UUID = "reportUuid";

    public static final String REPORTER_ID_HEADER = "reporterId";

    public static final String REPORT_TYPE_HEADER = "reportType";

    private final UUID resultUuid;

    private final NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext;

    public NonEvacuatedEnergyResultContext(UUID resultUuid, NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.nonEvacuatedEnergyRunContext = Objects.requireNonNull(nonEvacuatedEnergyRunContext);
    }

    public UUID getResultUuid() {
        return resultUuid;
    }

    public NonEvacuatedEnergyRunContext getRunContext() {
        return nonEvacuatedEnergyRunContext;
    }

    private static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
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
        UUID reportUuid = headers.containsKey(REPORT_UUID) ? UUID.fromString((String) Objects.requireNonNull(headers.get(REPORT_UUID))) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext = new NonEvacuatedEnergyRunContext(networkUuid, variantId, nonEvacuatedEnergyInputData, receiver, provider, reportUuid, reporterId, reportType, userId);
        return new NonEvacuatedEnergyResultContext(resultUuid, nonEvacuatedEnergyRunContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String nonEvacuatedEnergyInputDataJson;
        try {
            nonEvacuatedEnergyInputDataJson = objectMapper.writeValueAsString(nonEvacuatedEnergyRunContext.getNonEvacuatedEnergyInputData());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return MessageBuilder.withPayload(nonEvacuatedEnergyInputDataJson)
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", nonEvacuatedEnergyRunContext.getNetworkUuid().toString())
                .setHeader("variantId", nonEvacuatedEnergyRunContext.getVariantId())
                .setHeader("receiver", nonEvacuatedEnergyRunContext.getReceiver())
                .setHeader("provider", nonEvacuatedEnergyRunContext.getProvider())
                .setHeader(REPORT_UUID, nonEvacuatedEnergyRunContext.getReportUuid())
                .setHeader(REPORTER_ID_HEADER, nonEvacuatedEnergyRunContext.getReporterId())
                .setHeader(REPORT_TYPE_HEADER, nonEvacuatedEnergyRunContext.getReportType())
                .setHeader(HEADER_USER_ID, nonEvacuatedEnergyRunContext.getUserId())
            .build();
    }
}

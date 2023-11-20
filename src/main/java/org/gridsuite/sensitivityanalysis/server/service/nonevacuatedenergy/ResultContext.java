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

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class ResultContext {

    private static final String REPORT_UUID = "reportUuid";

    public static final String REPORTER_ID_HEADER = "reporterId";

    private final UUID resultUuid;

    private final RunContext runContext;

    public ResultContext(UUID resultUuid, RunContext runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
    }

    public UUID getResultUuid() {
        return resultUuid;
    }

    public RunContext getRunContext() {
        return runContext;
    }

    private static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
    }

    public static ResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, "resultUuid"));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, "networkUuid"));
        String variantId = (String) headers.get("variantId");

        String receiver = (String) headers.get("receiver");
        NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData;
        try {
            nonEvacuatedEnergyInputData = objectMapper.readValue(message.getPayload(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID) ? UUID.fromString((String) Objects.requireNonNull(headers.get(REPORT_UUID))) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        RunContext runContext = new RunContext(networkUuid, variantId, nonEvacuatedEnergyInputData, receiver, reportUuid, reporterId);
        return new ResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String nonEvacuatedEnergyInputDataJson;
        try {
            nonEvacuatedEnergyInputDataJson = objectMapper.writeValueAsString(runContext.getInputData());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return MessageBuilder.withPayload(nonEvacuatedEnergyInputDataJson)
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader("variantId", runContext.getVariantId())
                .setHeader("receiver", runContext.getReceiver())
                .setHeader(REPORT_UUID, runContext.getReportUuid())
                .setHeader(REPORTER_ID_HEADER, runContext.getReporterId())
                .build();
    }
}

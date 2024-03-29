/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import org.gridsuite.sensitivityanalysis.server.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.io.UncheckedIOException;
import java.util.*;

import static org.gridsuite.sensitivityanalysis.server.service.NotificationService.HEADER_USER_ID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class SensitivityAnalysisResultContext {

    private static final String REPORT_UUID = "reportUuid";

    public static final String REPORTER_ID_HEADER = "reporterId";

    public static final String REPORT_TYPE_HEADER = "reportType";

    private final UUID resultUuid;

    private final SensitivityAnalysisRunContext runContext;

    public SensitivityAnalysisResultContext(UUID resultUuid, SensitivityAnalysisRunContext runContext) {
        this.resultUuid = Objects.requireNonNull(resultUuid);
        this.runContext = Objects.requireNonNull(runContext);
    }

    public UUID getResultUuid() {
        return resultUuid;
    }

    public SensitivityAnalysisRunContext getRunContext() {
        return runContext;
    }

    private static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
    }

    public static SensitivityAnalysisResultContext fromMessage(Message<String> message, ObjectMapper objectMapper) {
        Objects.requireNonNull(message);
        MessageHeaders headers = message.getHeaders();
        UUID resultUuid = UUID.fromString(getNonNullHeader(headers, "resultUuid"));
        UUID networkUuid = UUID.fromString(getNonNullHeader(headers, "networkUuid"));
        String variantId = (String) headers.get("variantId");

        String receiver = (String) headers.get("receiver");
        String provider = (String) headers.get("provider");
        String userId = (String) headers.get(HEADER_USER_ID);
        SensitivityAnalysisInputData sensitivityAnalysisInputData;
        try {
            sensitivityAnalysisInputData = objectMapper.readValue(message.getPayload(), new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        UUID reportUuid = headers.containsKey(REPORT_UUID) ? UUID.fromString((String) headers.get(REPORT_UUID)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        SensitivityAnalysisRunContext runContext = new SensitivityAnalysisRunContext(networkUuid,
            variantId, sensitivityAnalysisInputData, receiver, provider, new ReportInfos(reportUuid, reporterId, reportType), userId);
        return new SensitivityAnalysisResultContext(resultUuid, runContext);
    }

    public Message<String> toMessage(ObjectMapper objectMapper) {
        String sensitivityAnalysisInputDataJson;
        try {
            sensitivityAnalysisInputDataJson = objectMapper.writeValueAsString(runContext.getSensitivityAnalysisInputData());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return MessageBuilder.withPayload(sensitivityAnalysisInputDataJson)
                .setHeader("resultUuid", resultUuid.toString())
                .setHeader("networkUuid", runContext.getNetworkUuid().toString())
                .setHeader("variantId", runContext.getVariantId())
                .setHeader("receiver", runContext.getReceiver())
                .setHeader("provider", runContext.getProvider())
                .setHeader(REPORT_UUID, runContext.getReportUuid())
                .setHeader(REPORTER_ID_HEADER, runContext.getReporterId())
                .setHeader(REPORT_TYPE_HEADER, runContext.getReportType())
                .setHeader(HEADER_USER_ID, runContext.getUserId())
                .build();
    }
}

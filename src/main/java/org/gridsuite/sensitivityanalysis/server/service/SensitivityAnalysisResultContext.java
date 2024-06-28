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
import com.powsybl.ws.commons.computation.service.AbstractResultContext;
import com.powsybl.ws.commons.computation.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.io.UncheckedIOException;
import java.util.*;

import static com.powsybl.ws.commons.computation.service.NotificationService.HEADER_USER_ID;
import static com.powsybl.ws.commons.computation.utils.MessageUtils.getNonNullHeader;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class SensitivityAnalysisResultContext extends AbstractResultContext<SensitivityAnalysisRunContext> {

    public SensitivityAnalysisResultContext(UUID resultUuid, SensitivityAnalysisRunContext runContext) {
        super(resultUuid, runContext);
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
        UUID reportUuid = headers.containsKey(REPORT_UUID_HEADER) ? UUID.fromString((String) headers.get(REPORT_UUID_HEADER)) : null;
        String reporterId = headers.containsKey(REPORTER_ID_HEADER) ? (String) headers.get(REPORTER_ID_HEADER) : null;
        String reportType = headers.containsKey(REPORT_TYPE_HEADER) ? (String) headers.get(REPORT_TYPE_HEADER) : null;
        SensitivityAnalysisRunContext runContext = new SensitivityAnalysisRunContext(networkUuid,
            variantId, receiver, new ReportInfos(reportUuid, reporterId, reportType), userId, provider, sensitivityAnalysisInputData);
        return new SensitivityAnalysisResultContext(resultUuid, runContext);
    }
}

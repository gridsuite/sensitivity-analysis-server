/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.gridsuite.sensitivityanalysis.server.computation.service.NotificationService;
import org.gridsuite.sensitivityanalysis.server.computation.utils.annotations.PostCompletion;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.gridsuite.sensitivityanalysis.server.computation.utils.MessageUtils.shortenMessage;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com>
 */
@Service
public class NonEvacuatedNotificationService extends NotificationService {
    public NonEvacuatedNotificationService(StreamBridge publisher) {
        super(publisher);
    }

    @Override
    public void sendCancelMessage(Message<String> message) {
        CANCEL_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send("publishNonEvacuatedEnergyCancel-out-0", message);
    }

    @Override
    public void sendRunMessage(Message<String> message) {
        RUN_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send("publishNonEvacuatedEnergyRun-out-0", message);
    }

    @Override
    @PostCompletion
    public void sendResultMessage(UUID resultUuid, String receiver) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .build();
        RESULT_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send("publishNonEvacuatedEnergyResult-out-0", message);
    }

    @Override
    @PostCompletion
    public void publishStop(UUID resultUuid, String receiver, String computationLabel) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_MESSAGE, getCancelMessage(computationLabel))
                .build();
        STOP_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send("publishNonEvacuatedEnergyStopped-out-0", message);
    }

    @Override
    @PostCompletion
    public void publishFail(UUID resultUuid, String receiver, String causeMessage, String userId, String computationLabel) {
        Message<String> message = MessageBuilder
                .withPayload("")
                .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
                .setHeader(HEADER_RECEIVER, receiver)
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_MESSAGE, shortenMessage(
                        getFailedMessage(computationLabel) + " : " + causeMessage))
                .build();
        FAILED_MESSAGE_LOGGER.debug(SENDING_MESSAGE, message);
        publisher.send("publishNonEvacuatedEnergyFailed-out-0", message);
    }
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.gridsuite.sensitivityanalysis.server.util.annotations.PostCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class NotificationService {
    private static final String CANCEL_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.cancel";
    private static final String RUN_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.run";
    private static final String STOP_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.stop";
    private static final String RESULT_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.result";
    private static final String FAILED_CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages.failed";

    private static final Logger RUN_MESSAGE_LOGGER = LoggerFactory.getLogger(RUN_CATEGORY_BROKER_OUTPUT);
    private static final Logger CANCEL_MESSAGE_LOGGER = LoggerFactory.getLogger(CANCEL_CATEGORY_BROKER_OUTPUT);
    private static final Logger STOP_MESSAGE_LOGGER = LoggerFactory.getLogger(STOP_CATEGORY_BROKER_OUTPUT);
    private static final Logger RESULT_MESSAGE_LOGGER = LoggerFactory.getLogger(RESULT_CATEGORY_BROKER_OUTPUT);
    private static final Logger FAILED_MESSAGE_LOGGER = LoggerFactory.getLogger(FAILED_CATEGORY_BROKER_OUTPUT);
    private static final String LOGGER_PREFIX = "Sending message : {}";

    public static final String CANCEL_MESSAGE = "Sensitivity analysis was canceled";
    public static final String FAIL_MESSAGE = "Sensitivity analysis has failed";

    public static final String HEADER_RESULT_UUID = "resultUuid";
    public static final String HEADER_RECEIVER = "receiver";
    public static final String HEADER_MESSAGE = "message";
    public static final String HEADER_USER_ID = "userId";

    private static final int MSG_MAX_LENGTH = 256;

    @Autowired
    private StreamBridge publisher;

    public void sendRunMessage(Message<String> message) {
        RUN_MESSAGE_LOGGER.debug(LOGGER_PREFIX, message);
        publisher.send("publishRun-out-0", message);
    }

    public void sendCancelMessage(Message<String> message) {
        CANCEL_MESSAGE_LOGGER.debug(LOGGER_PREFIX, message);
        publisher.send("publishCancel-out-0", message);
    }

    @PostCompletion
    public void sendResultMessage(UUID resultUuid, String receiver) {
        Message<String> message = MessageBuilder
            .withPayload("")
            .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
            .setHeader(HEADER_RECEIVER, receiver)
            .build();
        RESULT_MESSAGE_LOGGER.debug(LOGGER_PREFIX, message);
        publisher.send("publishResult-out-0", message);
    }

    @PostCompletion
    public void publishStop(UUID resultUuid, String receiver) {
        Message<String> message = MessageBuilder
            .withPayload("")
            .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
            .setHeader(HEADER_RECEIVER, receiver)
            .setHeader(HEADER_MESSAGE, CANCEL_MESSAGE)
            .build();
        STOP_MESSAGE_LOGGER.debug(LOGGER_PREFIX, message);
        publisher.send("publishStopped-out-0", message);
    }

    @PostCompletion
    public void publishFail(UUID resultUuid, String receiver, String causeMessage, String userId) {
        Message<String> message = MessageBuilder
            .withPayload("")
            .setHeader(HEADER_RESULT_UUID, resultUuid.toString())
            .setHeader(HEADER_RECEIVER, receiver)
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_MESSAGE, shortenMessage(FAIL_MESSAGE + " : " + causeMessage))
            .build();
        FAILED_MESSAGE_LOGGER.debug(LOGGER_PREFIX, message);
        publisher.send("publishFailed-out-0", message);
    }

    // prevents the message from being too long for rabbitmq
    // the beginning and ending are both kept, it should make it easier to identify
    public String shortenMessage(String msg) {
        if (msg == null) {
            return msg;
        }

        return msg.length() > MSG_MAX_LENGTH ?
                msg.substring(0, MSG_MAX_LENGTH / 2) + " ... " + msg.substring(msg.length() - MSG_MAX_LENGTH / 2, msg.length() - 1)
                : msg;
    }
}

/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
public final class BatchAsyncPollerFactory {

    public static BatchAsyncPollerFactory getDefault() {
        return new BatchAsyncPollerFactory();
    }

    public <T> BatchAsyncPoller<T> create(ScheduledExecutorService scheduledExecutorService, UUID resultUuid,
                                          String taskName, BiConsumer<UUID, List<T>> batchHandlingFunction) {
        return new BatchAsyncPoller<>(scheduledExecutorService, resultUuid, taskName, batchHandlingFunction);
    }
}

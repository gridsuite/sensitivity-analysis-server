/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
public final class ScheduledThreadPoolFactory {

    public static ScheduledThreadPoolFactory getDefault() {
        return new ScheduledThreadPoolFactory();
    }

    public ScheduledExecutorService create(int size, UUID threadPrefix) {
        Objects.requireNonNull(threadPrefix);

        ThreadFactory factory = new ThreadFactoryBuilder()
                .setNameFormat(threadPrefix + "-%d")
                .setDaemon(false)
                .build();
        return Executors.newScheduledThreadPool(size, factory);
    }
}

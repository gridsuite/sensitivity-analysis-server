/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
class ScheduledThreadPoolFactoryTest {

    private ScheduledThreadPoolFactory scheduledThreadPoolFactory;

    @BeforeEach
    void setUp() {
        scheduledThreadPoolFactory = ScheduledThreadPoolFactory.getDefault();
    }

    @Test
    void whenNewScheduledThreadPoolWithValidInputThenShouldReturnInitializedThreadPool() {
        int threadPoolSize = 4;
        UUID threadPrefix = UUID.randomUUID();

        ScheduledExecutorService executorService = scheduledThreadPoolFactory.create(threadPoolSize, threadPrefix);

        assertNotNull(executorService);
        assertFalse(executorService.isShutdown());
        assertFalse(executorService.isTerminated());

        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) executorService;
        int poolSize = scheduledThreadPoolExecutor.getPoolSize();
        int coreSize = scheduledThreadPoolExecutor.getCorePoolSize();
        assertEquals(0, poolSize);
        assertEquals(threadPoolSize, coreSize);
    }

    @Test
    void whenNewScheduledThreadPoolWithNegativeSizeThenShouldThrowException() {
        int threadPoolSize = -1;
        UUID threadPrefix = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> scheduledThreadPoolFactory.create(threadPoolSize, threadPrefix));
    }

    @Test
    void whenNewScheduledThreadPoolWithNoPrefixThenShouldThrowException() {
        int threadPoolSize = 1;
        UUID threadPrefix = null;

        assertThrows(NullPointerException.class,
                () -> scheduledThreadPoolFactory.create(threadPoolSize, threadPrefix));
    }
}

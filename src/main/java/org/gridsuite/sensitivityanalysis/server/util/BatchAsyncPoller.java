/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
@Slf4j
public class BatchAsyncPoller<T> {

    protected static final int BUFFER_SIZE = 512;
    private static final int TASK_INITIAL_DELAY = 0;
    private static final int TASK_DELAY = 100;

    private final UUID resultUuid;
    private final String taskName;
    private final AtomicBoolean isProducerFinished;
    private final BiConsumer<UUID, List<T>> batchHandlingFunction;

    private final BlockingQueue<T> blockingQueue;
    private final ScheduledFuture<?> pollingFuture;

    public BatchAsyncPoller(ScheduledExecutorService scheduledExecutorService, UUID resultUuid,
                            String taskName, BiConsumer<UUID, List<T>> batchHandlingFunction) {
        this.resultUuid = resultUuid;
        this.taskName = taskName;
        this.batchHandlingFunction = batchHandlingFunction;
        this.isProducerFinished = new AtomicBoolean(false);

        this.blockingQueue = new LinkedBlockingQueue<>();
        this.pollingFuture = scheduledExecutorService.scheduleWithFixedDelay(this::drainQueue, TASK_INITIAL_DELAY, TASK_DELAY, TimeUnit.MILLISECONDS);
    }

    public void add(T data) {
        if (pollingFuture.isDone()) {
            throw new IllegalStateException("Cannot add data to a finished Poller");
        }

        blockingQueue.add(data);
    }

    public void notifyCompletion() {
        isProducerFinished.set(true);
    }

    /**
     * @throws ExecutionException - if one scheduled iteration failed
     */
    public void waitForCompletion() throws ExecutionException {
        try {
            pollingFuture.get();
        } catch (CancellationException e) {
            // pass, this is the nominal case
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * This method makes exceptions bubble if the `batchHandlingFunction` throws one.
     * The goal is to stop the unnecessary consumption and make the calling code know that the process failed at one point.
     * The scheduler will stop it and mark the future with an exception -> a call to `notifyCompletion` will then throw an `ExecutionException`
     */
    private void drainQueue() {
        List<T> buffer = new ArrayList<>(BUFFER_SIZE);

        while (!shouldStop() && hasDrainedData(buffer)) {
            log.debug("{} - Treating {} elements in the batch, {} elements remaining  in the queue", taskName, buffer.size(), blockingQueue.size());
            batchHandlingFunction.accept(resultUuid, new ArrayList<>(buffer));
            buffer.clear();
        }

        if (shouldStop()) {
            pollingFuture.cancel(false);
        }
    }

    private boolean shouldStop() {
        // Thread.currentThread().isInterrupted() check is mandatory for the loop since it doesn't have method calls that checks the flag
        // isProducerFinished.get() && blockingQueue.isEmpty() is also mandatory given the logic inside the calling method
        // it allows to consume all data before leaving the calling loop
        return Thread.currentThread().isInterrupted() || isProducerFinished.get() && blockingQueue.isEmpty();
    }

    private boolean hasDrainedData(List<T> buffer) {
        return blockingQueue.drainTo(buffer, BUFFER_SIZE) > 0;
    }
}

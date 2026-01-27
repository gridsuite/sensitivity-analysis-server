package org.gridsuite.sensitivityanalysis.server.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * @author Ghiles Abdellah <ghiles.abdellah at rte-france.com>
 */
@Slf4j // TODO : self note, we should use Slf4j everywhere and unify logging (LogMarker, log pattern, exception injection, other?)
public class BatchAsyncPoller<T> {

    private static final int TASK_INITIAL_DELAY = 0;
    private static final int TASK_DELAY = 100;
    private static final int BUFFER_SIZE = 512;

    private final UUID resultUuid;
    private final String threadName;
    private final AtomicBoolean isProducerFinished;
    private final BiConsumer<UUID, List<T>> batchHandlingFunction;

    private final BlockingQueue<T> blockingQueue;
    private final ScheduledFuture<?> pollingFuture;

    public BatchAsyncPoller(ScheduledExecutorService scheduledExecutorService, UUID resultUuid,
                            String threadName, AtomicBoolean isProducerFinished,
                            BiConsumer<UUID, List<T>> batchHandlingFunction) {
        this.resultUuid = resultUuid;
        this.threadName = threadName;
        this.isProducerFinished = isProducerFinished;
        this.batchHandlingFunction = batchHandlingFunction;

        this.blockingQueue = new LinkedBlockingQueue<>();
        this.pollingFuture = scheduledExecutorService.scheduleWithFixedDelay(this::drainQueue, TASK_INITIAL_DELAY, TASK_DELAY, TimeUnit.MILLISECONDS);
    }

    public void add(T data) {
        // FIXME this may break the API contract of the calling method ? we should probably ignore the data
        if (pollingFuture.isDone()) {
            throw new IllegalStateException("Cannot add data to a finished poller");
        }

        blockingQueue.add(data);
    }

    public void waitForCompletion() {
        try {
            pollingFuture.get();
        } catch (CancellationException e) {
            log.warn("{} - Task was canceled", threadName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("{} - Unexpected error occurred during completion wait", threadName, e);
        }
    }

    private void drainQueue() {
        try {
            List<T> buffer = new ArrayList<>(BUFFER_SIZE);

            while (!shouldStop() && hasDrainedData(buffer)) {
                log.debug("{} - Treating {} elements in the batch", threadName, buffer.size());
                log.debug("{} - Remaining {} elements in the queue", threadName, blockingQueue.size());
                batchHandlingFunction.accept(resultUuid, buffer);

                // this clear is better than an assignation -> less GC pressure.
                // /!\ batchHandlingFunction should be fully synchronous /!\
                // TODO just put a an assignation to avoid future bug ?
                buffer.clear();
            }
        } catch (Exception e) {
            log.error("{} - Unexpected error occurred during persisting results", threadName, e);
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

/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
class BatchAsyncPollerTest {

    private ScheduledExecutorService scheduledExecutorServiceMock;
    private ScheduledFuture scheduledFutureMock;
    private BiConsumer<UUID, List<Object>> handlerMock;
    private ArgumentCaptor<Runnable> runnableCaptor;
    private Runnable actualRunnable;

    private long expectedInitialDelay;
    private long expectedDelay;
    private int expectedBufferSize;
    private UUID randomUUID;
    private String taskName;
    private Object data;

    public static Stream<Arguments> provideFutureState() {
        return Stream.of(
                Arguments.of(false, false, false, false),
                Arguments.of(true, false, false, false),
                Arguments.of(false, true, false, false),
                Arguments.of(false, false, true, false),
                Arguments.of(false, false, false, true)
        );
    }

    @BeforeEach
    void setUp() {
        scheduledExecutorServiceMock = mock(ScheduledExecutorService.class);
        scheduledFutureMock = mock(ScheduledFuture.class);
        runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        handlerMock = mock(BiConsumer.class);

        randomUUID = UUID.randomUUID();
        taskName = "TestTask";
        data = new Object();

        expectedInitialDelay = 0L;
        expectedDelay = 100L;
        expectedBufferSize = 512;

        when(scheduledExecutorServiceMock.scheduleWithFixedDelay(runnableCaptor.capture(), eq(expectedInitialDelay), eq(expectedDelay), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(scheduledFutureMock);
    }

    @Test
    void whenPollerIsCreatedThenFutureShouldBeInitialized() {
        createBatchAsyncPoller();

        verify(scheduledExecutorServiceMock).scheduleWithFixedDelay(any(Runnable.class), eq(expectedInitialDelay), eq(expectedDelay), eq(TimeUnit.MILLISECONDS));
        assertNotNull(actualRunnable);
        verifyNoInteractions(handlerMock);
    }

    @Test
    void whenFutureCalledOnceAndHasSingleValueThenHandlerShouldBeCalledOnce() {
        List<Object> expectedDataList = List.of(data);
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();

        batchAsyncPoller.add(data);
        actualRunnable.run();

        verify(handlerMock).accept(randomUUID, expectedDataList);
    }

    @Test
    void whenFutureCalledTwiceAndHasSingleValueThenHandlerShouldBeCalledOnce() {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();
        batchAsyncPoller.add(data);
        actualRunnable.run();
        actualRunnable.run();

        verify(handlerMock).accept(any(), anyList());
    }

    @Test
    void whenFutureCalledAndHasValueToBufferThenHandlerShouldBeCalledMultipleTimes() {
        int expectedNumberOfBatch = 2;
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();

        for (int i = 0; i < expectedBufferSize * expectedNumberOfBatch; i++) {
            batchAsyncPoller.add(data);
        }
        actualRunnable.run();

        verify(handlerMock, times(expectedNumberOfBatch)).accept(any(), anyList());
    }

    @Test
    void whenDoneAndAddDataThenShouldThrowException() {
        when(scheduledFutureMock.isDone()).thenReturn(true);

        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();

        assertThrows(IllegalStateException.class, () -> batchAsyncPoller.add(data));
    }

    @Test
    void whenProducerFinishedAndAddDataThenShouldThrowException() {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();
        batchAsyncPoller.notifyCompletion();

        assertThrows(IllegalStateException.class, () -> batchAsyncPoller.add(data));
    }

    @Test
    void whenCompleteThenFutureShouldBeCancelled() {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();

        batchAsyncPoller.notifyCompletion();
        actualRunnable.run();

        verify(scheduledFutureMock).cancel(false);
    }

    @Test
    void whenCompleteAndHasDataThenFutureShouldBeCancelledAfterFullPull() {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();
        batchAsyncPoller.add(data);

        batchAsyncPoller.notifyCompletion();
        actualRunnable.run();

        verify(scheduledFutureMock).cancel(false);
        verify(handlerMock).accept(any(), anyList());
    }

    @Test
    void whenInterruptedAndHasDataThenFutureShouldBeCancelledWithoutPull() {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();
        batchAsyncPoller.add(data);

        Thread.currentThread().interrupt();
        actualRunnable.run();

        verify(scheduledFutureMock).cancel(false);
        verifyNoInteractions(handlerMock);
    }

    @Test
    void whenInterruptedMidComputationAndHasDataThenFutureShouldBeCancelledAfterFirstPull() {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();
        for (int i = 0; i < expectedBufferSize * 2; i++) {
            batchAsyncPoller.add(data);
        }

        doAnswer(unused -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(handlerMock).accept(any(), anyList());
        actualRunnable.run();

        verify(scheduledFutureMock).cancel(false);
        verify(handlerMock).accept(any(), anyList());
    }

    /**
     * the aim is to check if an exception in the handler is propagated to the caller, ie the scheduler that will stop it and mark the future with an exception
     */
    @Test
    void whenExceptionInHandlerThenExceptionShouldBePropagatedToCaller() {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();
        batchAsyncPoller.add(data);

        doThrow(new RuntimeException("TestException"))
                .when(handlerMock).accept(any(), anyList());

        assertThrows(RuntimeException.class, () -> actualRunnable.run());
    }

    @ParameterizedTest
    @MethodSource("provideFutureState")
    void whenWaitForCompletionThenShouldWaitForFuture(boolean isDone, boolean isAbruptlyCanceled, boolean isInterrupted, boolean hasException) throws Exception {
        BatchAsyncPoller<Object> batchAsyncPoller = createBatchAsyncPoller();
        CountDownLatch waitForEndOFComputation = new CountDownLatch(1);

        // default case
        OngoingStubbing<Object> waitForEndOfComputationStubbing = when(scheduledFutureMock.get()).thenAnswer(invocationOnMock -> {
            waitForEndOFComputation.await();
            return null;
        });
        // handle multiple exceptions creation
        if (isDone || isAbruptlyCanceled) {
            waitForEndOfComputationStubbing.thenThrow(CancellationException.class);
            if (isAbruptlyCanceled) {
                waitForEndOfComputationStubbing.thenThrow(CancellationException.class);
            }
        } else if (isInterrupted) {
            waitForEndOfComputationStubbing.thenThrow(InterruptedException.class);
        } else if (hasException) {
            waitForEndOfComputationStubbing.thenThrow(ExecutionException.class);
        }

        CompletableFuture<Void> completableFuture = getCompletableFuture(batchAsyncPoller);
        // should be waiting for the computation end
        assertFalse(completableFuture.isDone());
        // simulate the end of computation
        waitForEndOFComputation.countDown();
        // small wait with timeout
        waitForFuture(completableFuture, isAbruptlyCanceled, hasException);
        assertTrue(completableFuture.isDone());

        verify(scheduledFutureMock).get();
    }

    private CompletableFuture<Void> getCompletableFuture(BatchAsyncPoller<Object> batchAsyncPoller) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        return CompletableFuture.runAsync(() -> {
            try {
                batchAsyncPoller.waitForCompletion();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    private BatchAsyncPoller<Object> createBatchAsyncPoller() {
        BatchAsyncPoller<Object> batchAsyncPoller = new BatchAsyncPoller<>(scheduledExecutorServiceMock, randomUUID, taskName, handlerMock);
        actualRunnable = runnableCaptor.getValue();

        return batchAsyncPoller;
    }

    private void waitForFuture(CompletableFuture<Void> completableFuture, boolean shouldHaveCancellationException, boolean shouldHaveExecutionException) {
        try {
            completableFuture.get(100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fail("Should not timeout");
        } catch (Exception e) {
            if (shouldHaveExecutionException) {
                assertEquals(ExecutionException.class, e.getClass());
            } else if (shouldHaveCancellationException) {
                assertEquals(CancellationException.class, e.getClass());
            } else {
                fail("Should not throw exception");
            }
        }
    }
}

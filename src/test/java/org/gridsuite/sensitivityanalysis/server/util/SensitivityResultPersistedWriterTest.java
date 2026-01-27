/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityValue;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultPersistedWriter.CONTINGENCY_WRITER_THREAD;
import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultPersistedWriter.SENSITIVITY_WRITER_THREAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
class SensitivityResultPersistedWriterTest {

    private SensitivityResultPersistedWriter sensitivityResultPersistedWriter;

    private SensitivityAnalysisResultService sensitivityAnalysisResultServiceMock;
    private ScheduledThreadPoolFactory scheduledThreadPoolFactoryMock;
    private ScheduledExecutorService scheduledExecutorServiceMock;
    private BatchAsyncPollerFactory batchAsyncPollerFactoryMock;
    private BatchAsyncPoller sensitivityPollerMock;
    private BatchAsyncPoller contingencyPollerMock;

    private UUID resultUuid;

    public static Stream<Arguments> provideInvalidSensitivityValue() {
        return Stream.of(
                Arguments.of(1, 2, 0.5, Double.NaN),
                Arguments.of(3, 4, Double.NaN, 0.8)
        );
    }

    @BeforeEach
    void setUp() {
        sensitivityAnalysisResultServiceMock = mock(SensitivityAnalysisResultService.class);
        scheduledThreadPoolFactoryMock = mock(ScheduledThreadPoolFactory.class);
        scheduledExecutorServiceMock = mock(ScheduledExecutorService.class);
        batchAsyncPollerFactoryMock = mock(BatchAsyncPollerFactory.class, RETURNS_DEEP_STUBS);
        sensitivityPollerMock = mock(BatchAsyncPoller.class);
        contingencyPollerMock = mock(BatchAsyncPoller.class);

        resultUuid = UUID.randomUUID();

        when(scheduledThreadPoolFactoryMock.create(2, resultUuid)).thenReturn(scheduledExecutorServiceMock);
        when(batchAsyncPollerFactoryMock.create(eq(scheduledExecutorServiceMock), eq(resultUuid), eq(SENSITIVITY_WRITER_THREAD), any(BiConsumer.class))).thenReturn(sensitivityPollerMock);
        when(batchAsyncPollerFactoryMock.create(eq(scheduledExecutorServiceMock), eq(resultUuid), eq(CONTINGENCY_WRITER_THREAD), any(BiConsumer.class))).thenReturn(contingencyPollerMock);

        sensitivityResultPersistedWriter = new SensitivityResultPersistedWriter(resultUuid, sensitivityAnalysisResultServiceMock, scheduledThreadPoolFactoryMock, batchAsyncPollerFactoryMock);
    }

    @Test
    void whenWriterIsCreatedThenTwoPollerAreCreatedWithExpectedParam() {
        verify(batchAsyncPollerFactoryMock).create(eq(scheduledExecutorServiceMock), eq(resultUuid), eq(SENSITIVITY_WRITER_THREAD), any(BiConsumer.class));
        verify(batchAsyncPollerFactoryMock).create(eq(scheduledExecutorServiceMock), eq(resultUuid), eq(CONTINGENCY_WRITER_THREAD), any(BiConsumer.class));
    }

    @Test
    void whenWriteSensitivityValueCalledWithValidValuesThenValueIsAddedToPoller() {
        int expectedFactorIndex = 1;
        int expectedContingencyIndex = 2;
        double expectedValue = 0.5;
        double expectedFunctionReference = 1.0;

        sensitivityResultPersistedWriter.writeSensitivityValue(expectedFactorIndex, expectedContingencyIndex, expectedValue, expectedFunctionReference);

        ArgumentCaptor<SensitivityValue> sensitivityValueCaptor = ArgumentCaptor.forClass(SensitivityValue.class);
        verify(sensitivityPollerMock).add(sensitivityValueCaptor.capture());
        SensitivityValue actualSensitivityValue = sensitivityValueCaptor.getValue();

        assertEquals(expectedFactorIndex, actualSensitivityValue.getFactorIndex());
        assertEquals(expectedContingencyIndex, actualSensitivityValue.getContingencyIndex());
        assertEquals(expectedValue, actualSensitivityValue.getValue(), 0.0);
        assertEquals(expectedFunctionReference, actualSensitivityValue.getFunctionReference(), 0.0);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidSensitivityValue")
    void whenWriteSensitivityValueCalledWithInvalidValuesThenShouldDoNothing(int expectedFactorIndex, int expectedContingencyIndex, double expectedValue, double expectedFunctionReference) {
        sensitivityResultPersistedWriter.writeSensitivityValue(expectedFactorIndex, expectedContingencyIndex, expectedValue, expectedFunctionReference);

        verifyNoInteractions(sensitivityPollerMock);
    }

    @Test
    void whenWriteContingencyStatusCalledWithValidValuesThenValueIsAddedToPoller() {
        int expectedContingencyIndex = 2;
        SensitivityAnalysisResult.Status expectedStatus = SensitivityAnalysisResult.Status.SUCCESS;
        ContingencyResult expectedContingencyResult = new ContingencyResult(expectedContingencyIndex, expectedStatus);

        sensitivityResultPersistedWriter.writeContingencyStatus(expectedContingencyIndex, expectedStatus);

        verify(contingencyPollerMock).add(expectedContingencyResult);
    }

    @Test
    void whenWriterWasClosedThenShouldThrowExceptionOnSensitivityWrite() {
        int expectedFactorIndex = 1;
        int expectedContingencyIndex = 2;
        double expectedValue = 0.5;
        double expectedFunctionReference = 1.0;

        when(scheduledExecutorServiceMock.isShutdown()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> sensitivityResultPersistedWriter.writeSensitivityValue(expectedFactorIndex, expectedContingencyIndex, expectedValue, expectedFunctionReference));
    }

    @Test
    void whenWriterWasClosedThenShouldThrowExceptionOnContingencyWrite() {
        int expectedContingencyIndex = 2;
        SensitivityAnalysisResult.Status expectedStatus = SensitivityAnalysisResult.Status.SUCCESS;

        when(scheduledExecutorServiceMock.isShutdown()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> sensitivityResultPersistedWriter.writeContingencyStatus(expectedContingencyIndex, expectedStatus));
    }

    @Test
    void whenWriterCloseThenShouldShutdownExecutorService() {
        sensitivityResultPersistedWriter.close();

        verify(scheduledExecutorServiceMock).shutdownNow();
    }

    @Test
    void whenNotifCompletionThenShouldNotifyAllPoller() {
        sensitivityResultPersistedWriter.notifyCompletion();

        verify(sensitivityPollerMock).notifyCompletion();
        verify(contingencyPollerMock).notifyCompletion();
    }

    @Test
    void whenWaitForCompletionThenShouldWaitAllPoller() throws Exception {
        sensitivityResultPersistedWriter.waitForCompletion();

        verify(sensitivityPollerMock).waitForCompletion();
        verify(contingencyPollerMock).waitForCompletion();
    }

    @Test
    void whenWaitForCompletionAndFirstPollerThrowsThenShouldThrowAndStillWaitForSecondPoller() throws Exception {
        doThrow(new RuntimeException("Mocked exception")).when(sensitivityPollerMock).waitForCompletion();

        assertThrows(ExecutionException.class, () -> sensitivityResultPersistedWriter.waitForCompletion());

        verify(sensitivityPollerMock).waitForCompletion();
        verify(contingencyPollerMock).waitForCompletion();
    }

    @Test
    void whenWaitForCompletionAndSecondPollerThrowsThenShouldThrow() throws Exception {
        doThrow(new RuntimeException("Mocked exception")).when(contingencyPollerMock).waitForCompletion();

        assertThrows(ExecutionException.class, () -> sensitivityResultPersistedWriter.waitForCompletion());

        verify(sensitivityPollerMock).waitForCompletion();
        verify(contingencyPollerMock).waitForCompletion();
    }

    @Test
    void whenWaitForCompletionAndFirstPollerInterruptedThenShouldPropagateAndIgnoreSecondPoller() throws Exception {
        doThrow(new InterruptedException("Mocked exception")).when(sensitivityPollerMock).waitForCompletion();

        assertThrows(InterruptedException.class, () -> sensitivityResultPersistedWriter.waitForCompletion());

        assertTrue(Thread.interrupted());
        verify(sensitivityPollerMock).waitForCompletion();
        verifyNoInteractions(contingencyPollerMock);
    }

    @Test
    void whenWaitForCompletionAndSecondPollerInterruptedThenShouldPropagate() throws Exception {
        doThrow(new InterruptedException("Mocked exception")).when(contingencyPollerMock).waitForCompletion();

        assertThrows(InterruptedException.class, () -> sensitivityResultPersistedWriter.waitForCompletion());

        assertTrue(Thread.interrupted());
        verify(sensitivityPollerMock).waitForCompletion();
        verify(contingencyPollerMock).waitForCompletion();
    }
}

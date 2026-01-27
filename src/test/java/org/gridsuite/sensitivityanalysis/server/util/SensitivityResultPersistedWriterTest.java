/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 * @author Jon Schuhmacher <jon.harper at at rte-france.com>
 */
class SensitivityResultPersistedWriterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityResultPersistedWriterTest.class);

    private final SensitivityAnalysisResultService analysisResultService = Mockito.mock(SensitivityAnalysisResultService.class);

    private SensitivityResultPersistedWriter resultWriterPersisted;

    @BeforeEach
    void setUp() {
        Mockito.reset(analysisResultService);

        doAnswer(answer -> {
            try {
                LOGGER.info(() -> "writing sensitivity results");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(analysisResultService).writeSensitivityValues(any(), anyList());

        doAnswer(answer -> {
            try {
                LOGGER.info(() -> "writing contingency statuses");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(analysisResultService).writeContingenciesStatus(any(), anyList());

        resultWriterPersisted = new SensitivityResultPersistedWriter(UUID.randomUUID(), analysisResultService);
    }

    @AfterEach
    void tearDown() {
        resultWriterPersisted.close();
    }
/*
    private void testOperating(boolean started, boolean finished, boolean interrupted) throws InterruptedException {
        if (started) {
            resultWriterPersisted.start();
        }
        if (interrupted) {
            resultWriterPersisted.interrupt();
        }
        resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.);
        resultWriterPersisted.writeContingencyStatus(0, SensitivityAnalysisResult.Status.SUCCESS);
        assertFalse(resultWriterPersisted.isConsumerFinished());
        if (finished) {
            resultWriterPersisted.onProducerFinished();
        }
        // This test always uses Thread.sleep, even though when
        // we start and either finish or interrupt we could just wait for isConsumerFinished()
        // This is done to ensure that sleeping correctly exhibit the tested behavior in the cases
        // where we can't wait for isConsumerFinished()
        // This makes the test slow so don't overuse it.
        // When we rewrite this test class to avoid real delays, we can improve this (for example
        // using a countdownlatch to guarantee execution order around SensitivityAnalysisResultService
        // method calls)
        Thread.sleep(500);
        assertEquals(started && (finished || interrupted), resultWriterPersisted.isConsumerFinished());
        int times = started && !interrupted ? 1 : 0;
        verify(analysisResultService, times(times)).writeSensitivityValues(any(), anyList());
        verify(analysisResultService, times(times)).writeContingenciesStatus(any(), anyList());
    }

    @Test
    void testNotOperatingIfNotStarted() throws InterruptedException {
        testOperating(false, false, false);
    }

    @Test
    void testOperatingIfStartedNotFinished() throws InterruptedException {
        testOperating(true, false, false);
    }

    @Test
    void testOperatingIfStartedInterrupted() throws InterruptedException {
        testOperating(true, false, true);
    }

    @Test
    void testOperatingIfStartedFinished() throws InterruptedException {
        testOperating(true, true, false);
    }*/
/*
    private void testWritingValue(Runnable writeOne, Consumer<SensitivityAnalysisResultService> verify, boolean batched, boolean throwing) {
        if (throwing) {
            doThrow(new RuntimeException("Error persisting sensitivity values"))
                .when(analysisResultService)
                .writeSensitivityValues(any(), anyList());
            doThrow(new RuntimeException("Error persisting contingency statuses"))
                .when(analysisResultService)
                .writeContingenciesStatus(any(), anyList());
        }
        resultWriterPersisted.start();
        if (batched) {
            IntStream.range(0, SensitivityResultWriterPersisted.BUFFER_SIZE * 3 / 2).forEach(i -> writeOne.run());
        } else {
            writeOne.run();
        }
        resultWriterPersisted.onProducerFinished();
        await().atMost(1000, TimeUnit.MILLISECONDS).until(() -> resultWriterPersisted.isConsumerFinished());
        if (batched && !throwing) {
            verify.accept(verify(analysisResultService, atLeast(2)));
            verify.accept(verify(analysisResultService, atMost(3)));
        } else {
            verify.accept(verify(analysisResultService, times(1)));
        }
    }
    private void testWritingSensitivityValue(boolean batched, boolean throwing) {
        testWritingValue(
            () -> resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.),
            verify -> verify.writeSensitivityValues(any(), anyList()),
            batched, throwing
        );
    }

    @Test
    void testWritingOneSensitivityValue() {
        testWritingSensitivityValue(false, false);
    }

    @Test
    void testWritingSeveralSensitivityValuesIsBatched() {
        testWritingSensitivityValue(true, false);
    }

    private void testWritingContingencyValue(boolean batched, boolean throwing) {
        testWritingValue(
                () -> resultWriterPersisted.writeContingencyStatus(0, SensitivityAnalysisResult.Status.SUCCESS),
                verify -> verify.writeContingenciesStatus(any(), anyList()),
                batched, throwing
        );
    }

    @Test
    void testWritingOneContingencyStatus() {
        testWritingContingencyValue(false, false);
    }

    @Test
    void testWritingSeveralContingencyStatusesIsBatched() {
        testWritingContingencyValue(true, false);
    }

    @Test
    void testIsEndingIfErrorOccursPersistingSensitivityValues() {
        testWritingSensitivityValue(false, true);
    }

    @Test
    void testIsEndingIfErrorOccursPersistingContingencyStatuses() {
        testWritingContingencyValue(false, true);
    }*/
}

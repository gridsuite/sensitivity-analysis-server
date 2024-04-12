/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
class SensitivityResultWriterPersistedTest {
    public static final Logger LOGGER = LoggerFactory.getLogger(SensitivityResultWriterPersistedTest.class);

    private final SensitivityAnalysisResultRepository analysisResultRepository = Mockito.mock(SensitivityAnalysisResultRepository.class);

    @SpyBean
    private SensitivityResultWriterPersisted resultWriterPersisted;

    @BeforeEach
    void setUp() {
        Mockito.reset(analysisResultRepository);

        doAnswer(answer -> {
            try {
                LOGGER.info(() -> "writing sensitivity results");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(analysisResultRepository).writeSensitivityValues(any(), anyList());

        doAnswer(answer -> {
            try {
                LOGGER.info(() -> "writing contingency statuses");
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(analysisResultRepository).writeContingenciesStatus(any(), anyList());

        resultWriterPersisted = Mockito.spy(new SensitivityResultWriterPersisted(analysisResultRepository));
    }

    @AfterEach
    void tearDown() {
        resultWriterPersisted.interrupt();
    }

    @Test
    void testNotOperatingIfNotStarted() {
        verify(analysisResultRepository, times(0)).writeSensitivityValues(any(), anyList());
        resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.);
        assertTrue(resultWriterPersisted.isWorking());
        verify(analysisResultRepository, times(0)).writeSensitivityValues(any(), anyList());
    }

    @Test
    void testWritingOneSensitivityValue() {
        verify(analysisResultRepository, times(0)).writeSensitivityValues(any(), anyList());
        resultWriterPersisted.start(UUID.randomUUID());
        assertFalse(resultWriterPersisted.isWorking());

        resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.);
        await().atMost(500, TimeUnit.MILLISECONDS).until(resultWriterPersisted::isWorking);
        await().atMost(1, TimeUnit.SECONDS).until(() -> !resultWriterPersisted.isWorking());
        verify(analysisResultRepository, times(1)).writeSensitivityValues(any(), anyList());
    }

    @Test
    void testWritingSeveralSensitivityValuesIsBatched() {
        verify(analysisResultRepository, times(0)).writeSensitivityValues(any(), anyList());
        resultWriterPersisted.start(UUID.randomUUID());
        assertFalse(resultWriterPersisted.isWorking());

        IntStream.range(0, 1000).forEach(i -> resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.));
        assertTrue(resultWriterPersisted.isWorking());

        await().atMost(1000, TimeUnit.MILLISECONDS).until(() -> !resultWriterPersisted.isWorking());
        verify(analysisResultRepository, times(2)).writeSensitivityValues(any(), anyList());
    }

    @Test
    void testWritingOneContingencyStatus() {
        verify(analysisResultRepository, times(0)).writeSensitivityValues(any(), anyList());
        resultWriterPersisted.start(UUID.randomUUID());
        assertFalse(resultWriterPersisted.isWorking());

        resultWriterPersisted.writeContingencyStatus(0, SensitivityAnalysisResult.Status.SUCCESS);
        assertTrue(resultWriterPersisted.isWorking());

        await().atMost(500, TimeUnit.MILLISECONDS).until(() -> !resultWriterPersisted.isWorking());
        verify(analysisResultRepository, times(1)).writeContingenciesStatus(any(), anyList());
    }

    @Test
    void testWritingSeveralContingencyStatusesIsBatched() {
        verify(analysisResultRepository, times(0)).writeSensitivityValues(any(), anyList());
        resultWriterPersisted.start(UUID.randomUUID());
        assertFalse(resultWriterPersisted.isWorking());

        IntStream.range(0, 1000).forEach(i -> resultWriterPersisted.writeContingencyStatus(0, SensitivityAnalysisResult.Status.SUCCESS));
        assertTrue(resultWriterPersisted.isWorking());

        await().atMost(1000, TimeUnit.MILLISECONDS).until(() -> !resultWriterPersisted.isWorking());
        verify(analysisResultRepository, times(2)).writeContingenciesStatus(any(), anyList());
    }

    @Test
    void testNotOperatingAfterInterruption() {
        resultWriterPersisted.start(UUID.randomUUID());
        resultWriterPersisted.interrupt();
        resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.);
        await().atLeast(500, TimeUnit.MILLISECONDS);
        verify(analysisResultRepository, times(0)).writeSensitivityValues(any(), anyList());
    }

    @Test
    void testIsEndingIfErrorOccursPersistingSensitivityValues() {
        doThrow(new RuntimeException("Error persisting sensitivity values"))
            .when(analysisResultRepository)
            .writeSensitivityValues(any(), anyList());
        resultWriterPersisted.start(UUID.randomUUID());
        IntStream.range(0, 1000).forEach(i -> resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.));
        await().atMost(1000, TimeUnit.MILLISECONDS).until(() -> !resultWriterPersisted.isWorking());
    }

    @Test
    void testIsEndingIfErrorOccursPersistingContingencyStatuses() {
        doThrow(new RuntimeException("Error persisting contingency statuses"))
            .when(analysisResultRepository)
            .writeContingenciesStatus(any(), anyList());
        resultWriterPersisted.start(UUID.randomUUID());
        IntStream.range(0, 1000).forEach(i -> resultWriterPersisted.writeContingencyStatus(0, SensitivityAnalysisResult.Status.SUCCESS));
        await().atMost(1000, TimeUnit.MILLISECONDS).until(() -> !resultWriterPersisted.isWorking());
    }

    @Test
    void testIsEndingIfErrorOccursInTheRunMethodWhileWritingSensitivityValues() {
        doThrow(new RuntimeException("Error in threads"))
            .when(resultWriterPersisted)
            .run(any(), any(), any(), any(SensitivityResultWriterPersisted.BatchedRunnable.class));
        resultWriterPersisted.start(UUID.randomUUID());
        IntStream.range(0, 1000).forEach(i -> resultWriterPersisted.writeSensitivityValue(0, 0, 0., 0.));
        await().atMost(1000, TimeUnit.MILLISECONDS).until(() -> !resultWriterPersisted.isWorking());
    }

    @Test
    void testIsEndingIfErrorOccursInTheRunMethodWhileWritingContingencyResults() {
        doThrow(new RuntimeException("Error in threads"))
            .when(resultWriterPersisted)
            .run(any(), any(), any(), any(SensitivityResultWriterPersisted.BatchedRunnable.class));
        resultWriterPersisted.start(UUID.randomUUID());
        IntStream.range(0, 1000).forEach(i -> resultWriterPersisted.writeContingencyStatus(0, SensitivityAnalysisResult.Status.SUCCESS));
        await().atMost(1000, TimeUnit.MILLISECONDS).until(() -> !resultWriterPersisted.isWorking());
    }
}

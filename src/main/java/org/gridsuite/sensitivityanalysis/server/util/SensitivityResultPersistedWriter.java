/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityResultWriter;
import com.powsybl.sensitivity.SensitivityValue;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisResultService;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
public class SensitivityResultPersistedWriter implements SensitivityResultWriter, AutoCloseable {

    protected static final String SENSITIVITY_WRITER_THREAD = "sensitivityWriterThread";
    protected static final String CONTINGENCY_WRITER_THREAD = "contingencyWriterThread";
    private static final int THREAD_POOL_SIZE = 2;

    private final ScheduledExecutorService scheduledExecutorService;
    private final BatchAsyncPoller<SensitivityValue> sensitivityBatchAsyncPoller;
    private final BatchAsyncPoller<ContingencyResult> contingencyBatchAsyncPoller;

    public SensitivityResultPersistedWriter(UUID resultUuid, SensitivityAnalysisResultService sensitivityAnalysisResultService,
                                            ScheduledThreadPoolFactory scheduledThreadPoolFactory, BatchAsyncPollerFactory batchAsyncPollerFactory) {
        this.scheduledExecutorService = scheduledThreadPoolFactory.create(THREAD_POOL_SIZE, resultUuid);
        this.sensitivityBatchAsyncPoller = batchAsyncPollerFactory.create(this.scheduledExecutorService, resultUuid, SENSITIVITY_WRITER_THREAD, sensitivityAnalysisResultService::writeSensitivityValues);
        this.contingencyBatchAsyncPoller = batchAsyncPollerFactory.create(this.scheduledExecutorService, resultUuid, CONTINGENCY_WRITER_THREAD, sensitivityAnalysisResultService::writeContingenciesStatus);
    }

    @Override
    public void writeSensitivityValue(int factorIndex, int contingencyIndex, double value, double functionReference) {
        throwOnExecutorShutdown();

        if (Double.isNaN(functionReference) || Double.isNaN(value)) {
            return;
        }
        sensitivityBatchAsyncPoller.add(new SensitivityValue(factorIndex, contingencyIndex, value, functionReference));
    }

    @Override
    public void writeContingencyStatus(int contingencyIndex, SensitivityAnalysisResult.Status status) {
        throwOnExecutorShutdown();

        contingencyBatchAsyncPoller.add(new ContingencyResult(contingencyIndex, status));
    }

    @Override
    public void close() {
        scheduledExecutorService.shutdownNow();
    }

    public void notifyCompletion() {
        sensitivityBatchAsyncPoller.notifyCompletion();
        contingencyBatchAsyncPoller.notifyCompletion();
    }

    /**
     * If the writer is finished, this method blocks until all the data has been written.
     * Except due to InterruptedException, we choose here to explicitly wait for both pollers to finish. It's a choice for simplicity and symmetricity.
     * The resulting exception (if any) is either
     * - ExecutionException that is composed with all the suppressed exceptions.
     * - InterruptedException if any was triggered while waiting for one of the subtasks -> this means we don't guarantee the call to wait of all subtasks
     */
    public void waitForCompletion() throws InterruptedException, ExecutionException {
        Exception sensitivityException = waitForSensitivityException();
        Exception contingencyException = waitForContingencyException();

        throwResultingException(sensitivityException, contingencyException);
    }

    private void throwResultingException(Exception... exceptions) throws ExecutionException {
        List<Exception> nonNullExceptions = Arrays.stream(exceptions)
                .filter(Objects::nonNull)
                .toList();

        if (!nonNullExceptions.isEmpty()) {
            // nonNullExceptions.getFirst() is a shortcut since we don't care which exception threw first... this can be improved
            ExecutionException computedException = new ExecutionException("At least one Poller failed", nonNullExceptions.getFirst());
            nonNullExceptions.forEach(computedException::addSuppressed);
            throw computedException;
        }
    }

    private Exception waitForSensitivityException() throws InterruptedException {
        try {
            sensitivityBatchAsyncPoller.waitForCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            return e;
        }
        return null;
    }

    private Exception waitForContingencyException() throws InterruptedException {
        try {
            contingencyBatchAsyncPoller.waitForCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            return e;
        }
        return null;
    }

    private void throwOnExecutorShutdown() {
        if (scheduledExecutorService.isShutdown()) {
            throw new IllegalStateException("Cannot add data to a finished Writer");
        }
    }
}

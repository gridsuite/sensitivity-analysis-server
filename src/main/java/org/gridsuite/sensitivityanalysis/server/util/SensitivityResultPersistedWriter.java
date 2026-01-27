/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityResultWriter;
import com.powsybl.sensitivity.SensitivityValue;
import org.gridsuite.sensitivityanalysis.server.service.SensitivityAnalysisResultService;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Ghiles Abdellah <ghiles.abdellah at rte-france.com>
 */
public class SensitivityResultPersistedWriter implements SensitivityResultWriter, AutoCloseable {

    private static final String SENSITIVITY_WRITER_THREAD = "sensitivityWriterThread";
    private static final String CONTINGENCY_WRITER_THREAD = "contingencyWriterThread";

    private final ScheduledExecutorService scheduledExecutorService;
    private final BatchAsyncPoller<SensitivityValue> sensitivityBatchAsyncPoller;
    private final BatchAsyncPoller<ContingencyResult> contingencyBatchAsyncPoller;
    private final AtomicBoolean isProducerFinished;

    public SensitivityResultPersistedWriter(UUID resultUuid, SensitivityAnalysisResultService sensitivityAnalysisResultService) {
        this.isProducerFinished = new AtomicBoolean(false);

        this.scheduledExecutorService = Executors.newScheduledThreadPool(2); // TODO pass a custom ThreadFactory for thread naming ?
        this.sensitivityBatchAsyncPoller = new BatchAsyncPoller<>(this.scheduledExecutorService, resultUuid, SENSITIVITY_WRITER_THREAD, isProducerFinished, sensitivityAnalysisResultService::writeSensitivityValues);
        this.contingencyBatchAsyncPoller = new BatchAsyncPoller<>(this.scheduledExecutorService, resultUuid, CONTINGENCY_WRITER_THREAD, isProducerFinished, sensitivityAnalysisResultService::writeContingenciesStatus);
    }

    @Override
    public void writeSensitivityValue(int factorIndex, int contingencyIndex, double value, double functionReference) {
        if (Double.isNaN(functionReference) || Double.isNaN(value)) {
            return;
        }
        sensitivityBatchAsyncPoller.add(new SensitivityValue(factorIndex, contingencyIndex, value, functionReference));
    }

    @Override
    public void writeContingencyStatus(int contingencyIndex, SensitivityAnalysisResult.Status status) {
        contingencyBatchAsyncPoller.add(new ContingencyResult(contingencyIndex, status));
    }

    @Override
    public void close() {
        scheduledExecutorService.shutdownNow();
    }

    public void notifyCompletion() {
        isProducerFinished.set(true);
    }

    public void waitForCompletion() {
        sensitivityBatchAsyncPoller.waitForCompletion();
        contingencyBatchAsyncPoller.waitForCompletion();
    }
}

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

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
public class SensitivityResultPersistedWriter implements SensitivityResultWriter, AutoCloseable {

    protected static final String SENSITIVITY_WRITER_THREAD = "sensitivityWriterThread";
    protected static final String CONTINGENCY_WRITER_THREAD = "contingencyWriterThread";

    private final ScheduledExecutorService scheduledExecutorService;
    private final BatchAsyncPoller<SensitivityValue> sensitivityBatchAsyncPoller;
    private final BatchAsyncPoller<ContingencyResult> contingencyBatchAsyncPoller;

    public SensitivityResultPersistedWriter(UUID resultUuid, SensitivityAnalysisResultService sensitivityAnalysisResultService,
                                            ExecutorProviderService executorProviderService, BatchAsyncPollerFactory batchAsyncPollerFactory) {
        this.scheduledExecutorService = executorProviderService.newScheduledThreadPool(2, resultUuid);
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

    public void waitForCompletion() throws ExecutionException {
        sensitivityBatchAsyncPoller.waitForCompletion();
        contingencyBatchAsyncPoller.waitForCompletion();
    }

    private void throwOnExecutorShutdown() {
        if (scheduledExecutorService.isShutdown()) {
            throw new IllegalStateException("Cannot add data to a finished Writer");
        }
    }
}

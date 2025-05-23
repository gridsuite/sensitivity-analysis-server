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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public class SensitivityResultWriterPersisted implements SensitivityResultWriter {
    public static final Logger LOGGER = LoggerFactory.getLogger(SensitivityResultWriterPersisted.class);

    public static final int BUFFER_SIZE = 512;

    private final SensitivityAnalysisResultService sensitivityAnalysisResultService;

    private final BlockingQueue<SensitivityValue> sensitivityValuesQueue;

    private final BlockingQueue<ContingencyResult> contingencyResultsQueue;

    private final Thread sensitivityValuesThread;

    private final Thread contingencyResultsThread;

    private final AtomicBoolean sensitivityValuesWorking;

    private final AtomicBoolean contingencyResultsWorking;

    private UUID resultUuid;

    public SensitivityResultWriterPersisted(UUID resultUuid, SensitivityAnalysisResultService sensitivityAnalysisResultService) {
        this.sensitivityAnalysisResultService = sensitivityAnalysisResultService;
        sensitivityValuesQueue = new LinkedBlockingQueue<>();
        contingencyResultsQueue = new LinkedBlockingQueue<>();
        sensitivityValuesThread = new Thread(sensitivityValuesBatchedHandling(), "sensitivityWriterThread");
        contingencyResultsThread = new Thread(contingencyResultsBatchedHandling(), "contingencyWriterThread");
        sensitivityValuesWorking = new AtomicBoolean(false);
        contingencyResultsWorking = new AtomicBoolean(false);
        this.resultUuid = resultUuid;
    }

    public void start() {
        sensitivityValuesThread.start();
        contingencyResultsThread.start();
    }

    public void interrupt() {
        sensitivityValuesThread.interrupt();
        contingencyResultsThread.interrupt();
    }

    public boolean isWorking() {
        return !sensitivityValuesQueue.isEmpty()
            || !contingencyResultsQueue.isEmpty()
            || sensitivityValuesWorking.get()
            || contingencyResultsWorking.get();
    }

    @Override
    public void writeSensitivityValue(int factorIndex, int contingencyIndex, double value, double functionReference) {
        if (Double.isNaN(functionReference) || Double.isNaN(value)) {
            return;
        }
        sensitivityValuesQueue.add(new SensitivityValue(factorIndex, contingencyIndex, value, functionReference));
    }

    @Override
    public void writeContingencyStatus(int contingencyIndex, SensitivityAnalysisResult.Status status) {
        contingencyResultsQueue.add(new ContingencyResult(contingencyIndex, status));
    }

    private Runnable sensitivityValuesBatchedHandling() {
        return () -> run(
            sensitivityValuesThread,
            sensitivityValuesWorking,
            sensitivityValuesQueue,
            sensitivityAnalysisResultService::writeSensitivityValues
        );
    }

    private Runnable contingencyResultsBatchedHandling() {
        return () -> run(
            contingencyResultsThread,
            contingencyResultsWorking,
            contingencyResultsQueue,
            sensitivityAnalysisResultService::writeContingenciesStatus
        );
    }

    private interface BatchedRunnable<T> {
        void run(UUID resultUuid, List<T> tasks);
    }

    private <T> void run(Thread thread, AtomicBoolean isWorking, BlockingQueue<T> queue, BatchedRunnable<T> runnable) {
        try {
            while (!thread.isInterrupted()) {
                List<T> tasks = new ArrayList<>(BUFFER_SIZE);
                while (queue.drainTo(tasks, BUFFER_SIZE) == 0) {
                    Thread.sleep(100);
                }
                LOGGER.debug("{} - Remaining {} elements in the queue", thread.getName(), queue.size());
                if (!tasks.isEmpty()) {
                    LOGGER.debug("{} - Treating {} elements in the batch", thread.getName(), tasks.size());
                    isWorking.set(true);
                    runnable.run(resultUuid, tasks);
                    isWorking.set(false);
                }
            }
        } catch (InterruptedException e) {
            LOGGER.debug("Thread {} has been interrupted", thread.getName());
            thread.interrupt();
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred during persisting results", e);
            queue.clear();
            isWorking.set(false);
        }
    }
}

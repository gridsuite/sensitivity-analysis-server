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
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public class SensitivityResultWriterPersisted implements SensitivityResultWriter, AutoCloseable {
    public static final Logger LOGGER = LoggerFactory.getLogger(SensitivityResultWriterPersisted.class);

    public static final int BUFFER_SIZE = 256;

    private final SensitivityAnalysisResultRepository sensitivityAnalysisResultRepository;

    private UUID resultUuid;

    BlockingQueue<SensitivityValue> sensitivityValuesQueue = new LinkedBlockingQueue<>();

    BlockingQueue<SensitivityAnalysisResultRepository.ContingencyResult> contingencyResultsQueue = new LinkedBlockingQueue<>();
    Thread sensitivityValuesThread;

    Thread contingencyResultsThread;

    boolean sensitivityValuesWorking = false;

    boolean contingencyResultsWorking = false;

    public SensitivityResultWriterPersisted(SensitivityAnalysisResultRepository sensitivityAnalysisResultRepository) {
        this.sensitivityAnalysisResultRepository = sensitivityAnalysisResultRepository;
    }

    public void start(UUID resultUuid) {
        this.resultUuid = resultUuid;
        sensitivityValuesThread = new Thread(sensitivityValuesBatchedHandling());
        sensitivityValuesThread.setDaemon(true);
        sensitivityValuesThread.start();
        contingencyResultsThread = new Thread(contingencyResultsBatchedHandling());
        contingencyResultsThread.setDaemon(true);
        contingencyResultsThread.start();
    }

    private Runnable sensitivityValuesBatchedHandling() {
        return () -> {
            while (!sensitivityValuesThread.isInterrupted()) {
                List<SensitivityValue> tasks = new ArrayList<>(BUFFER_SIZE);
                while (sensitivityValuesQueue.drainTo(tasks, BUFFER_SIZE) == 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        sensitivityValuesThread.interrupt();
                    }
                }
                LOGGER.info("Remaining {} elements in the queue", sensitivityValuesQueue.size());
                LOGGER.info("Treating {} elements in the batch", tasks.size());
                sensitivityValuesWorking = true;
                sensitivityAnalysisResultRepository.writeSensitivityValues(resultUuid, tasks);
                sensitivityValuesWorking = false;
            }
        };
    }

    private Runnable contingencyResultsBatchedHandling() {
        return () -> {
            while (!contingencyResultsThread.isInterrupted()) {
                List<SensitivityAnalysisResultRepository.ContingencyResult> tasks = new ArrayList<>(BUFFER_SIZE);
                while (contingencyResultsQueue.drainTo(tasks, BUFFER_SIZE) == 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        contingencyResultsThread.interrupt();
                    }
                }
                contingencyResultsWorking = true;
                sensitivityAnalysisResultRepository.writeContingenciesStatus(resultUuid, tasks);
                contingencyResultsWorking = false;
            }
        };
    }

    @Override
    public void writeSensitivityValue(int factorIndex, int contingencyIndex, double value, double functionReference) {
        if (Double.isNaN(value) || Double.isNaN(functionReference)) {
            return;
        }
        sensitivityValuesQueue.add(new SensitivityValue(factorIndex, contingencyIndex, value, functionReference));
    }

    @Override
    public void writeContingencyStatus(int contingencyIndex, SensitivityAnalysisResult.Status status) {
        contingencyResultsQueue.add(new SensitivityAnalysisResultRepository.ContingencyResult(contingencyIndex, status));
    }

    public boolean isWorking() {
        return !sensitivityValuesQueue.isEmpty()
            || !contingencyResultsQueue.isEmpty()
            || sensitivityValuesWorking
            || contingencyResultsWorking;
    }

    @Override
    public void close() {
        sensitivityValuesThread.interrupt();
        contingencyResultsThread.interrupt();
    }
}

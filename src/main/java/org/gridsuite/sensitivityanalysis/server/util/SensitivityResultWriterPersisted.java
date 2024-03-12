/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.util;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityResultWriter;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityFactorEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.AnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.ContingencyResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityFactorRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public class SensitivityResultWriterPersisted implements SensitivityResultWriter {

    private final AnalysisResultRepository analysisResultRepository;

    private final SensitivityFactorRepository sensitivityFactorRepository;

    private final ContingencyResultRepository contingencyResultRepository;

    private UUID resultUuid;

    public SensitivityResultWriterPersisted(AnalysisResultRepository analysisResultRepository, SensitivityFactorRepository sensitivityFactorRepository, ContingencyResultRepository contingencyResultRepository) {
        this.analysisResultRepository = analysisResultRepository;
        this.sensitivityFactorRepository = sensitivityFactorRepository;
        this.contingencyResultRepository = contingencyResultRepository;
    }

    public void init(UUID resultUuid) {
        this.resultUuid = resultUuid;
    }

    @Transactional
    @Override
    public void writeSensitivityValue(int factorIndex, int contingencyIndex, double value, double functionReference) {
        AnalysisResultEntity analysisResult = analysisResultRepository.findByResultUuid(resultUuid);
        SensitivityFactorEntity sensitivityFactor = sensitivityFactorRepository.findByAnalysisResultAndIndex(analysisResult, factorIndex);
        sensitivityFactor.getSensitivityResult().setValue(value);
        sensitivityFactor.getSensitivityResult().setFunctionReference(functionReference);
    }

    @Transactional
    @Override
    public void writeContingencyStatus(int contingencyIndex, SensitivityAnalysisResult.Status status) {
        AnalysisResultEntity analysisResult = analysisResultRepository.findByResultUuid(resultUuid);
        ContingencyResultEntity contingencyResult = contingencyResultRepository.findByAnalysisResultAndIndex(analysisResult, contingencyIndex);
        contingencyResult.setStatus(status);
    }
}

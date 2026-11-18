/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.gridsuite.sensitivityanalysis.server.repositories.AnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.ContingencyResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.GlobalStatusRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.RawSensitivityResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {
    private final AnalysisResultRepository analysisResultRepository;
    private final GlobalStatusRepository globalStatusRepository;
    private final SensitivityResultRepository sensitivityResultRepository;
    private final RawSensitivityResultRepository rawSensitivityResultRepository;
    private final ContingencyResultRepository contingencyResultRepository;

    public SupervisionService(AnalysisResultRepository analysisResultRepository,
                              GlobalStatusRepository globalStatusRepository,
                              SensitivityResultRepository sensitivityResultRepository,
                              RawSensitivityResultRepository rawSensitivityResultRepository,
                              ContingencyResultRepository contingencyResultRepository) {
        this.analysisResultRepository = analysisResultRepository;
        this.globalStatusRepository = globalStatusRepository;
        this.sensitivityResultRepository = sensitivityResultRepository;
        this.rawSensitivityResultRepository = rawSensitivityResultRepository;
        this.contingencyResultRepository = contingencyResultRepository;
    }

    public Integer getAnalysisResultsCount() {
        return (int) analysisResultRepository.count();
    }

    @Transactional
    public void deleteAllResults() {
        globalStatusRepository.deleteAll();
        sensitivityResultRepository.deleteAllPostContingencies();
        sensitivityResultRepository.deleteAll();
        rawSensitivityResultRepository.deleteAll();
        contingencyResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
    }
}

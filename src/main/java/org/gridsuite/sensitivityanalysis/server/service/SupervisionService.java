/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.gridsuite.sensitivityanalysis.server.repositories.AnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.nonevacuatedenergy.NonEvacuatedEnergyResultRepository;
import org.springframework.stereotype.Service;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {
    private final AnalysisResultRepository analysisResultRepository;
    private final NonEvacuatedEnergyResultRepository nonEvacuatedEnergyResultRepository;

    public SupervisionService(AnalysisResultRepository analysisResultRepository,
                              NonEvacuatedEnergyResultRepository nonEvacuatedEnergyResultRepository) {
        this.analysisResultRepository = analysisResultRepository;
        this.nonEvacuatedEnergyResultRepository = nonEvacuatedEnergyResultRepository;
    }

    public Integer getAnalysisResultsCount() {
        return (int) analysisResultRepository.count();
    }

    public Integer getNonEvacuatedEnergyResultsCount() {
        return (int) nonEvacuatedEnergyResultRepository.count();
    }
}

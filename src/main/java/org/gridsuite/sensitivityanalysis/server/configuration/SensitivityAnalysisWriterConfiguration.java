/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.configuration;

import org.gridsuite.sensitivityanalysis.server.repositories.AnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.ContingencyResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityFactorRepository;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityResultWriterPersisted;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Configuration
public class SensitivityAnalysisWriterConfiguration {

    // At each call of the bean a new instance will be created as it is a stateful bean (because of resultUuid)
    @Bean
    @Scope("prototype")
    public SensitivityResultWriterPersisted sensitivityResultWriterPersisted(AnalysisResultRepository analysisResultRepository,
                                                                             SensitivityFactorRepository sensitivityFactorRepository,
                                                                             ContingencyResultRepository contingencyResultRepository) {
        return new SensitivityResultWriterPersisted(analysisResultRepository, sensitivityFactorRepository, contingencyResultRepository);
    }
}

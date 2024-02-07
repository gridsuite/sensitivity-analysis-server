/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
public interface SensitivityAnalysisParametersRepository extends JpaRepository<SensitivityAnalysisParametersEntity, UUID> {
}

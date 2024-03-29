/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities.parameters;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisInputData;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "sensitivityFactorWithSensiTypeForPstEntity")
public class SensitivityFactorWithSensiTypeForPstEntity extends AbstractSensitivityFactorEntity {

    @Column(name = "sensitivityType")
    @Enumerated(EnumType.STRING)
    private SensitivityAnalysisInputData.SensitivityType sensitivityType;
}

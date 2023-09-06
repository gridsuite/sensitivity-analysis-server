/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class ContingencyEmbeddable {

    @Column
    private String contingencyId;

    @Column
    @Enumerated(EnumType.STRING)
    SensitivityAnalysisResult.Status status;
}

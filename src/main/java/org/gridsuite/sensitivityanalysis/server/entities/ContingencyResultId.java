/*
  Copyright (c) 2026, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@EqualsAndHashCode
public class ContingencyResultId implements Serializable {
    private UUID analysisResultId;
    private String contingencyId;

    public ContingencyResultId(UUID analysisResultId, String contingencyId) {
        this.analysisResultId = analysisResultId;
        this.contingencyId = contingencyId;
    }
}

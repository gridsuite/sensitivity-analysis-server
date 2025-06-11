/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@Getter
@NoArgsConstructor
@FieldNameConstants
@Entity
@Table(name = "analysis_result")
public class AnalysisResultEntity {
    @Id
    private UUID resultUuid;

    @Column(columnDefinition = "timestamptz")
    private Instant writeTimeStamp;

    public AnalysisResultEntity(UUID resultUuid, Instant writeTimeStamp) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
    }
}

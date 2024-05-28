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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@Getter
@NoArgsConstructor
@Entity
@Table(name = "analysis_result")
public class AnalysisResultEntity {
    @Id
    private UUID resultUuid;

    @Column(columnDefinition = "timestamptz")
    private OffsetDateTime writeTimeStamp;

    public AnalysisResultEntity(UUID resultUuid, OffsetDateTime writeTimeStamp) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
    }
}

/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities.nonevacuatedenergy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "non_evacuated_energy_result")
public class NonEvacuatedEnergyResultEntity implements Serializable {
    @Id
    private UUID resultUuid;

    @Column
    private LocalDateTime writeTimeStamp;

    @Column(name = "result", columnDefinition = "CLOB")
    private String result;

    public NonEvacuatedEnergyResultEntity(UUID resultUuid, LocalDateTime writeTimeStamp, String result) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
        this.result = result;
    }
}



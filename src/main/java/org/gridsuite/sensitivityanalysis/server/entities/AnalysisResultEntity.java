/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
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

    @Column
    private LocalDateTime writeTimeStamp;

    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SensitivityEntity> sensitivities;

    public AnalysisResultEntity(UUID resultUuid, LocalDateTime writeTimeStamp, List<SensitivityEntity> sensitivities) {
        this.resultUuid = resultUuid;
        this.writeTimeStamp = writeTimeStamp;
        addSensitivities(sensitivities);
    }

    private void addSensitivities(List<SensitivityEntity> sensitivities) {
        if (sensitivities != null) {
            sensitivities.forEach(s -> s.setResult(this));
            this.sensitivities = sensitivities;
        }
    }
}

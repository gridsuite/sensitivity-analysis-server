/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "sensitivity")
public class SensitivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @NotNull
    private UUID id;

    @Column
    private int factorIndex;

    @Column
    private int contingencyIndex;

    @Column(name = "value_")
    private double value;

    @Column
    private double functionReference;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Setter
    private AnalysisResultEntity result;

    public SensitivityEntity(int factorIndex, int contingencyIndex, double value, double functionReference) {
        this.id = UUID.randomUUID();
        this.factorIndex = factorIndex;
        this.contingencyIndex = contingencyIndex;
        this.value = value;
        this.functionReference = functionReference;
    }
}

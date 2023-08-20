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
    private UUID sensitivityId;

    @Embedded
    private SensitivityFactorEmbeddable factor;

    @Embedded
    private ContingencyEmbeddable contingency;

    @Column(name = "value_")
    private Double value;

    @Column
    private Double functionReference;

    @Column
    private Double valueAfter;

    @Column
    private Double functionReferenceAfter;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Setter
    private AnalysisResultEntity result;

    public SensitivityEntity(SensitivityFactorEmbeddable factor, ContingencyEmbeddable contingency, Double value, Double functionReference, Double valueAfter, Double functionReferenceAfter) {
        this.sensitivityId = UUID.randomUUID();
        this.factor = factor;
        this.contingency = contingency;
        this.value = value;
        this.functionReference = functionReference;
        this.valueAfter = valueAfter;
        this.functionReferenceAfter = functionReferenceAfter;
    }
}

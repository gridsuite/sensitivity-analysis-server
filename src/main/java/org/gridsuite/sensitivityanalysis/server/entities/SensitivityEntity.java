/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "sensitivity", indexes = @Index(
        name = "sensitivity_result_uuid_function_variable_contingency_idx",
        columnList = "sensitivityId, result_result_uuid, functionId, functionType, variableId, contingencyId"))
public class SensitivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID sensitivityId;

    @Embedded
    private SensitivityFactorEmbeddable factor;

    @Embedded
    private ContingencyEmbeddable contingency;

    @Column(name = "value_")
    private double value;

    @Column
    private double functionReference;

    @Column
    private double valueAfter;

    @Column
    private double functionReferenceAfter;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Setter
    private AnalysisResultEntity result;

    public SensitivityEntity(SensitivityFactorEmbeddable factor,
                             ContingencyEmbeddable contingency,
                             double value,
                             double functionReference) {
        this.sensitivityId = UUID.randomUUID();
        this.factor = factor;
        this.contingency = contingency;
        this.value = value;
        this.functionReference = functionReference;

    }

    public SensitivityEntity(SensitivityFactorEmbeddable factor,
                             ContingencyEmbeddable contingency,
                             double value,
                             double functionReference,
                             double valueAfter,
                             double functionReferenceAfter) {
        this(factor, contingency, value, functionReference);
        this.valueAfter = valueAfter;
        this.functionReferenceAfter = functionReferenceAfter;
    }
}

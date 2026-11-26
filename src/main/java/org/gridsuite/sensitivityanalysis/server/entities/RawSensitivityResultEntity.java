/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@IdClass(SensitivityResultId.class)
@Entity
@FieldNameConstants
@Table(
    name = "raw_sensitivity_result",
    indexes = {
        @Index(name = "raw_sensitivity_result_analysis_result", columnList = "analysis_result_id"),
        @Index(name = "raw_sensitivity_result_analysis_result_factor_index", columnList = "analysis_result_id, factor_index")
    })
public class RawSensitivityResultEntity extends AbstractManuallyAssignedIdentifierEntity<SensitivityResultId> {

    @Id
    @Column(name = "analysis_result_id")
    private UUID analysisResultId;

    @Id
    @Column(name = "factor_index")
    private int factorIndex;

    @Column(name = "value_", nullable = false)
    private double value;

    @Column(name = "function_reference", nullable = false)
    private double functionReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", insertable = false, updatable = false)
    private AnalysisResultEntity analysisResult;

    public RawSensitivityResultEntity(int factorIndex,
                                      double value,
                                      double functionReference,
                                      AnalysisResultEntity analysisResult) {
        this.analysisResultId = analysisResult.getResultUuid();
        this.factorIndex = factorIndex;
        this.value = value;
        this.functionReference = functionReference;
        this.analysisResult = analysisResult;
    }

    @Override
    public SensitivityResultId getId() {
        return new SensitivityResultId(analysisResultId, factorIndex);
    }
}

/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
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
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "raw_sensitivity_result", indexes = {@Index(name = "raw_sensitivity_result_analysis_result_factor_index", columnList = "analysis_result_id, factor_index")})
public class RawSensitivityResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id")
    private AnalysisResultEntity analysisResult;

    @Column(name = "factor_index")
    private int index;

    @Column(name = "value_")
    private double value;

    @Column(name = "function_reference")
    private double functionReference;

    public RawSensitivityResultEntity(AnalysisResultEntity analysisResult, int index, double value, double functionReference) {
        this.analysisResult = analysisResult;
        this.index = index;
        this.value = value;
        this.functionReference = functionReference;
    }
}

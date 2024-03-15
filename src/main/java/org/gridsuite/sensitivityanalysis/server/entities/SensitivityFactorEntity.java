/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "sensitivity_factor",
    indexes = {@Index(name = "sensitivity_factor_search_index", columnList = "function_id, function_type, variable_id, variable_type")}
)
public class SensitivityFactorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne
    @JoinColumns(value = {
        @JoinColumn(name = "analysis_result_id", referencedColumnName = "analysis_result_id", updatable = false, insertable = false),
        @JoinColumn(name = "index", referencedColumnName = "factor_index", updatable = false, insertable = false)
    })
    private RawSensitivityResultEntity rawSensitivityResult;

    @Column(name = "index", nullable = false)
    private int index;

    @Column(name = "function_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SensitivityFunctionType functionType;

    @Column(name = "function_id", nullable = false)
    private String functionId;

    @Column(name = "variable_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SensitivityVariableType variableType;

    @Column(name = "variable_id", nullable = false)
    private String variableId;

    @Column(name = "variable_set", nullable = false)
    private boolean variableSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id")
    private AnalysisResultEntity analysisResult;

    @OneToOne(mappedBy = "factor")
    private SensitivityResultEntity sensitivityResult;

    public SensitivityFactorEntity(int index, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId, boolean variableSet, AnalysisResultEntity analysisResult) {
        this.index = index;
        this.functionType = functionType;
        this.functionId = functionId;
        this.variableType = variableType;
        this.variableId = variableId;
        this.variableSet = variableSet;
        this.analysisResult = analysisResult;
    }
}

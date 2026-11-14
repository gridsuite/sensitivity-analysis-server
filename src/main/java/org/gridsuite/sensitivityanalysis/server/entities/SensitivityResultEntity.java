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
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Getter
@Setter
@FieldNameConstants
@NoArgsConstructor
@IdClass(SensitivityResultId.class)
@Entity
@Table(
    name = "sensitivity_result",
    indexes = {
        @Index(name = "sensitivity_result_analysis_result_idx", columnList = "analysis_result_id"),
        @Index(name = "sensitivity_result_analysis_result_factor_index_idx", columnList = "analysis_result_id, factor_index"),
        @Index(name = "sensitivity_result_analysis_result_factor_index_search_idx", columnList = "analysis_result_id, factor_index, function_type, variable_type, function_id, variable_id"),
        // Greatly helps during deletion as it references itself as a foreign key
        @Index(name = "sensitivity_result_pre_contingency_sensitivity_result_id_idx", columnList = "pre_contingency_analysis_result_id, pre_contingency_factor_index"),
        // Greatly helps during contingency results deletion as it references a foreign key
        @Index(name = "sensitivity_result_contingency_result_id_idx", columnList = "contingency_id")
    }
)
public class SensitivityResultEntity extends AbstractManuallyAssignedIdentifierEntity<SensitivityResultId> {

    @Id
    @Column(name = "analysis_result_id")
    private UUID analysisResultId;

    @Id
    @Column(name = "factor_index")
    private int factorIndex;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", insertable = false, updatable = false)
    private AnalysisResultEntity analysisResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contingency_id")
    private ContingencyResultEntity contingencyResult;

    // these foreign keys fields declared explicitly to use to avoid N +1 select when insert
    @Column(name = "pre_contingency_analysis_result_id")
    private UUID preContingencyAnalysisResultId;

    @Column(name = "pre_contingency_factor_index")
    private Integer preContingencyFactorIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(
        value = {
            @JoinColumn(name = "pre_contingency_analysis_result_id",
                    referencedColumnName = "analysis_result_id",
                    insertable = false, updatable = false),
            @JoinColumn(name = "pre_contingency_factor_index",
                    referencedColumnName = "factor_index",
                    insertable = false, updatable = false)
        },
        foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT) // to avoid N +1 when insert
    )
    private SensitivityResultEntity preContingencySensitivityResult;

    @OneToOne
    @JoinColumns(
        value = {
            @JoinColumn(name = "analysis_result_id", referencedColumnName = "analysis_result_id", updatable = false, insertable = false),
            @JoinColumn(name = "factor_index", referencedColumnName = "factor_index", updatable = false, insertable = false)
        },
        foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT)
    )
    private RawSensitivityResultEntity rawSensitivityResult;

    public SensitivityResultEntity(int factorIndex,
                                   SensitivityFunctionType functionType,
                                   String functionId,
                                   SensitivityVariableType variableType,
                                   String variableId,
                                   boolean variableSet,
                                   AnalysisResultEntity analysisResult,
                                   ContingencyResultEntity contingencyResult,
                                   SensitivityResultEntity preContingencySensitivityResult) {
        this.analysisResultId = analysisResult.getResultUuid();
        this.factorIndex = factorIndex;
        this.functionType = functionType;
        this.functionId = functionId;
        this.variableType = variableType;
        this.variableId = variableId;
        this.variableSet = variableSet;
        this.analysisResult = analysisResult;
        this.contingencyResult = contingencyResult;
        // to avoid N +1 select when insert, write direct fks values when dto => entity
        if (preContingencySensitivityResult != null) {
            this.preContingencyAnalysisResultId = preContingencySensitivityResult.getAnalysisResultId();
            this.preContingencyFactorIndex = preContingencySensitivityResult.getFactorIndex();
        }
    }

    @Override
    public SensitivityResultId getId() {
        return new SensitivityResultId(analysisResultId, factorIndex);
    }
}

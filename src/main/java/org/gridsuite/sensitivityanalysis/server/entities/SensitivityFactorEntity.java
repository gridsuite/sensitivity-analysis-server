package org.gridsuite.sensitivityanalysis.server.entities;

import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "sensitivity_factor",
    indexes = {@Index(name = "unique_factor_index_analysis", columnList = "index, analysis_result_id", unique = true)}
)
public class SensitivityFactorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id")
    private AnalysisResultEntity analysisResult;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "factor")
    private List<SensitivityResultEntity> sensitivityResultEntities;

    public SensitivityFactorEntity(int index, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId, AnalysisResultEntity analysisResult) {
        this(index, functionType, functionId, variableType, variableId, false, analysisResult);
    }

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

package org.gridsuite.sensitivityanalysis.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sensitivity_result")
public class SensitivityResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID sensitivityId;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id")
    private AnalysisResultEntity result;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factor_id")
    private SensitivityFactorEntity factor;

    @Column(name = "value")
    private double value;

    @Column(name = "function_reference")
    private double functionReference;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "contingency_id")
    private ContingencyResultEntity contingencyResult;

    public SensitivityResultEntity(AnalysisResultEntity result, SensitivityFactorEntity factor, double value, double functionReference) {
        this.result = result;
        this.factor = factor;
        this.value = value;
        this.functionReference = functionReference;
    }

    public SensitivityResultEntity(AnalysisResultEntity result, SensitivityFactorEntity factor, double value, double functionReference, ContingencyResultEntity contingencyResult) {
        this.result = result;
        this.factor = factor;
        this.value = value;
        this.functionReference = functionReference;
        this.contingencyResult = contingencyResult;
    }
}

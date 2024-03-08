package org.gridsuite.sensitivityanalysis.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id")
    private AnalysisResultEntity result;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factor_id")
    private SensitivityFactorEntity factor;

    @Column(name = "value")
    private double value;

    @Column(name = "function_reference")
    private double functionReference;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "contingency_id")
    private ContingencyResultEntity contingencyResult;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "pre_contingency_sensitivity_result_id")
    private SensitivityResultEntity preContingencySensitivityResult;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "preContingencySensitivityResult")
    private Set<SensitivityResultEntity> postContingencySensitivityResults;

    public SensitivityResultEntity(AnalysisResultEntity result, SensitivityFactorEntity factor, ContingencyResultEntity contingencyResult, SensitivityResultEntity preContingencySensitivityResult) {
        this.result = result;
        this.factor = factor;
        this.contingencyResult = contingencyResult;
        this.preContingencySensitivityResult = preContingencySensitivityResult;
    }
}

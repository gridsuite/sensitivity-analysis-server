package org.gridsuite.sensitivityanalysis.server.entities;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
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
    name = "contingency_result",
    indexes = {
        @Index(name = "unique_contingency_analysis", columnList = "contingency_id, analysis_result_id", unique = true),
        @Index(name = "unique_contingency_index_analysis", columnList = "index, analysis_result_id", unique = true)
    }
)
public class ContingencyResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "index", nullable = false)
    private int index;

    @Column(name = "contingency_id", nullable = false, updatable = false)
    private String contingencyId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    SensitivityAnalysisResult.Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id")
    private AnalysisResultEntity analysisResult;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "contingencyResult")
    private List<SensitivityResultEntity> sensitivityResults;

    public ContingencyResultEntity(int index, String contingencyId, AnalysisResultEntity analysisResult) {
        this.index = index;
        this.contingencyId = contingencyId;
        this.analysisResult = analysisResult;
    }

    public ContingencyResultEntity(int index, String contingencyId, SensitivityAnalysisResult.Status status) {
        this.index = index;
        this.contingencyId = contingencyId;
        this.status = status;
    }
}

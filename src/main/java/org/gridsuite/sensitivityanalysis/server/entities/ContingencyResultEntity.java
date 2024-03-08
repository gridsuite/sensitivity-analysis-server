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
    name = "contingency_result"
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

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "contingencyResult")
    private List<SensitivityResultEntity> sensitivityResults;

    public ContingencyResultEntity(int index, String contingencyId) {
        this.index = index;
        this.contingencyId = contingencyId;
    }

    public ContingencyResultEntity(int index, String contingencyId, SensitivityAnalysisResult.Status status) {
        this.index = index;
        this.contingencyId = contingencyId;
        this.status = status;
    }
}

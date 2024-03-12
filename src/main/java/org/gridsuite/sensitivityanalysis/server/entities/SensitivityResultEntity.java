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

import java.util.Set;
import java.util.UUID;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "sensitivity_result",
    indexes = {
        @Index(name = "sensitivity_result_analysis_result_idx", columnList = "result_id"),
        // Greatly helps during deletion as it references itself as a foreign key
        @Index(name = "sensitivity_result_pre_contingency_sensitivity_result_id_index", columnList = "pre_contingency_sensitivity_result_id")
    }
)
public class SensitivityResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID sensitivityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private AnalysisResultEntity result;

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "factor_id")
    private SensitivityFactorEntity factor;

    @Column(name = "value")
    private double value;

    @Column(name = "function_reference")
    private double functionReference;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
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

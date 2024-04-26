/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
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
    name = "contingency_result",
    indexes = {
        @Index(name = "contingency_result_analysis_result_idx", columnList = "analysis_result_id"),
        @Index(name = "unique_contingency_analysis", columnList = "analysis_result_id, contingency_id", unique = true),
        @Index(name = "unique_contingency_index_analysis", columnList = "analysis_result_id, index", unique = true)
    })
public class ContingencyResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "index", nullable = false)
    private int index;

    @Column(name = "contingency_id", nullable = false)
    private String contingencyId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    SensitivityAnalysisResult.Status status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id")
    private AnalysisResultEntity analysisResult;

    public ContingencyResultEntity(int index, String contingencyId, AnalysisResultEntity analysisResult) {
        this.index = index;
        this.contingencyId = contingencyId;
        this.analysisResult = analysisResult;
    }
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities;

import java.util.List;
import java.util.UUID;

import java.time.LocalDateTime;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "analysis_result")
public class AnalysisResultEntity {
    @Id
    private UUID resultUuid;

    @Column
    private LocalDateTime writeTimeStamp;

    @ElementCollection
    @CollectionTable (name = "factor")
    @OrderColumn
    private List<SensitivityFactorEmbeddable> factors;

    @ElementCollection
    @CollectionTable(name = "contingency")
    @OrderColumn
    private List<ContingencyEmbeddable> contingencies = new java.util.ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "sensitivity")
    private List<SensitivityEmbeddable> sensitivities;
}

/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities.parameters;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractSensitivityFactorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    // liquidbase add foreignKey everytime, you need to delete it
    @ElementCollection
    @CollectionTable(
            name = "monitoredBranch",
            joinColumns = @JoinColumn(name = "SensitivityFactorId")
    )
    private List<EquipmentsContainerEmbeddable> monitoredBranch;

    // liquidbase add foreignKey everytime, you need to delete it
    @ElementCollection
    @CollectionTable(
            name = "injections",
            joinColumns = @JoinColumn(name = "SensitivityFactorId")
    )
    private List<EquipmentsContainerEmbeddable> injections;

    // liquidbase add foreignKey everytime, you need to delete it
    @ElementCollection
    @CollectionTable(
            name = "contingencies",
            joinColumns = @JoinColumn(name = "SensitivityFactorId")
    )
    private List<EquipmentsContainerEmbeddable> contingencies;

    @Column(name = "activated", columnDefinition = "boolean default true")
    private boolean activated;
}

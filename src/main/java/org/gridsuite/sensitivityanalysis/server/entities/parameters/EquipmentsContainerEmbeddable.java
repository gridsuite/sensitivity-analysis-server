/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities.parameters;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.sensitivityanalysis.server.dto.EquipmentsContainer;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class EquipmentsContainerEmbeddable {

    @Column(name = "containerId")
    private UUID containerId;

    @Column(name = "containerName")
    private String containerName;

    public static List<EquipmentsContainerEmbeddable> toEmbeddableContainerEquipments(List<EquipmentsContainer> containers) {
        return containers == null ? null :
                containers.stream()
                        .map(container -> new EquipmentsContainerEmbeddable(container.getContainerId(), container.getContainerName()))
                        .collect(Collectors.toList());
    }

    public static List<EquipmentsContainer> fromEmbeddableContainerEquipments(List<EquipmentsContainerEmbeddable> containers) {
        return containers == null ? null :
                containers.stream()
                        .map(container -> new EquipmentsContainer(container.getContainerId(), container.getContainerName()))
                        .collect(Collectors.toList());
    }
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.GlobalStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public interface GlobalStatusRepository extends JpaRepository<GlobalStatusEntity, UUID> {
    @Modifying
    @Override
    @Query(value = "DELETE FROM GlobalStatusEntity")
    void deleteAll();

    GlobalStatusEntity findByResultUuid(UUID resultUuid);

    void deleteByResultUuid(UUID resultUuid);
}

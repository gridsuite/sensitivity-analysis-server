package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SensitivityResultRepository extends JpaRepository<SensitivityResultEntity, UUID> {
}

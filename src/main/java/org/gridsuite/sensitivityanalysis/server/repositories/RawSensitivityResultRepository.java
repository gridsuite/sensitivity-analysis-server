package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.RawSensitivityResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface RawSensitivityResultRepository extends JpaRepository<RawSensitivityResultEntity, UUID> {

    @Modifying
    @Query(value = "DELETE FROM RawSensitivityResultEntity f WHERE f.analysisResult.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Query(value = "DELETE FROM RawSensitivityResultEntity")
    void deleteAll();
}

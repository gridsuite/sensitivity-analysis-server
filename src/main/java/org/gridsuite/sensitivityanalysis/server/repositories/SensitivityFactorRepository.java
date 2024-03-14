package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityFactorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface SensitivityFactorRepository extends JpaRepository<SensitivityFactorEntity, UUID> {

    @Modifying
    @Query(value = "DELETE FROM SensitivityFactorEntity f WHERE f.analysisResult.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Query(value = "DELETE FROM SensitivityFactorEntity")
    void deleteAll();

    SensitivityFactorEntity findByAnalysisResultAndIndex(AnalysisResultEntity analysisResult, int index);
}

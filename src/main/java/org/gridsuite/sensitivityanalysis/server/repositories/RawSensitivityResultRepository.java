package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.RawSensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface RawSensitivityResultRepository extends JpaRepository<RawSensitivityResultEntity, SensitivityResultId> {

    @Modifying
    @Query(value = """
        INSERT INTO raw_sensitivity_result(id, analysis_result_id, sensitivity_result_id, factor_index, value_, function_reference)
        VALUES (:id, :analysisResultId, :sensitivityResultId, :factorIndex, :value, :functionReference)
        """, nativeQuery = true)
    void insert(UUID id, UUID analysisResultId, UUID sensitivityResultId, int factorIndex, double value, double functionReference);

    RawSensitivityResultEntity findByAnalysisResultAndIndex(AnalysisResultEntity analysisResult, int index);

    @Modifying
    @Query(value = "DELETE FROM RawSensitivityResultEntity f WHERE f.analysisResult.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Query(value = "DELETE FROM RawSensitivityResultEntity")
    void deleteAll();
}

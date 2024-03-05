package org.gridsuite.sensitivityanalysis.server.repositories;

import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ContingencyResultRepository extends JpaRepository<ContingencyResultEntity, UUID> {

    @Modifying
    @Query(value = "DELETE FROM ContingencyResultEntity f WHERE f.analysisResult.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    ContingencyResultEntity findByAnalysisResultAndIndex(AnalysisResultEntity analysisResult, int index);
}

package org.gridsuite.sensitivityanalysis.server.util;

import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityResultRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparing;

@Repository
public class TestRepository {

    private final SensitivityResultRepository sensitivityResultRepository;

    public TestRepository(SensitivityResultRepository sensitivityResultRepository) {
        this.sensitivityResultRepository = sensitivityResultRepository;
    }

    @Transactional
    public List<? extends SensitivityOfTo> createSortedSensitivityList() {
        //contingency.id comparator
        Comparator<ContingencyResultEntity> comparatorByContingencyId = comparing(ContingencyResultEntity::getContingencyId, Comparator.comparing(String::toString));
        //sensitivityId comparator (the toString is needed because UUID comparator is not the same as the string one)
        Comparator<SensitivityResultEntity> comparatorBySensiId = comparing(s -> s.getSensitivityId().toString());
        //contingency.id and resultUuid (in that order) comparator
        Comparator<SensitivityResultEntity> comparatorByContingencyIdAndSensiId = comparing(SensitivityResultEntity::getContingencyResult, comparatorByContingencyId).thenComparing(comparatorBySensiId);
        return sensitivityResultRepository.findAll().stream()
            .filter(s -> s.getContingencyResult() != null)
            .sorted(comparatorByContingencyIdAndSensiId)
            .map(sensitivityEntity ->
                (SensitivityWithContingency) SensitivityWithContingency.builder().funcId(sensitivityEntity.getFactor().getFunctionId())
                    .contingencyId(sensitivityEntity.getContingencyResult().getContingencyId())
                    .varId(sensitivityEntity.getFactor().getVariableId())
                    .varIsAFilter(sensitivityEntity.getFactor().isVariableSet())
                    .value(sensitivityEntity.getValue())
                    .functionReference(sensitivityEntity.getFunctionReference())
                    .build())
            .toList();
    }
}

package org.gridsuite.sensitivityanalysis.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.utils.specification.AbstractCommonSpecificationBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// preContingency
@Service
public class SensitivityResultSpecificationBuilder extends AbstractCommonSpecificationBuilder<SensitivityResultEntity> {

    @Override
    public boolean isNotParentFilter(ResourceFilterDTO filter) {
        return List.of(SensitivityResultEntity.Fields.id, SensitivityResultEntity.Fields.rawSensitivityResult)
                .contains(filter.column());
    }

    @Override
    public String getIdFieldName() {
        return SensitivityResultEntity.Fields.id;
    }

    @Override
    public Path<UUID> getResultIdPath(Root<SensitivityResultEntity> root) {
        return root.get(SensitivityResultEntity.Fields.analysisResult).get(AnalysisResultEntity.Fields.resultUuid);
    }

    @Override
    public Specification<SensitivityResultEntity> addSpecificFilterWhenNoChildrenFilter() {
        Specification<SensitivityResultEntity> spec = (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.isNull(root.get(SensitivityResultEntity.Fields.contingencyResult))
        );
        return spec.and(Specification.not(nullRawValue()));
    }

    public static Specification<SensitivityResultEntity> nullRawValue() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.isNull(root.get("rawSensitivityResult").get("value"))
        );
    }

    @Override
    public Specification<SensitivityResultEntity> addSpecificFilterWhenChildrenFilters() {
        return addSpecificFilterWhenNoChildrenFilter();
    }
}

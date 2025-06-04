package org.gridsuite.sensitivityanalysis.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.specification.AbstractCommonSpecificationBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.RawSensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Mathieu Deharbe <mathieu.deharbe_externe at rte-france.com>
 *     preContingency (N)
 */
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
        return Specification.not(nullRawValue())
                .and(nullContingency());
    }

    @Override
    public Specification<SensitivityResultEntity> addSpecificFilterWhenChildrenFilters() {
        return addSpecificFilterWhenNoChildrenFilter();
    }

    public static Specification<SensitivityResultEntity> nullRawValue() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.isNull(
                        root.get(SensitivityResultEntity.Fields.rawSensitivityResult).get(RawSensitivityResultEntity.Fields.value)
                )
        );
    }

    public Specification<SensitivityResultEntity> nullContingency() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.isNull(root.get(SensitivityResultEntity.Fields.contingencyResult))
        );
    }

    public static Specification<SensitivityResultEntity> fieldIn(Collection<?> collection,
                                                                 String fieldName,
                                                                 String subFieldName) {
        return (root, query, criteriaBuilder) -> {
            if (!CollectionUtils.isEmpty(collection)) {
                var field = subFieldName == null ? root.get(fieldName) : root.get(fieldName).get(subFieldName);
                return collection.stream()
                        .map(id -> criteriaBuilder.equal(field, id))
                        .reduce(criteriaBuilder.or(), criteriaBuilder::or);
            }
            return criteriaBuilder.and(); // Always True
        };
    }
}

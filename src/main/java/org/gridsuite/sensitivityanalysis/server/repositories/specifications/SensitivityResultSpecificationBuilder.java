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

import java.util.List;
import java.util.UUID;

import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultSpecification.nullContingency;

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

    public static Specification<SensitivityResultEntity> nullRawValue() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.isNull(
                        root.get(SensitivityResultEntity.Fields.rawSensitivityResult).get(RawSensitivityResultEntity.Fields.value)
                )
        );
    }

    @Override
    public Specification<SensitivityResultEntity> addSpecificFilterWhenChildrenFilters() {
        return addSpecificFilterWhenNoChildrenFilter();
    }
}

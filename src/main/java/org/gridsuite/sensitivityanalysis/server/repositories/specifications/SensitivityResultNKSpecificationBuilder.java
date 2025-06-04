package org.gridsuite.sensitivityanalysis.server.repositories.specifications;

import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * @author Mathieu Deharbe <mathieu.deharbe_externe at rte-france.com>
 *     preContingency (N)
 */
@Service
public class SensitivityResultNKSpecificationBuilder extends SensitivityResultSpecificationBuilder {
    @Override
    public Specification<SensitivityResultEntity> addSpecificFilterWhenNoChildrenFilter() {
        return Specification.not(nullRawValue())
                .and(Specification.not(nullContingency()));
    }
}

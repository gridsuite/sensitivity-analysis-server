package org.gridsuite.sensitivityanalysis.server.repositories.specifications;

import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import static org.gridsuite.sensitivityanalysis.server.util.SensitivityResultSpecification.nullContingency;

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

package org.gridsuite.sensitivityanalysis.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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

    @Override
    public Specification<SensitivityResultEntity> buildSpecificationFromSelector(UUID resultUuid, List<ResourceFilterDTO> resourceFilters, ResultsSelector selector) {
        return super.buildSpecificationFromSelector(resultUuid, resourceFilters, selector)
                .and(fieldIn(
                        selector.getContingencyIds(),
                        SensitivityResultEntity.Fields.contingencyResult,
                        ContingencyResultEntity.Fields.contingencyId)
                );
    }
}

/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
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
 * Builds specifications for querying {@link SensitivityResultEntity} objects.
 * This builder focuses on constructing criteria for sensitivity analysis results including the contingency handling, i.e. tab N_K
 *
 * @author Mathieu Deharbe <mathieu.deharbe_externe at rte-france.com>
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

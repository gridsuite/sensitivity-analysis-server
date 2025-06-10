/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.specification.AbstractCommonSpecificationBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
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
 * Builds specifications for querying {@link SensitivityResultEntity} objects.
 *
 * This builder focuses on constructing criteria for sensitivity analysis results without the contingency handling, i.e. tab N
 *
 * @author Mathieu Deharbe <mathieu.deharbe_externe at rte-france.com>
 * 
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

    public Specification<SensitivityResultEntity> buildSpecificationFromSelector(UUID resultUuid, List<ResourceFilterDTO> resourceFilters, ResultsSelector selector) {
        return buildSpecification(resultUuid, resourceFilters, false)
                .and(fieldIn(
                        List.of(selector.getFunctionType()),
                        ResultsSelector.Fields.functionType,
                        null))
                .and(fieldIn(
                        selector.getFunctionIds(),
                        SensitivityResultEntity.Fields.functionId,
                        null))
                .and(fieldIn(
                        selector.getVariableIds(),
                        SensitivityResultEntity.Fields.variableId,
                        null));
    }

    public Specification<SensitivityResultEntity> nullRawValue() {
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

    public Specification<SensitivityResultEntity> fieldIn(Collection<?> collection,
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

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
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.RawSensitivityResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds specifications for querying {@link SensitivityResultEntity} objects.
 * This builder focuses on constructing criteria for sensitivity analysis results without the contingency handling, i.e. tab N
 *
 * @author Mathieu Deharbe <mathieu.deharbe_externe at rte-france.com>
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
        Specification<SensitivityResultEntity> baseSpec = buildSpecification(resultUuid, Collections.emptyList(), false);
        Specification<SensitivityResultEntity> resourceFilterSpec = buildCustomResourceFilterSpecification(resourceFilters);
        Specification<SensitivityResultEntity> finalSpec = baseSpec;
        if (resourceFilterSpec != null) {
            finalSpec = finalSpec.and(resourceFilterSpec);
        }

        return finalSpec
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

    private Specification<SensitivityResultEntity> buildCustomResourceFilterSpecification(List<ResourceFilterDTO> resourceFilters) {
        if (resourceFilters == null || resourceFilters.isEmpty()) {
            return Specification.where(null);
        }
        Map<String, List<ResourceFilterDTO>> filtersByColumn = resourceFilters.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(ResourceFilterDTO::column));
        Specification<SensitivityResultEntity> finalSpec = null;
        for (List<ResourceFilterDTO> filtersForColumn : filtersByColumn.values()) {
            Specification<SensitivityResultEntity> columnSpec = buildOrSpecificationForColumn(filtersForColumn);

            if (columnSpec != null) {
                finalSpec = finalSpec == null ? columnSpec : finalSpec.and(columnSpec);
            }
        }
        return finalSpec;
    }

    private Specification<SensitivityResultEntity> buildOrSpecificationForColumn(List<ResourceFilterDTO> filters) {
        if (filters.isEmpty()) {
            return null;
        }
        Specification<SensitivityResultEntity> columnSpec = null;
        for (ResourceFilterDTO filter : filters) {
            Specification<SensitivityResultEntity> singleFilterSpec = createSingleFilterSpecification(filter);
            columnSpec = columnSpec == null ? singleFilterSpec : columnSpec.or(singleFilterSpec);
        }
        return columnSpec;
    }

    private Specification<SensitivityResultEntity> createSingleFilterSpecification(ResourceFilterDTO filter) {
        return (root, query, criteriaBuilder) -> {
            if (filter.value() instanceof List<?> valueList && !valueList.isEmpty()) {
                Path<Object> fieldPath = getFieldPathForFilter(root, filter.column());

                switch (filter.type()) {
                    case IN:
                        return fieldPath.in(valueList);
                    case EQUALS:
                        if (valueList.size() == 1) {
                            return criteriaBuilder.equal(fieldPath, valueList.getFirst());
                        }
                        return fieldPath.in(valueList);
                    default:
                        return criteriaBuilder.and();
                }
            }
            return criteriaBuilder.and();
        };
    }

    private Path<Object> getFieldPathForFilter(Root<SensitivityResultEntity> root, String column) {
        return switch (column) {
            case "functionId" -> root.get(SensitivityResultEntity.Fields.functionId);
            case "variableId" -> root.get(SensitivityResultEntity.Fields.variableId);
            case "contingencyId" -> root.get(SensitivityResultEntity.Fields.contingencyResult)
                    .get(ContingencyResultEntity.Fields.contingencyId);
            default -> root.get(column);
        };
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

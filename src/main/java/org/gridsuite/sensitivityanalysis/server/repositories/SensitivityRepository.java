/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.sensitivity.SensitivityFunctionType;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.util.CollectionUtils;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

public interface SensitivityRepository extends JpaRepository<SensitivityEntity, UUID> {

    String FACTOR = "factor";
    String CONTINGENCY = "contingency";

    int countByResultAndFactorFunctionTypeAndContingencyIsNull(AnalysisResultEntity result, SensitivityFunctionType functionType);

    int countByResultAndFactorFunctionTypeAndContingencyIsNotNull(AnalysisResultEntity result, SensitivityFunctionType functionType);

    Page<SensitivityEntity> findAll(Specification<SensitivityEntity> specification, Pageable pageable);

    static Specification<SensitivityEntity> getSpecification(AnalysisResultEntity sas,
                                                             SensitivityFunctionType functionType,
                                                             Collection<String> functionIds,
                                                             Collection<String> variableIds,
                                                             Collection<String> contingencyIds,
                                                             boolean withContingency) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addPredicate(criteriaBuilder, root, predicates, List.of(sas.getResultUuid()), "result", "resultUuid");
            addPredicate(criteriaBuilder, root, predicates, List.of(functionType), FACTOR, "functionType");

            predicates.add(withContingency ? criteriaBuilder.isNotNull(root.get(CONTINGENCY)) : criteriaBuilder.isNull(root.get(CONTINGENCY)));

            addPredicate(criteriaBuilder, root, predicates, functionIds, FACTOR, "functionId");
            addPredicate(criteriaBuilder, root, predicates, variableIds, FACTOR, "variableId");
            addPredicate(criteriaBuilder, root, predicates, contingencyIds, CONTINGENCY, "contingencyId");

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addPredicate(CriteriaBuilder criteriaBuilder,
                                     Root<SensitivityEntity> root,
                                     List<Predicate> predicates,
                                     Collection<?> collection,
                                     String fieldName,
                                     String subFieldName) {
        if (!CollectionUtils.isEmpty(collection)) {
            Expression<?> expression = subFieldName == null ? root.get(fieldName) : root.get(fieldName).get(subFieldName);
            var predicate = collection.stream()
                    .map(id -> criteriaBuilder.equal(expression, id))
                    .toArray(Predicate[]::new);
            predicates.add(criteriaBuilder.or(predicate));
        }
    }
}

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

            predicates.add(criteriaBuilder.equal(root.get("result").get("resultUuid"), sas.getResultUuid()));
            predicates.add(withContingency ? criteriaBuilder.isNull(root.get("contingency")) : criteriaBuilder.isNotNull(root.get("contingency")));
            predicates.add(criteriaBuilder.equal(root.get("factor").get("functionType"), functionType));

            addPredicate(criteriaBuilder, root, predicates, functionIds, "factor", "functionId");
            addPredicate(criteriaBuilder, root, predicates, variableIds, "factor", "variableId");
            addPredicate(criteriaBuilder, root, predicates, contingencyIds, "contingency", "id");

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addPredicate(CriteriaBuilder criteriaBuilder,
                                     Root<SensitivityEntity> root,
                                     List<Predicate> predicates,
                                     Collection<String> ids,
                                     String fieldName,
                                     String subFieldName) {
        if (ids != null && !ids.isEmpty()) {
            Expression<?> expression = subFieldName == null ? root.get(fieldName) : root.get(fieldName).get(subFieldName);
            var predicate = ids.stream()
                    .map(id -> criteriaBuilder.equal(expression, id))
                    .toArray(Predicate[]::new);
            predicates.add(criteriaBuilder.or(predicate));
        }
    }
}

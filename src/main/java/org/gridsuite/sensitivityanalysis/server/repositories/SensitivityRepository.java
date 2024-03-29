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
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.CollectionUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

public interface SensitivityRepository extends JpaRepository<SensitivityEntity, UUID>, JpaSpecificationExecutor<SensitivityEntity> {

    String FACTOR = "factor";
    String CONTINGENCY = "contingency";

    Page<SensitivityEntity> findAll(Specification<SensitivityEntity> specification, Pageable pageable);

    @Query(value = "SELECT distinct s.factor.functionId from SensitivityEntity as s " +
            "where s.result.resultUuid = :resultUuid " +
            "and s.factor.functionType = :sensitivityFunctionType " +
            "and ((:withContingency = true and s.contingency is not null ) or (:withContingency = false and s.contingency is null )) " +
            "order by s.factor.functionId")
    List<String> getDistinctFunctionIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType, boolean withContingency);

    @Query(value = "SELECT distinct s.factor.variableId from SensitivityEntity as s " +
            "where s.result.resultUuid = :resultUuid " +
            "and s.factor.functionType = :sensitivityFunctionType " +
            "and ((:withContingency = true and s.contingency is not null ) or (:withContingency = false and s.contingency is null )) " +
            "order by s.factor.variableId")
    List<String> getDistinctVariableIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType, boolean withContingency);

    @Query(value = "SELECT distinct s.contingency.contingencyId from SensitivityEntity as s " +
            "where s.result.resultUuid = :resultUuid and s.factor.functionType = :sensitivityFunctionType " +
            "order by s.contingency.contingencyId")
    List<String> getDistinctContingencyIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType);

    @Modifying
    @Query(value = "DELETE FROM SensitivityEntity WHERE result.resultUuid = ?1")
    void deleteSensitivityBySensitivityAnalysisResultUUid(UUID resultUuid);

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

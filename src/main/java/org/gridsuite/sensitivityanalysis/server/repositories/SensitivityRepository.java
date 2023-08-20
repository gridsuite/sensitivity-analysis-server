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

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author Seddik Yengui <seddik.yengui at rte-france.com>
 */

public interface SensitivityRepository extends JpaRepository<SensitivityEntity, UUID> {
    Page<SensitivityEntity> findByResult(AnalysisResultEntity result, Pageable pageable);

    //Page<SensitivityEntity> findAllByResultAndFactorIndexInAndContingencyIndexIsLessThan(AnalysisResultEntity result, List<Integer> factorIndex, int contingencyIndex, Pageable pageable);
    //Page<SensitivityEntity> findAllByResultAndFactorIndexInAndContingencyIndexIsGreaterThanEqual(AnalysisResultEntity result, List<Integer> factorIndex, int contingencyIndex, Pageable pageable);

    List<SensitivityEntity> findByResult(AnalysisResultEntity result);
    //List<SensitivityEntity> findAllByResultAndContingencyIndexIsGreaterThan(AnalysisResultEntity result, int contingencyIndex);
    //List<SensitivityEntity> findByResultAndFactorIndexInAndContingencyIndexIsLessThan(AnalysisResultEntity result, List<Integer> factorIndex, int contingencyIndex);

    Page<SensitivityEntity> findAllByResultAndFactor_FunctionTypeAndContingencyIsNull(AnalysisResultEntity result, SensitivityFunctionType functionType, Pageable pageable);
    Page<SensitivityEntity> findAllByResultAndFactor_FunctionTypeAndContingencyIsNotNull(AnalysisResultEntity result, SensitivityFunctionType functionType, Pageable pageable);
    int countByResultAndFactor_FunctionTypeAndContingencyIsNull(AnalysisResultEntity result, SensitivityFunctionType functionType);
    int countByResultAndFactor_FunctionTypeAndContingencyIsNotNull(AnalysisResultEntity result, SensitivityFunctionType functionType);

    List<SensitivityEntity> findAll(Specification<SensitivityEntity> specification);
    Page<SensitivityEntity> findAll(Specification<SensitivityEntity> specification, Pageable pageable);

    static Specification<SensitivityEntity> getSpecification(AnalysisResultEntity sas,
                                                             SensitivityFunctionType functionType,
                                                             Collection<String> functionIds,
                                                             Collection<String> variableIds,
                                                             boolean withContingency) {
        return ((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("result").get("resultUuid"), sas.getResultUuid()));
            predicates.add(withContingency ? criteriaBuilder.isNull(root.get("contingency")) : criteriaBuilder.isNotNull(root.get("contingency")));
            predicates.add(criteriaBuilder.equal(root.get("factor").get("functionType"), functionType));

            if (functionIds != null && !functionIds.isEmpty()) {
                var funcIdPredicates = functionIds.stream()
                        .map(funcId -> criteriaBuilder.equal(root.get("factor").get("functionId"), funcId))
                        .toArray(Predicate[]::new);
                predicates.add(criteriaBuilder.or(funcIdPredicates));
            }

            if (variableIds != null && !variableIds.isEmpty()) {
                var varIdPredicates = variableIds.stream()
                        .map(varId -> criteriaBuilder.equal(root.get("factor").get("variableId"), varId))
                        .toArray(Predicate[]::new);
                predicates.add(criteriaBuilder.or(varIdPredicates));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        });
    }
}

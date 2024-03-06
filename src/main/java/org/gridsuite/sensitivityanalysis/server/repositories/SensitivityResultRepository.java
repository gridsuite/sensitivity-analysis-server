package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.sensitivity.SensitivityFunctionType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SensitivityResultRepository extends JpaRepository<SensitivityResultEntity, UUID>, JpaSpecificationExecutor<SensitivityResultEntity> {
    String FACTOR = "factor";
    String CONTINGENCY = "contingencyResult";

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity f WHERE f.result.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    static Specification<SensitivityResultEntity> getSpecification(AnalysisResultEntity sas,
                                                                   SensitivityFunctionType functionType,
                                                                   Collection<String> functionIds,
                                                                   Collection<String> variableIds,
                                                                   Collection<String> contingencyIds) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = List.of(
                orPredicate(criteriaBuilder, root, List.of(sas.getResultUuid()), "result", "resultUuid"),
                orPredicate(criteriaBuilder, root, List.of(functionType), FACTOR, "functionType"),
                orPredicate(criteriaBuilder, root, functionIds, FACTOR, "functionId"),
                orPredicate(criteriaBuilder, root, variableIds, FACTOR, "variableId"),
                orPredicate(criteriaBuilder, root, contingencyIds, CONTINGENCY, "contingencyId")
            );
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    static Specification<SensitivityResultEntity> getSpecification(AnalysisResultEntity sas,
                                                                   SensitivityFunctionType functionType,
                                                                   Collection<String> functionIds,
                                                                   Collection<String> variableIds) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = List.of(
                orPredicate(criteriaBuilder, root, List.of(sas.getResultUuid()), "result", "resultUuid"),
                orPredicate(criteriaBuilder, root, List.of(functionType), FACTOR, "functionType"),
                orPredicate(criteriaBuilder, root, functionIds, FACTOR, "functionId"),
                orPredicate(criteriaBuilder, root, variableIds, FACTOR, "variableId"),
                criteriaBuilder.isNull(root.get(CONTINGENCY))
            );
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate orPredicate(CriteriaBuilder criteriaBuilder,
                                         Root<SensitivityResultEntity> root,
                                         Collection<?> collection,
                                         String fieldName,
                                         String subFieldName) {
        if (!CollectionUtils.isEmpty(collection)) {
            Expression<?> expression = subFieldName == null ? root.get(fieldName) : root.get(fieldName).get(subFieldName);
            var predicate = collection.stream()
                .map(id -> criteriaBuilder.equal(expression, id))
                .toArray(Predicate[]::new);
            return criteriaBuilder.or(predicate);
        }
        return criteriaBuilder.and(); // Always True
    }
}

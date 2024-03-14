package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.sensitivity.SensitivityFunctionType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SensitivityResultRepository extends JpaRepository<SensitivityResultEntity, UUID>, JpaSpecificationExecutor<SensitivityResultEntity> {
    String FACTOR = "factor";
    String CONTINGENCY = "contingencyResult";

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity s WHERE s.result.resultUuid = :analysisResultUuid AND s.preContingencySensitivityResult is not null")
    void deleteAllPostContingenciesByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity s WHERE s.result.resultUuid = :analysisResultUuid")
    void deleteAllByAnalysisResultUuid(UUID analysisResultUuid);

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity s WHERE s.preContingencySensitivityResult is not null")
    void deleteAllPostContingencies();

    @Modifying
    @Query(value = "DELETE FROM SensitivityResultEntity")
    void deleteAll();

    @Query(value = "SELECT distinct s.factor.functionId from SensitivityResultEntity as s " +
        "where s.result.resultUuid = :resultUuid " +
        "and s.factor.functionType = :sensitivityFunctionType " +
        "and ((:withContingency = true and s.contingencyResult is not null ) or (:withContingency = false and s.contingencyResult is null ))")
    List<String> getDistinctFunctionIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType, boolean withContingency);

    @Query(value = "SELECT distinct s.factor.variableId from SensitivityResultEntity as s " +
        "where s.result.resultUuid = :resultUuid " +
        "and s.factor.functionType = :sensitivityFunctionType " +
        "and ((:withContingency = true and s.contingencyResult is not null ) or (:withContingency = false and s.contingencyResult is null ))")
    List<String> getDistinctVariableIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType, boolean withContingency);

    @Query(value = "SELECT distinct s.contingencyResult.contingencyId from SensitivityResultEntity as s " +
        "where s.result.resultUuid = :resultUuid and s.factor.functionType = :sensitivityFunctionType")
    List<String> getDistinctContingencyIds(UUID resultUuid, SensitivityFunctionType sensitivityFunctionType);

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
                criteriaBuilder.and(criteriaBuilder.isNotNull(root.get(CONTINGENCY)), orPredicate(criteriaBuilder, root, contingencyIds, CONTINGENCY, "contingencyId"))
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

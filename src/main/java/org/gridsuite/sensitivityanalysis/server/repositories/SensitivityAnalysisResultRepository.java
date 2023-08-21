/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityValue;
import org.gridsuite.sensitivityanalysis.server.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyEmbeddable;
import org.gridsuite.sensitivityanalysis.server.entities.GlobalStatusEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityFactorEmbeddable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public class SensitivityAnalysisResultRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalysisResultRepository.class);

    private final GlobalStatusRepository globalStatusRepository;

    private final AnalysisResultRepository analysisResultRepository;

    private final SensitivityRepository sensitivityRepository;

    public SensitivityAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                               AnalysisResultRepository analysisResultRepository,
                                               SensitivityRepository sensitivityRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.sensitivityRepository = sensitivityRepository;
    }

    private static AnalysisResultEntity toAnalysisResultEntity(UUID resultUuid, SensitivityAnalysisResult result) {
        List<SensitivityFactorEmbeddable> factors = result.getFactors().stream().map(f ->
            new SensitivityFactorEmbeddable(f.getFunctionType(), f.getFunctionId(),
                f.getVariableType(), f.getVariableId(), f.isVariableSet(),
                f.getContingencyContext().getContextType(), f.getContingencyContext().getContingencyId()))
            .collect(Collectors.toList());
        List<ContingencyEmbeddable> contingencies = result.getContingencyStatuses().stream().map(cs ->
                new ContingencyEmbeddable(cs.getContingencyId(), cs.getStatus()))
            .collect(Collectors.toList());
        Supplier<Stream<SensitivityValue>> sensitivityWithoutContingency = () -> result.getValues().stream().filter(s -> s.getContingencyIndex() < 0);
        List<SensitivityEntity> sensitivities = result.getValues().stream()
            .map(v -> {
                Double value = v.getValue();
                Double functionReference = v.getFunctionReference();
                Double valueAfter = null;
                Double functionReferenceAfter = null;
                if (v.getContingencyIndex() >= 0) {
                    var factor = factors.get(v.getFactorIndex());
                    var other = sensitivityWithoutContingency.get().filter(s -> {
                        var funcId = factors.get(s.getFactorIndex()).getFunctionId();
                        var varId = factors.get(s.getFactorIndex()).getVariableId();

                        return Objects.equals(factor.getFunctionId(), funcId) && Objects.equals(factor.getVariableId(), varId);
                    }).findFirst().orElse(null);
                    value = other == null ? null : other.getValue();
                    functionReference = other == null ? null : other.getFunctionReference();
                    valueAfter = v.getValue();
                    functionReferenceAfter = v.getFunctionReference();
                }
                return new SensitivityEntity(factors.get(v.getFactorIndex()),
                        v.getContingencyIndex() < 0 ? null : contingencies.get(v.getContingencyIndex()),
                        value,
                        functionReference,
                        valueAfter,
                        functionReferenceAfter);
            })
            .collect(Collectors.toList());
        //To avoid consistency issue we truncate the time to microseconds since postgres and h2 can only store a precision of microseconds
        return new AnalysisResultEntity(resultUuid, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), sensitivities);
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void insert(UUID resultUuid, SensitivityAnalysisResult result) {
        Objects.requireNonNull(resultUuid);

        if (result != null) {
            analysisResultRepository.save(toAnalysisResultEntity(resultUuid, result));
        }
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        analysisResultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        analysisResultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public String findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return globalEntity.getStatus();
        } else {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public SensitivityRunQueryResult getRunResult(UUID resultUuid, ResultsSelector selector) {
        AnalysisResultEntity sas = analysisResultRepository.findByResultUuid(resultUuid);
        if (sas == null) {
            return null;
        }

        List<SensitivityEntity> sensitivityEntities = getSensitivityEntities(sas, selector);
        return getSensitivityRunQueryResult(selector, sas, sensitivityEntities, null);
    }

    private List<SensitivityEntity> getSensitivityEntities(AnalysisResultEntity sas, ResultsSelector selector) {
        Specification<SensitivityEntity> specification = SensitivityRepository.getSpecification(sas,
                selector.getFunctionType(),
                selector.getFunctionIds(),
                selector.getVariableIds(),
                selector.getIsJustBefore());

        int pageNumber = 0;
        int pageSize = Integer.MAX_VALUE;
        if (selector.getPageSize() != null && selector.getPageNumber() != null) {
            pageNumber = selector.getPageNumber();
            pageSize = selector.getPageSize();
        }

        Map<ResultsSelector.SortKey, Integer> sortKeysWithWeightAndDirection = selector.getSortKeysWithWeightAndDirection();
        List<Sort.Order> sortListFiltered = new ArrayList<>();
        if (sortKeysWithWeightAndDirection != null && !sortKeysWithWeightAndDirection.isEmpty()) {
            List<Sort.Order> sortList = new ArrayList<>(Collections.nCopies(
                    sortKeysWithWeightAndDirection.size(), null));
            sortKeysWithWeightAndDirection.keySet().forEach(sortKey -> {
                int index = Math.abs(sortKeysWithWeightAndDirection.get(sortKey)) - 1;
                sortList.add(index, sortKeysWithWeightAndDirection.get(sortKey) > 0 ? Sort.Order.asc(Objects.requireNonNull(getSort(sortKey))) :
                        Sort.Order.desc(Objects.requireNonNull(getSort(sortKey))));
            });
            sortListFiltered = sortList.stream().filter(Objects::nonNull).toList();
        }
        Pageable pageable = sortListFiltered.isEmpty() ? PageRequest.of(pageNumber, pageSize) :
                PageRequest.of(pageNumber, pageSize, Sort.by(sortListFiltered));

        Page<SensitivityEntity> sensiResults = sensitivityRepository.findAll(specification, pageable);
        return sensiResults.getContent();
    }

    private SensitivityRunQueryResult getSensitivityRunQueryResult(ResultsSelector selector, AnalysisResultEntity sas, List<SensitivityEntity> sensitivityEntities, Pageable pageable) {
        if (sas == null) {
            return null;
        }

        SensitivityRunQueryResult.SensitivityRunQueryResultBuilder retBuilder = SensitivityRunQueryResult.builder()
            .isJustBefore(selector.getIsJustBefore())
            .functionType(selector.getFunctionType())
            .requestedChunkSize(selector.getPageSize() == null ? 0 : selector.getPageSize())
            .chunkOffset(selector.getOffset() == null ? 0 : selector.getOffset());

        if (sensitivityEntities.isEmpty()) {
            retBuilder.totalSensitivitiesCount(0);
            retBuilder.filteredSensitivitiesCount(0);
            retBuilder.sensitivities(List.of());
            return retBuilder.build();
        }

        Set<String> allFunctionIds = new TreeSet<>();
        Set<String> allVariableIds = new TreeSet<>();
        Set<String> allContingencyIds = new TreeSet<>();

        int count;
        if (selector.getIsJustBefore()) {
            List<SensitivityOfTo> befores = new ArrayList<>();
            sensitivityEntities.forEach(sensitivityEntity -> {
                SensitivityFactorEmbeddable factorEmbeddable = sensitivityEntity.getFactor();
                allFunctionIds.add(factorEmbeddable.getFunctionId());
                allVariableIds.add(factorEmbeddable.getVariableId());
                befores.add(SensitivityOfTo.builder()
                        .funcId(factorEmbeddable.getFunctionId())
                        .varId(factorEmbeddable.getVariableId())
                        .varIsAFilter(factorEmbeddable.isVariableSet())
                        .value(sensitivityEntity.getValue())
                        .functionReference(sensitivityEntity.getFunctionReference())
                        .build());
            });
            count = sensitivityRepository.countByResultAndFactorFunctionTypeAndContingencyIsNull(sas, selector.getFunctionType());
            complete(retBuilder, allFunctionIds, allVariableIds, null, count, befores);
        } else {
            List<SensitivityWithContingency> after = new ArrayList<>();
            sensitivityEntities.forEach(sensitivityEntity -> {
                SensitivityFactorEmbeddable factorEmbeddable = sensitivityEntity.getFactor();
                allFunctionIds.add(factorEmbeddable.getFunctionId());
                allVariableIds.add(factorEmbeddable.getVariableId());
                if (factorEmbeddable.getContingencyId() != null) {
                    allContingencyIds.add(factorEmbeddable.getContingencyId());
                }
                SensitivityWithContingency.SensitivityWithContingencyBuilder<?, ?> r = SensitivityWithContingency.builder()
                        .funcId(factorEmbeddable.getFunctionId())
                        .varId(factorEmbeddable.getVariableId())
                        .varIsAFilter(factorEmbeddable.isVariableSet())
                        .contingencyId(sensitivityEntity.getContingency().getId());

                if (sensitivityEntity.getValue() != null) {
                    r.value(sensitivityEntity.getValue());
                }

                if (sensitivityEntity.getFunctionReference() != null) {
                    r.functionReference(sensitivityEntity.getFunctionReference());
                }

                if (sensitivityEntity.getValueAfter() != null) {
                    r.valueAfter(sensitivityEntity.getValueAfter());
                }

                if (sensitivityEntity.getFunctionReferenceAfter() != null) {
                    r.functionReferenceAfter(sensitivityEntity.getFunctionReferenceAfter());
                }
                after.add(r.build());
            });
            count = sensitivityRepository.countByResultAndFactorFunctionTypeAndContingencyIsNotNull(sas, selector.getFunctionType());
            complete(retBuilder, allFunctionIds, allVariableIds, allContingencyIds, count, after);
        }
        return retBuilder.build();
    }

    private void complete(SensitivityRunQueryResult.SensitivityRunQueryResultBuilder retBuilder,
            Collection<String> allFunctionIds,
            Collection<String> allVariableIds,
            Collection<String> allContingencyIds,
            int count,
            List<? extends SensitivityOfTo> filtered

    ) {
        retBuilder.allFunctionIds(allFunctionIds.stream().sorted().collect(Collectors.toList()));
        retBuilder.allVariableIds(allVariableIds.stream().sorted().collect(Collectors.toList()));
        if (allContingencyIds != null) {
            retBuilder.allContingencyIds(allContingencyIds.stream().sorted().collect(Collectors.toList()));
        }
        retBuilder.filteredSensitivitiesCount(filtered.size());
        retBuilder.sensitivities(filtered);
        retBuilder.totalSensitivitiesCount(count);
    }

    private String getSort(ResultsSelector.SortKey sortKey) {
        switch (sortKey) {
            case FUNCTION : return "factor.functionId";
            case SENSITIVITY : return "value";
            case REFERENCE : return "functionReference";
            case VARIABLE : return "factor.variableId";
            case CONTINGENCY : return "contingency.id";
            case POST_REFERENCE : return "functionReferenceAfter";
            case POST_SENSITIVITY : return "valueAfter";
            default: return null;
        }
    }
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.contingency.Contingency;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityResultFilterOptions;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.SortKey;
import org.gridsuite.sensitivityanalysis.server.entities.*;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    private final SensitivityResultRepository sensitivityResultRepository;

    private final SensitivityFactorRepository sensitivityFactorRepository;

    private final ContingencyResultRepository contingencyResultRepository;

    private static final String DEFAULT_SENSITIVITY_SORT_COLUMN = "sensitivityId";

    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.ASC;

    public SensitivityAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                               AnalysisResultRepository analysisResultRepository,
                                               SensitivityRepository sensitivityRepository, SensitivityResultRepository sensitivityResultRepository, SensitivityFactorRepository sensitivityFactorRepository, ContingencyResultRepository contingencyResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.sensitivityRepository = sensitivityRepository;
        this.sensitivityResultRepository = sensitivityResultRepository;
        this.sensitivityFactorRepository = sensitivityFactorRepository;
        this.contingencyResultRepository = contingencyResultRepository;
    }

    private static AnalysisResultEntity toAnalysisResultEntity(UUID resultUuid, SensitivityAnalysisResult result) {
        List<SensitivityFactorEmbeddable> factors = result.getFactors().stream().map(f ->
                new SensitivityFactorEmbeddable(f.getFunctionType(), f.getFunctionId(),
                        f.getVariableType(), f.getVariableId(), f.isVariableSet(),
                        f.getContingencyContext().getContextType(), f.getContingencyContext().getContingencyId())).toList();
        List<ContingencyEmbeddable> contingencies = result.getContingencyStatuses().stream().map(cs ->
                new ContingencyEmbeddable(cs.getContingencyId(), cs.getStatus())).toList();
        Supplier<Stream<SensitivityValue>> sensitivityWithoutContingency = () -> result.getValues().stream().filter(s -> s.getContingencyIndex() < 0);
        List<SensitivityEntity> sensitivities = result.getValues().stream()
            .filter(v -> !Double.isNaN(v.getValue()))
            .map(v -> getSensitivityEntity(factors, contingencies, sensitivityWithoutContingency, v))
            .collect(Collectors.toList());
        //To avoid consistency issue we truncate the time to microseconds since postgres and h2 can only store a precision of microseconds
        return new AnalysisResultEntity(resultUuid, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), sensitivities);
    }

    private static SensitivityEntity getSensitivityEntity(List<SensitivityFactorEmbeddable> factors,
                                                          List<ContingencyEmbeddable> contingencies,
                                                          Supplier<Stream<SensitivityValue>> sensitivityWithoutContingency,
                                                          SensitivityValue sensitivityValue) {
        if (sensitivityValue.getContingencyIndex() >= 0) {
            var factor = factors.get(sensitivityValue.getFactorIndex());
            var base = sensitivityWithoutContingency.get().filter(s -> {
                var funcId = factors.get(s.getFactorIndex()).getFunctionId();
                var varId = factors.get(s.getFactorIndex()).getVariableId();

                return Objects.equals(factor.getFunctionId(), funcId) && Objects.equals(factor.getVariableId(), varId);
            }).findFirst().orElse(null);
            return new SensitivityEntity(factors.get(sensitivityValue.getFactorIndex()),
                                                     sensitivityValue.getContingencyIndex() < 0 ? null : contingencies.get(sensitivityValue.getContingencyIndex()),
                                                     base == null ? 0 : base.getValue(),
                                                     base == null ? 0 : base.getFunctionReference(),
                                                     sensitivityValue.getValue(),
                                                     sensitivityValue.getFunctionReference());
        }
        return new SensitivityEntity(factors.get(sensitivityValue.getFactorIndex()),
                                     sensitivityValue.getContingencyIndex() < 0 ? null : contingencies.get(sensitivityValue.getContingencyIndex()),
                                     sensitivityValue.getValue(),
                                     sensitivityValue.getFunctionReference());
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    public AnalysisResultEntity insertAnalysisResult(UUID resultUuid) {
        return analysisResultRepository.save(new AnalysisResultEntity(resultUuid, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)));
    }

    private void insertNewResults(UUID resultUuid, SensitivityAnalysisResult result) {
        result.getValues().forEach(s -> {
            AnalysisResultEntity analysisResult = analysisResultRepository.findByResultUuid(resultUuid);
            ContingencyResultEntity contingencyResult = contingencyResultRepository.findByAnalysisResultAndIndex(analysisResult, s.getContingencyIndex());
            Optional.ofNullable(contingencyResult).ifPresent(r -> r.setStatus(result.getContingencyStatus(r.getContingencyId())));
            SensitivityFactorEntity sensitivityFactor = sensitivityFactorRepository.findByAnalysisResultAndIndex(analysisResult, s.getFactorIndex());
            sensitivityResultRepository.save(new SensitivityResultEntity(analysisResult, sensitivityFactor, s.getValue(), s.getFunctionReference(), contingencyResult));
        });
    }

    @Transactional
    public void insertFactorsAndContingencies(List<SensitivityFactor> factors, List<Contingency> contingencies, AnalysisResultEntity analysisResult) {
        insertFactors(factors, analysisResult);
        insertContingencies(contingencies, analysisResult);
    }

    private void insertFactors(List<SensitivityFactor> factors, AnalysisResultEntity analysisResult) {
        List<SensitivityFactorEntity> sensitivityFactorEntities = IntStream.range(0, factors.size())
            .mapToObj(i -> {
                SensitivityFactor f = factors.get(i);
                return new SensitivityFactorEntity(
                    i,
                    f.getFunctionType(),
                    f.getFunctionId(),
                    f.getVariableType(),
                    f.getVariableId(),
                    f.isVariableSet(),
                    analysisResult
                );
            })
            .toList();
        sensitivityFactorRepository.saveAll(sensitivityFactorEntities);
    }

    private void insertContingencies(List<Contingency> contingencies, AnalysisResultEntity analysisResult) {
        List<ContingencyResultEntity> contingencyResultEntities = IntStream.range(0, contingencies.size())
            .mapToObj(i -> {
                Contingency c = contingencies.get(i);
                return new ContingencyResultEntity(i, c.getId(), analysisResult);
            })
            .toList();
        contingencyResultRepository.saveAll(contingencyResultEntities);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void insert(UUID resultUuid, SensitivityAnalysisResult result, String status) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            analysisResultRepository.save(toAnalysisResultEntity(resultUuid, result));
            insertNewResults(resultUuid, result);
        }
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        globalStatusRepository.deleteByResultUuid(resultUuid);
        sensitivityRepository.deleteSensitivityBySensitivityAnalysisResultUUid(resultUuid);
        contingencyResultRepository.deleteAllByAnalysisResultUuid(resultUuid);
        sensitivityFactorRepository.deleteAllByAnalysisResultUuid(resultUuid);
        analysisResultRepository.deleteByResultUuid(resultUuid);
        LOGGER.info("Sensitivity analysis result '{}' has been deleted in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
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
    public SensitivityResultFilterOptions getSensitivityResultFilterOptions(UUID resultUuid, ResultsSelector selector) {
        AnalysisResultEntity sas = analysisResultRepository.findByResultUuid(resultUuid);
        if (sas == null) {
            return null;
        }

        boolean withContingency = selector.getTabSelection() == ResultTab.N_K;
        SensitivityResultFilterOptions.SensitivityResultFilterOptionsBuilder sensitivityResultOptionsBuilder = SensitivityResultFilterOptions.builder()
                .allFunctionIds(sensitivityRepository.getDistinctFunctionIds(sas.getResultUuid(), selector.getFunctionType(), withContingency))
                .allVariableIds(sensitivityRepository.getDistinctVariableIds(sas.getResultUuid(), selector.getFunctionType(), withContingency));

        if (withContingency) {
            sensitivityResultOptionsBuilder.allContingencyIds(sensitivityRepository.getDistinctContingencyIds(sas.getResultUuid(), selector.getFunctionType())
                            .stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
        }

        return sensitivityResultOptionsBuilder.build();
    }

    @Transactional(readOnly = true)
    public SensitivityRunQueryResult getRunResult(UUID resultUuid, ResultsSelector selector) {
        AnalysisResultEntity sas = analysisResultRepository.findByResultUuid(resultUuid);
        if (sas == null) {
            return null;
        }

        Specification<SensitivityEntity> specification = SensitivityRepository.getSpecification(sas,
                selector.getFunctionType(),
                selector.getFunctionIds(),
                selector.getVariableIds(),
                selector.getContingencyIds(),
                selector.getTabSelection() == ResultTab.N_K);

        List<SensitivityEntity> sensitivityEntities = getSensitivityEntities(selector, specification);
        long filteredSensitivitiesCount = sensitivityRepository.count(specification);
        return getSensitivityRunQueryResult(selector, sas, sensitivityEntities, filteredSensitivitiesCount);
    }

    private List<SensitivityEntity> getSensitivityEntities(ResultsSelector selector, Specification<SensitivityEntity> specification) {
        int pageNumber = 0;
        int pageSize = Integer.MAX_VALUE;
        if (selector.getPageSize() != null &&
                selector.getPageSize() > 0 &&
                selector.getPageNumber() != null) {
            pageNumber = selector.getPageNumber();
            pageSize = selector.getPageSize();
        }

        Map<SortKey, Integer> sortKeysWithWeightAndDirection = selector.getSortKeysWithWeightAndDirection();
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
        Page<SensitivityEntity> sensiResults = sensitivityRepository.findAll(specification, addDefaultSort(pageable, DEFAULT_SENSITIVITY_SORT_COLUMN));
        return sensiResults.getContent();
    }

    private SensitivityRunQueryResult getSensitivityRunQueryResult(ResultsSelector selector, AnalysisResultEntity sas,
                                                                   List<SensitivityEntity> sensitivityEntities,
                                                                   long filteredSensitivitiesCount) {
        if (sas == null) {
            return null;
        }

        boolean withContingency = selector.getTabSelection() == ResultTab.N_K;
        SensitivityRunQueryResult.SensitivityRunQueryResultBuilder retBuilder = SensitivityRunQueryResult.builder()
            .resultTab(selector.getTabSelection())
            .functionType(selector.getFunctionType())
            .requestedChunkSize(selector.getPageSize() == null ? 0 : selector.getPageSize())
            .chunkOffset(selector.getOffset() == null ? 0 : selector.getOffset());

        if (sensitivityEntities.isEmpty()) {
            retBuilder.totalSensitivitiesCount(0L);
            retBuilder.filteredSensitivitiesCount(0L);
            retBuilder.sensitivities(List.of());
            return retBuilder.build();
        }

        var totalSensitivitiesCountSpec = SensitivityRepository.getSpecification(sas,
                selector.getFunctionType(),
                null,
                null,
                null,
                withContingency);

        long count = sensitivityRepository.count(totalSensitivitiesCountSpec);
        if (!withContingency) {
            List<SensitivityOfTo> befores = new ArrayList<>();
            sensitivityEntities.forEach(sensitivityEntity -> {
                SensitivityFactorEmbeddable factorEmbeddable = sensitivityEntity.getFactor();
                befores.add(SensitivityOfTo.builder()
                        .funcId(factorEmbeddable.getFunctionId())
                        .varId(factorEmbeddable.getVariableId())
                        .varIsAFilter(factorEmbeddable.isVariableSet())
                        .value(sensitivityEntity.getValue())
                        .functionReference(sensitivityEntity.getFunctionReference())
                        .build());
            });
            complete(retBuilder, filteredSensitivitiesCount, count, befores);
        } else {
            List<SensitivityWithContingency> after = new ArrayList<>();
            sensitivityEntities.forEach(sensitivityEntity -> {
                SensitivityFactorEmbeddable factorEmbeddable = sensitivityEntity.getFactor();
                SensitivityWithContingency r = SensitivityWithContingency.builder()
                        .funcId(factorEmbeddable.getFunctionId())
                        .varId(factorEmbeddable.getVariableId())
                        .varIsAFilter(factorEmbeddable.isVariableSet())
                        .contingencyId(sensitivityEntity.getContingency().getContingencyId())
                        .value(sensitivityEntity.getValue())
                        .functionReference(sensitivityEntity.getFunctionReference())
                        .valueAfter(sensitivityEntity.getValueAfter())
                        .functionReferenceAfter(sensitivityEntity.getFunctionReferenceAfter())
                        .build();

                after.add(r);
            });
            complete(retBuilder, filteredSensitivitiesCount, count, after);
        }
        return retBuilder.build();
    }

    private void complete(SensitivityRunQueryResult.SensitivityRunQueryResultBuilder retBuilder,
            long filteredSensitivitiesCount,
            long count,
            List<? extends SensitivityOfTo> filtered

    ) {
        retBuilder.filteredSensitivitiesCount(filteredSensitivitiesCount);
        retBuilder.sensitivities(filtered);
        retBuilder.totalSensitivitiesCount(count);
    }

    private String getSort(SortKey sortKey) {
        return switch (sortKey) {
            case FUNCTION -> "factor.functionId";
            case SENSITIVITY -> "value";
            case REFERENCE -> "functionReference";
            case VARIABLE -> "factor.variableId";
            case CONTINGENCY -> "contingency.contingencyId";
            case POST_REFERENCE -> "functionReferenceAfter";
            case POST_SENSITIVITY -> "valueAfter";
        };
    }

    private Pageable addDefaultSort(Pageable pageable, String defaultSortColumn) {
        if (pageable.isPaged() && pageable.getSort().getOrderFor(defaultSortColumn) == null) {
            //if it's already sorted by our defaultColumn we don't add another sort by the same column
            Sort finalSort = pageable.getSort().and(Sort.by(DEFAULT_SORT_DIRECTION, defaultSortColumn));
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), finalSort);
        }
        //nothing to do if the request is not paged
        return pageable;
    }
}

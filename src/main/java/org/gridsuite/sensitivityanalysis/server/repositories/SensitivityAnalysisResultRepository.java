/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.contingency.Contingency;
import com.powsybl.sensitivity.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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

    private final SensitivityResultRepository sensitivityResultRepository;

    private final SensitivityFactorRepository sensitivityFactorRepository;

    private final ContingencyResultRepository contingencyResultRepository;

    private static final String DEFAULT_SENSITIVITY_SORT_COLUMN = "sensitivityId";

    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.ASC;

    public SensitivityAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                               AnalysisResultRepository analysisResultRepository,
                                               SensitivityResultRepository sensitivityResultRepository,
                                               SensitivityFactorRepository sensitivityFactorRepository,
                                               ContingencyResultRepository contingencyResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.sensitivityResultRepository = sensitivityResultRepository;
        this.sensitivityFactorRepository = sensitivityFactorRepository;
        this.contingencyResultRepository = contingencyResultRepository;
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    public void createResults(List<List<SensitivityFactor>> factorsGroup, List<Contingency> contingencies, UUID resultUuid) {
        AnalysisResultEntity analysisResult = analysisResultRepository.save(new AnalysisResultEntity(resultUuid, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)));
        Map<String, ContingencyResultEntity> contingenciesById = createContingencyResults(contingencies, analysisResult);
        Set<SensitivityResultEntity> results = createSensitivityResults(factorsGroup, analysisResult, contingenciesById);
        sensitivityResultRepository.saveAllAndFlush(results);
    }

    private static Map<String, ContingencyResultEntity> createContingencyResults(List<Contingency> contingencies, AnalysisResultEntity analysisResult) {
        return IntStream.range(0, contingencies.size())
            .mapToObj(i -> new ContingencyResultEntity(i, contingencies.get(i).getId(), analysisResult))
            .collect(Collectors.toMap(
                ContingencyResultEntity::getContingencyId,
                Function.identity()
            ));
    }

    private static Set<SensitivityResultEntity> createSensitivityResults(List<List<SensitivityFactor>> factorsGroups, AnalysisResultEntity analysisResult, Map<String, ContingencyResultEntity> contingenciesById) {
        AtomicInteger factorCounter = new AtomicInteger(0);
        return factorsGroups.stream()
            .flatMap(factorsGroup -> {
                if (factorsGroup.isEmpty()) {
                    return Stream.of();
                }

                // For the information we need to create the entities, all the sensitivity factors of a group are equivalent
                // So we can keep the pre-contingency sensitivity factor.
                SensitivityFactor preContingencySensitivityfactor = factorsGroup.get(0);
                SensitivityResultEntity preContingencySensitivityResult = new SensitivityResultEntity(
                    analysisResult,
                    createSensitivityFactor(preContingencySensitivityfactor, factorCounter.getAndIncrement(), analysisResult),
                    null,
                    null
                );

                // No need to return preContingencySensitivityResult if it's not the only result in the group because it will be saved
                // by JPA cascading persist operation (as it is referenced in the sensitivity results of the contingencies).
                // But if it is the only one we should return it explicitly.
                if (factorsGroup.size() == 1) {
                    return Stream.of(preContingencySensitivityResult);
                }

                // We should skip the first element as we want to only keep contingency related factors here
                return factorsGroup.subList(1, factorsGroup.size()).stream()
                    .map(sensitivityFactor -> new SensitivityResultEntity(
                        analysisResult,
                        createSensitivityFactor(preContingencySensitivityfactor, factorCounter.getAndIncrement(), analysisResult),
                        contingenciesById.get(sensitivityFactor.getContingencyContext().getContingencyId()),
                        preContingencySensitivityResult
                    ));
            })
            .collect(Collectors.toSet());
    }

    private static SensitivityFactorEntity createSensitivityFactor(SensitivityFactor factor, int index, AnalysisResultEntity analysisResult) {
        return new SensitivityFactorEntity(
            index,
            factor.getFunctionType(),
            factor.getFunctionId(),
            factor.getVariableType(),
            factor.getVariableId(),
            factor.isVariableSet(),
            analysisResult
        );
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void saveGlobalStatus(UUID resultUuid, String status) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        globalStatusRepository.deleteByResultUuid(resultUuid);
        sensitivityResultRepository.deleteAllPostContingenciesByAnalysisResultUuid(resultUuid);
        sensitivityResultRepository.deleteAllByAnalysisResultUuid(resultUuid);
        sensitivityFactorRepository.deleteAllByAnalysisResultUuid(resultUuid);
        contingencyResultRepository.deleteAllByAnalysisResultUuid(resultUuid);
        analysisResultRepository.deleteById(resultUuid);
        LOGGER.info("Sensitivity analysis result '{}' has been deleted in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        sensitivityResultRepository.deleteAllPostContingencies();
        sensitivityResultRepository.deleteAll();
        sensitivityFactorRepository.deleteAll();
        contingencyResultRepository.deleteAll();
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
            .allFunctionIds(sensitivityResultRepository.getDistinctFunctionIds(sas.getResultUuid(), selector.getFunctionType(), withContingency))
            .allVariableIds(sensitivityResultRepository.getDistinctVariableIds(sas.getResultUuid(), selector.getFunctionType(), withContingency));

        if (withContingency) {
            sensitivityResultOptionsBuilder.allContingencyIds(sensitivityResultRepository.getDistinctContingencyIds(sas.getResultUuid(), selector.getFunctionType())
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

        Specification<SensitivityResultEntity> specification = selector.getTabSelection() == ResultTab.N_K ?
            SensitivityResultRepository.getSpecification(sas,
                selector.getFunctionType(),
                selector.getFunctionIds(),
                selector.getVariableIds(),
                selector.getContingencyIds()) :
            SensitivityResultRepository.getSpecification(sas,
                selector.getFunctionType(),
                selector.getFunctionIds(),
                selector.getVariableIds());

        Page<SensitivityResultEntity> sensitivityEntities = sensitivityResultRepository.findAll(specification, getPageable(selector));
        return getSensitivityRunQueryResult(selector, sas, sensitivityEntities);
    }

    private static Pageable getPageable(ResultsSelector selector) {
        int pageNumber = 0;
        int pageSize = Integer.MAX_VALUE;
        if (selector.getPageSize() != null &&
            selector.getPageSize() > 0 &&
            selector.getPageNumber() != null) {
            pageNumber = selector.getPageNumber();
            pageSize = selector.getPageSize();
        }
        List<Sort.Order> sortListFiltered = getOrders(selector);
        Pageable pageable = sortListFiltered.isEmpty() ? PageRequest.of(pageNumber, pageSize) :
            PageRequest.of(pageNumber, pageSize, Sort.by(sortListFiltered));
        return addDefaultSort(pageable, DEFAULT_SENSITIVITY_SORT_COLUMN);
    }

    private static List<Sort.Order> getOrders(ResultsSelector selector) {
        Map<SortKey, Integer> sortKeysWithWeightAndDirection = selector.getSortKeysWithWeightAndDirection();
        if (sortKeysWithWeightAndDirection != null && !sortKeysWithWeightAndDirection.isEmpty()) {
            List<Sort.Order> sortList = new ArrayList<>(Collections.nCopies(
                sortKeysWithWeightAndDirection.size(), null));
            sortKeysWithWeightAndDirection.keySet().forEach(sortKey -> {
                int index = Math.abs(sortKeysWithWeightAndDirection.get(sortKey)) - 1;
                sortList.add(index, sortKeysWithWeightAndDirection.get(sortKey) > 0 ? Sort.Order.asc(Objects.requireNonNull(getSort(sortKey))) :
                    Sort.Order.desc(Objects.requireNonNull(getSort(sortKey))));
            });
            return sortList.stream().filter(Objects::nonNull).toList();
        }
        return Collections.emptyList();
    }

    private SensitivityRunQueryResult getSensitivityRunQueryResult(ResultsSelector selector, AnalysisResultEntity sas,
                                                                   Page<SensitivityResultEntity> sensitivityEntities) {
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

        List<? extends SensitivityOfTo> sensitivities;
        if (!withContingency) {
            sensitivities = sensitivityEntities
                .stream()
                .map(sensitivityEntity -> (SensitivityOfTo) SensitivityOfTo.builder()
                    .funcId(sensitivityEntity.getFactor().getFunctionId())
                    .varId(sensitivityEntity.getFactor().getVariableId())
                    .varIsAFilter(sensitivityEntity.getFactor().isVariableSet())
                    .value(sensitivityEntity.getValue())
                    .functionReference(sensitivityEntity.getFunctionReference())
                    .build())
                .toList();
        } else {
            sensitivities = sensitivityEntities
                .stream()
                .map(sensitivityResultEntity -> {
                    SensitivityFactorEntity factor = sensitivityResultEntity.getFactor();
                    return SensitivityWithContingency.builder()
                        .funcId(factor.getFunctionId())
                        .varId(factor.getVariableId())
                        .varIsAFilter(factor.isVariableSet())
                        .contingencyId(sensitivityResultEntity.getContingencyResult().getContingencyId())
                        .value(sensitivityResultEntity.getPreContingencySensitivityResult().getValue())
                        .functionReference(sensitivityResultEntity.getPreContingencySensitivityResult().getFunctionReference())
                        .valueAfter(sensitivityResultEntity.getValue())
                        .functionReferenceAfter(sensitivityResultEntity.getFunctionReference())
                        .build();
                })
                .toList();
        }
        complete(retBuilder, sensitivityEntities.getTotalElements(), sensitivityEntities.getTotalElements(), sensitivities);
        return retBuilder.build();
    }

    private void complete(SensitivityRunQueryResult.SensitivityRunQueryResultBuilder retBuilder,
                          long filteredSensitivitiesCount,
                          long count,
                          List<? extends SensitivityOfTo> filtered
    ) {
        retBuilder.filteredSensitivitiesCount(filteredSensitivitiesCount);
        retBuilder.sensitivities(filtered);
        retBuilder.totalSensitivitiesCount(count); // Unused in the front
    }

    private static String getSort(SortKey sortKey) {
        return switch (sortKey) {
            case FUNCTION -> "factor.functionId";
            case SENSITIVITY, POST_SENSITIVITY -> "value";
            case REFERENCE, POST_REFERENCE -> "functionReference";
            case VARIABLE -> "factor.variableId";
            case CONTINGENCY -> "contingencyResult.contingencyId";
        };
    }

    private static Pageable addDefaultSort(Pageable pageable, String defaultSortColumn) {
        if (pageable.isPaged() && pageable.getSort().getOrderFor(defaultSortColumn) == null) {
            //if it's already sorted by our defaultColumn we don't add another sort by the same column
            Sort finalSort = pageable.getSort().and(Sort.by(DEFAULT_SORT_DIRECTION, defaultSortColumn));
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), finalSort);
        }
        //nothing to do if the request is not paged
        return pageable;
    }
}

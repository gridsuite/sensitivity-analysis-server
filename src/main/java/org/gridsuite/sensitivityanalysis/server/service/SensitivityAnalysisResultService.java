/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.ws.commons.computation.service.AbstractComputationResultService;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultTab;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.resultselector.SortKey;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityResultFilterOptions;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.GlobalStatusEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.AnalysisResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.ContingencyResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.GlobalStatusRepository;
import org.gridsuite.sensitivityanalysis.server.entities.*;
import org.gridsuite.sensitivityanalysis.server.repositories.RawSensitivityResultRepository;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityResultRepository;
import org.gridsuite.sensitivityanalysis.server.util.ContingencyResult;
import org.gridsuite.sensitivityanalysis.server.util.SensitivityResultSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisResultService extends AbstractComputationResultService<SensitivityAnalysisStatus> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalysisResultService.class);

    private static final String DEFAULT_SENSITIVITY_SORT_COLUMN = "id";

    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.ASC;

    private final GlobalStatusRepository globalStatusRepository;

    private final AnalysisResultRepository analysisResultRepository;

    private final SensitivityResultRepository sensitivityResultRepository;

    private final ContingencyResultRepository contingencyResultRepository;

    private final RawSensitivityResultRepository rawSensitivityResultRepository;

    public SensitivityAnalysisResultService(GlobalStatusRepository globalStatusRepository,
                                               AnalysisResultRepository analysisResultRepository,
                                               SensitivityResultRepository sensitivityResultRepository,
                                               ContingencyResultRepository contingencyResultRepository,
                                               RawSensitivityResultRepository rawSensitivityResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.analysisResultRepository = analysisResultRepository;
        this.sensitivityResultRepository = sensitivityResultRepository;
        this.contingencyResultRepository = contingencyResultRepository;
        this.rawSensitivityResultRepository = rawSensitivityResultRepository;
    }

    @Transactional
    @Override
    public void insertStatus(List<UUID> resultUuids, SensitivityAnalysisStatus status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> new GlobalStatusEntity(uuid, status.name())).toList());
    }

    @Transactional
    public AnalysisResultEntity insertAnalysisResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return analysisResultRepository.save(new AnalysisResultEntity(resultUuid, Instant.now().truncatedTo(ChronoUnit.MICROS)));
    }

    @Transactional
    public void saveAllResultsAndFlush(Iterable<SensitivityResultEntity> results) {
        sensitivityResultRepository.saveAllAndFlush(results);
    }

    @Transactional
    public void saveAllContingencyResultsAndFlush(Iterable<ContingencyResultEntity> contingencyResultEntities) {
        contingencyResultRepository.saveAllAndFlush(contingencyResultEntities);
    }

    @Transactional
    public void writeSensitivityValues(UUID resultUuid, List<SensitivityValue> sensitivityValues) {
        AnalysisResultEntity analysisResult = analysisResultRepository.findByResultUuid(resultUuid);
        var rawSensitivityResults = sensitivityValues.stream().map(s -> new RawSensitivityResultEntity(
            s.getFactorIndex(),
            s.getValue(),
            s.getFunctionReference(),
            analysisResult
        )).collect(Collectors.toSet());
        rawSensitivityResultRepository.saveAllAndFlush(rawSensitivityResults);
    }

    @Transactional
    public void writeContingenciesStatus(UUID resultUuid, List<ContingencyResult> contingencies) {
        contingencies.forEach(c -> {
            AnalysisResultEntity analysisResult = analysisResultRepository.findByResultUuid(resultUuid);
            ContingencyResultEntity contingencyResult = contingencyResultRepository.findByAnalysisResultAndIndex(analysisResult, c.contingencyIndex());
            Optional.ofNullable(contingencyResult).ifPresentOrElse(
                cr -> cr.setStatus(c.status()),
                () -> LOGGER.warn("Contingency with index {} for analysis '{}' was not found. Status will not be persisted.", c.contingencyIndex(), resultUuid)
            );
        });
    }

    @Transactional
    @Override
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        globalStatusRepository.deleteByResultUuid(resultUuid);
        sensitivityResultRepository.deleteAllPostContingenciesByAnalysisResultUuid(resultUuid);
        sensitivityResultRepository.deleteAllByAnalysisResultUuid(resultUuid);
        rawSensitivityResultRepository.deleteAllByAnalysisResultUuid(resultUuid);
        contingencyResultRepository.deleteAllByAnalysisResultUuid(resultUuid);
        analysisResultRepository.deleteById(resultUuid);
        LOGGER.info("Sensitivity analysis result '{}' has been deleted in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
    }

    @Transactional
    @Override
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        sensitivityResultRepository.deleteAllPostContingencies();
        sensitivityResultRepository.deleteAll();
        rawSensitivityResultRepository.deleteAll();
        contingencyResultRepository.deleteAll();
        analysisResultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    @Override
    public SensitivityAnalysisStatus findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return SensitivityAnalysisStatus.valueOf(globalEntity.getStatus());
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
            SensitivityResultSpecification.postContingencies(sas,
                selector.getFunctionType(),
                selector.getFunctionIds(),
                selector.getVariableIds(),
                selector.getContingencyIds()) :
            SensitivityResultSpecification.preContingency(sas,
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

        List<? extends SensitivityOfTo> sensitivities = selector.getTabSelection() != ResultTab.N_K ?
            sensitivityEntities
                .stream()
                .map(sensitivityEntity -> (SensitivityOfTo) SensitivityOfTo.builder()
                    .funcId(sensitivityEntity.getFunctionId())
                    .varId(sensitivityEntity.getVariableId())
                    .varIsAFilter(sensitivityEntity.isVariableSet())
                    .value(sensitivityEntity.getRawSensitivityResult().getValue())
                    .functionReference(sensitivityEntity.getRawSensitivityResult().getFunctionReference())
                    .build())
                .toList()
            : sensitivityEntities
                .stream()
                .map(sensitivityResultEntity -> SensitivityWithContingency.builder()
                    .funcId(sensitivityResultEntity.getFunctionId())
                    .varId(sensitivityResultEntity.getVariableId())
                    .varIsAFilter(sensitivityResultEntity.isVariableSet())
                    .contingencyId(sensitivityResultEntity.getContingencyResult().getContingencyId())
                    .value(sensitivityResultEntity.getPreContingencySensitivityResult().getRawSensitivityResult().getValue())
                    .functionReference(sensitivityResultEntity.getPreContingencySensitivityResult().getRawSensitivityResult().getFunctionReference())
                    .valueAfter(sensitivityResultEntity.getRawSensitivityResult().getValue())
                    .functionReferenceAfter(sensitivityResultEntity.getRawSensitivityResult().getFunctionReference())
                    .build())
                .toList();
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
            case FUNCTION -> "functionId";
            case SENSITIVITY, POST_SENSITIVITY -> "rawSensitivityResult.value";
            case REFERENCE, POST_REFERENCE -> "rawSensitivityResult.functionReference";
            case VARIABLE -> "variableId";
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

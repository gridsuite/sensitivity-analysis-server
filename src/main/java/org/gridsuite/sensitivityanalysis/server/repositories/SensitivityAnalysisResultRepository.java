/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;
import org.apache.commons.lang3.tuple.Pair;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        List<SensitivityEntity> sensitivities = result.getValues().stream()
            .map(v -> new SensitivityEntity(v.getFactorIndex(), v.getContingencyIndex(),
                v.getValue(), v.getFunctionReference()))
            .collect(Collectors.toList());
        //To avoid consistency issue we truncate the time to microseconds since postgres and h2 can only store a precision of microseconds
        return new AnalysisResultEntity(resultUuid, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS), factors, contingencies, sensitivities);
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

    interface SensitivityConsumer {
        void consume(SensitivityEntity sar, ContingencyEmbeddable c, SensitivityFactorEmbeddable f);
    }

    private int apply(Collection<String> funcIds,
                      Collection<String> varIds,
                      Collection<String> contingencyIds,
                      List<ContingencyEmbeddable> cs,
                      List<SensitivityFactorEmbeddable> fs,
                      SensitivityFunctionType funcType,
                      Set<String> allFunctionIds,
                      Set<String> allVariableIds,
                      Set<String> allContingencyIds,
                      SensitivityConsumer handle,
                      List<SensitivityEntity> sensitivityEntities,
                      AnalysisResultEntity sas) {
        AtomicInteger count = new AtomicInteger();
        for (SensitivityEntity sar : sensitivityEntities) {
            int fi = sar.getFactorIndex();
            SensitivityFactorEmbeddable f = fs.get(fi);

            if (f.getFunctionType() != funcType) {
                continue;
            }

            int ci = sar.getContingencyIndex();
            ContingencyEmbeddable c = ci < 0 ? null : cs.get(ci);
            if (ci < 0 != (allContingencyIds == null)) {
                continue;
            } else {
                count.addAndGet(1);
                allFunctionIds.add(f.getFunctionId());
                allVariableIds.add(f.getVariableId());
                if (allContingencyIds != null && c != null) {
                    allContingencyIds.add(c.getId());
                }
            }

            if (funcIds != null && !funcIds.contains(f.getFunctionId())) {
                continue;
            }
            if (varIds != null && !varIds.contains(f.getVariableId())) {
                continue;
            }
            if (c != null && contingencyIds != null && !contingencyIds.contains(c.getId())) {
                continue;
            }

            handle.consume(sar, c, f);
        }

        return count.get();
    }

    private boolean canOnlyReturnEmpty(Collection<String> funcIds, Collection<String> varIds, Collection<String> contingencies,
        SensitivityFunctionType sensitivityFunctionType,
        List<ContingencyEmbeddable> cs, List<SensitivityFactorEmbeddable> fs) {

        if (contingencies != null && cs.stream().noneMatch(c -> contingencies.contains(c.getId()))) {
            return true;
        }
        if (funcIds != null && fs.stream().noneMatch(f -> funcIds.contains(f.getFunctionId()))) {
            return true;
        }
        if (varIds != null && fs.stream().noneMatch(f -> varIds.contains(f.getVariableId()))) {
            return true;
        }
        if (sensitivityFunctionType != null && fs.stream().noneMatch(f -> f.getFunctionType() == sensitivityFunctionType)) {
            return true;
        }

        return false;
    }

    @Transactional(readOnly = true)
    public SensitivityRunQueryResult getRunResult(UUID resultUuid, ResultsSelector selector) {
        AnalysisResultEntity sas = analysisResultRepository.findByResultUuid(resultUuid);
        List<SensitivityEntity> sensitivityEntities = getSensitivityEntities(sas, selector);
        return getSensitivityRunQueryResult(selector, sas, sensitivityEntities, null);
    }

    private List<SensitivityEntity> getSensitivityEntities(AnalysisResultEntity sas, ResultsSelector selector) {
        if (selector.getPageSize() != null && selector.getPageNumber() != null) {
            List<ContingencyEmbeddable> cs = sas.getContingencies();
            List<SensitivityFactorEmbeddable> fs = sas.getFactors();
            Collection<String> funcIds = selector.getFunctionIds();
            Collection<String> varIds = selector.getVariableIds();
            var indexes = IntStream.range(0, fs.size())
                    .filter(idx -> {
                        var factor = fs.get(idx);
                        return factor.getFunctionType() == selector.getFunctionType() &&
                                (funcIds == null || funcIds.contains(factor.getFunctionId())) &&
                                (varIds == null || varIds.contains(factor.getVariableId()));
                    })
                    .boxed()
                    .toList();

            Pageable pageable = PageRequest.of(selector.getPageNumber(), selector.getPageSize());
            var sensiResults = selector.getIsJustBefore() ?
                    sensitivityRepository.findAllByResultAndFactorIndexInAndContingencyIndexIsLessThan(sas, indexes, 0, pageable) :
                    sensitivityRepository.findAllByResultAndFactorIndexInAndContingencyIndexIsGreaterThan(sas, indexes, 0, pageable);
            return sensiResults.getContent();
        }

        return sensitivityRepository.findByResult(sas);
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

        List<ContingencyEmbeddable> cs = sas.getContingencies();
        List<SensitivityFactorEmbeddable> fs = sas.getFactors();
        Collection<String> funcIds = selector.getFunctionIds();
        Collection<String> varIds = selector.getVariableIds();
        Collection<String> contingencyIds = selector.getContingencyIds();
        if (canOnlyReturnEmpty(funcIds, varIds, contingencyIds, selector.getFunctionType(), cs, fs)) {
            retBuilder.totalSensitivitiesCount(0);
            retBuilder.filteredSensitivitiesCount(0);
            retBuilder.sensitivities(List.of());
            return retBuilder.build();
        }

        Map<Pair<String, String>, SensitivityOfTo> before = new HashMap<>();
        Set<String> allFunctionIds = new TreeSet<>();
        Set<String> allVariableIds = new TreeSet<>();
        Set<String> allContingencyIds = new TreeSet<>();

        int count;
        if (selector.getIsJustBefore()) {
            //var sensiResults = sensitivityRepository.findAllByResultAndFactorIndexInAndContingencyIndexIsLessThan(sas, indexes, 0, pageable);
            count = apply(funcIds, varIds, null, cs, fs, selector.getFunctionType(),
                    allFunctionIds, allVariableIds, null, (sar, c, f) -> {
                        before.put(Pair.of(f.getFunctionId(), f.getVariableId()), SensitivityOfTo.builder()
                                .funcId(f.getFunctionId())
                                .varId(f.getVariableId())
                                .varIsAFilter(f.isVariableSet())
                                .value(sar.getValue())
                                .functionReference(sar.getFunctionReference())
                                .build());
                    }, sensitivityEntities, sas);
            Comparator<SensitivityOfTo> cmp = makeComparatorOfTo(selector.getSortKeysWithWeightAndDirection());
            List<SensitivityOfTo> befores = new ArrayList<>(before.values());
            if (cmp != null) {
                befores.sort(cmp);
            }
            complete(retBuilder, allFunctionIds, allVariableIds, null, count, befores);
        } else {
            List<SensitivityWithContingency> after = new ArrayList<>();
            //var sensiResults2 = sensitivityRepository.findAllByResultAndFactorIndexInAndContingencyIndexIsGreaterThan(sas, indexes, 0, pageable);
            count = apply(funcIds, varIds, contingencyIds, cs, fs, selector.getFunctionType(),
                allFunctionIds, allVariableIds, allContingencyIds, (sar, c, f) -> {
                    if (c == null) {
                        return;
                    }
                    var found = IntStream.range(0, fs.size())
                            .filter(i -> {
                                var factorEmbeddable = fs.get(i);
                                return Objects.equals(factorEmbeddable.getFunctionId(), f.getFunctionId()) &&
                                        Objects.equals(factorEmbeddable.getVariableId(), f.getVariableId()) &&
                                        Objects.equals(factorEmbeddable.getFunctionType(), selector.getFunctionType());
                            })
                            .boxed()
                            .collect(Collectors.toList());
                    var listTest = sensitivityRepository.findAllByResultAndContingencyIndexIsLessThan(sas, 0);
                    var sensiList = sensitivityRepository.findByResultAndFactorIndexInAndContingencyIndexIsLessThan(sas, found, 0);
                    var sensi = sensiList.size() == 0 ? null : sensiList.get(0);
                    SensitivityOfTo sensitivityOfTo = null;
                    if (sensi != null) {
                        SensitivityFactorEmbeddable embeddable = fs.get(sensi.getFactorIndex());
                        sensitivityOfTo = SensitivityOfTo.builder()
                                .funcId(embeddable.getFunctionId())
                                .varId(embeddable.getVariableId())
                                .varIsAFilter(embeddable.isVariableSet())
                                .value(sensi.getValue())
                                .functionReference(sensi.getFunctionReference())
                                .build();
                    }
                    //SensitivityOfTo b = before.get(Pair.of(f.getFunctionId(), f.getVariableId()));
                    SensitivityWithContingency r = SensitivityWithContingency.toBuilder(
                            sensitivityOfTo, f.getFunctionId(), f.getVariableId())
                        .varIsAFilter(f.isVariableSet())
                        .valueAfter(sar.getValue())
                        .functionReferenceAfter(sar.getFunctionReference())
                        .contingencyId(c.getId())
                        .build();
                    after.add(r);
                }, sensitivityEntities, sas);
            Comparator<SensitivityWithContingency> cmp = makeComparatorWith(selector.getSortKeysWithWeightAndDirection());
            if (cmp != null) {
                after.sort(cmp);
            }

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

    Comparator<SensitivityWithContingency> makeComparatorWith(ResultsSelector.SortKey key) {
        switch (key) {
            case FUNCTION:
                return Comparator.comparing(SensitivityWithContingency::getFuncId);
            case VARIABLE:
                return Comparator.comparing(SensitivityWithContingency::getVarId);
            case CONTINGENCY:
                return Comparator.comparing(SensitivityWithContingency::getContingencyId);
            case REFERENCE:
                return Comparator.comparing(SensitivityWithContingency::getFunctionReference);
            case SENSITIVITY:
                return Comparator.comparing(SensitivityWithContingency::getValue);
            case POST_REFERENCE:
                return Comparator.comparing(SensitivityWithContingency::getFunctionReferenceAfter);
            case POST_SENSITIVITY:
                return Comparator.comparing(SensitivityWithContingency::getValueAfter);
        }
        return null;
    }

    Comparator<SensitivityOfTo> makeComparatorOfTo(ResultsSelector.SortKey key) {
        switch (key) {
            case FUNCTION:
                return Comparator.comparing(SensitivityOfTo::getFuncId);
            case VARIABLE:
                return Comparator.comparing(SensitivityOfTo::getVarId);
            case REFERENCE:
                return Comparator.comparing(SensitivityOfTo::getFunctionReference);
            case SENSITIVITY:
                return Comparator.comparing(SensitivityOfTo::getValue);
            case POST_SENSITIVITY:
            case CONTINGENCY:
            case POST_REFERENCE:
                return null; // completing the switch appeases Sonar in a way but lowers coverage
        }
        return null;
    }

    Comparator<SensitivityWithContingency> makeComparatorWith(
        Map<ResultsSelector.SortKey, Integer> sortKeysWithWeightAndDirection) {
        if (sortKeysWithWeightAndDirection == null) {
            return null;
        }
        ArrayList<Pair<ResultsSelector.SortKey, Boolean>> sortedKeysToDirection = new ArrayList<>(Collections.nCopies(
            sortKeysWithWeightAndDirection.size(), null));
        sortKeysWithWeightAndDirection.forEach((k, s) -> {
            int index = Math.abs(s) - 1;
            sortedKeysToDirection.set(index, Pair.of(k, s > 0));
        });

        Optional<Comparator<SensitivityWithContingency>> ret = sortedKeysToDirection.stream()
            .filter(Objects::nonNull)
            .map(kd -> {
                Comparator<SensitivityWithContingency> direct = makeComparatorWith(kd.getKey());
                return kd.getValue() ? direct : direct.reversed();
            }).reduce(Comparator::thenComparing);

        return ret.orElse(null);
    }

    Comparator<SensitivityOfTo> makeComparatorOfTo(
        Map<ResultsSelector.SortKey, Integer> sortKeysWithWeightAndDirection) {
        if (sortKeysWithWeightAndDirection == null) {
            return null;
        }

        ArrayList<Pair<ResultsSelector.SortKey, Boolean>> sortedKeysToDirection = new ArrayList<>(Collections.nCopies(
            sortKeysWithWeightAndDirection.size(), null));
        sortKeysWithWeightAndDirection.forEach((k, s) -> {
            int index = Math.abs(s) - 1;
            sortedKeysToDirection.set(index, Pair.of(k, s > 0));
        });

        Optional<Comparator<SensitivityOfTo>> ret = sortedKeysToDirection.stream()
            .filter(Objects::nonNull)
            .map(kd -> {
                Comparator<SensitivityOfTo> direct = makeComparatorOfTo(kd.getKey());
                return kd.getValue() ? direct : direct.reversed();
            }).reduce(Comparator::thenComparing);

        return ret.orElse(null);
    }
}

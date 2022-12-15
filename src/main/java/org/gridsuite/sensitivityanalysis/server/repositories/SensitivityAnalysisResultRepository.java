/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import java.time.LocalDateTime;

import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.sensitivityanalysis.server.ResultsSelector;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityRunQueryResult;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyEmbeddable;
import org.gridsuite.sensitivityanalysis.server.entities.GlobalStatusEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityEmbeddable;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityFactorEmbeddable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public class SensitivityAnalysisResultRepository {

    private final GlobalStatusRepository globalStatusRepository;

    private final AnalysisResultRepository analysisResultRepository;

    public SensitivityAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                               AnalysisResultRepository analysisResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.analysisResultRepository = analysisResultRepository;
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
        List<SensitivityEmbeddable> sensitivities = result.getValues().stream()
            .map(v -> new SensitivityEmbeddable(v.getFactorIndex(), v.getContingencyIndex(),
                v.getValue(), v.getFunctionReference()))
            .collect(Collectors.toList());
        return new AnalysisResultEntity(resultUuid, LocalDateTime.now(), factors, contingencies, sensitivities);
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
        void consume(SensitivityEmbeddable sar, ContingencyEmbeddable c, SensitivityFactorEmbeddable f);
    }

    private int apply(AnalysisResultEntity sas, Collection<String> funcIds, Collection<String> varIds, Collection<String> contingencyIds,
        List<ContingencyEmbeddable> cs, List<SensitivityFactorEmbeddable> fs, SensitivityFunctionType funcType, boolean beforeOverAfter,
        Set<String> allFunctionIds, Set<String> allVariableIds, Set<String> allContingencyIds, SensitivityConsumer handle) {
        int count = 0;
        for (SensitivityEmbeddable sar : sas.getSensitivities()) {
            int fi = sar.getFactorIndex();
            SensitivityFactorEmbeddable f = fs.get(fi);

            int ci = sar.getContingencyIndex();
            ContingencyEmbeddable c = ci < 0 ? null : cs.get(ci);
            if ((ci < 0) == beforeOverAfter && f.getFunctionType() == funcType) {
                count += 1;
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

        return count;
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

    public SensitivityRunQueryResult getRunResult(UUID resultUuid, ResultsSelector selector) {
        AnalysisResultEntity sas = analysisResultRepository.findByResultUuid(resultUuid);
        if (sas == null) {
            return null;
        }

        SensitivityRunQueryResult.SensitivityRunQueryResultBuilder retBuilder = SensitivityRunQueryResult.builder()
            .isJustBefore(selector.getIsJustBefore())
            .functionType(selector.getFunctionType())
            .requestedChunkSize(selector.getChunkSize() == null ? 0 : selector.getChunkSize())
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
        int count = apply(sas, funcIds, varIds, null, cs, fs, selector.getFunctionType(), selector.getIsJustBefore(),
            allFunctionIds, allVariableIds, null, (sar, c, f) -> {
                if (c != null) {
                    return;
                }
                before.put(Pair.of(f.getFunctionId(), f.getVariableId()), SensitivityOfTo.builder()
                    .funcId(f.getFunctionId())
                    .varId(f.getVariableId())
                    .varIsAFilter(f.isVariableSet())
                    .value(sar.getValue())
                    .functionReference(sar.getFunctionReference())
                    .build());
            });

        if (selector.getIsJustBefore()) {
            Comparator<SensitivityOfTo> cmp = makeComparatorOfTo(selector.getSortKeysWithWeightAndDirection());
            List<SensitivityOfTo> befores = new ArrayList<>(before.values());
            if (cmp != null) {
                befores.sort(cmp);
            }
            complete(retBuilder, allFunctionIds, allVariableIds, null, count, selector, befores);
            return retBuilder.build();
        } else {
            List<SensitivityWithContingency> after = new ArrayList<>();
            count = apply(sas, funcIds, varIds, contingencyIds, cs, fs, selector.getFunctionType(), false,
                allFunctionIds, allVariableIds, allContingencyIds, (sar, c, f) -> {
                    if (c == null) {
                        return;
                    }
                    SensitivityOfTo b = before.get(Pair.of(f.getFunctionId(), f.getVariableId()));
                    SensitivityWithContingency r = SensitivityWithContingency.toBuilder(b)
                        .varIsAFilter(f.isVariableSet())
                        .valueAfter(sar.getValue())
                        .functionReferenceAfter(sar.getFunctionReference())
                        .contingencyId(c.getId())
                        .build();
                    after.add(r);
                });
            Comparator<SensitivityWithContingency> cmp = makeComparatorWith(selector.getSortKeysWithWeightAndDirection());
            if (cmp != null) {
                after.sort(cmp);
            }

            complete(retBuilder, allFunctionIds, allVariableIds, allContingencyIds, count, selector, after);
            return retBuilder.build();
        }
    }

    private void complete(SensitivityRunQueryResult.SensitivityRunQueryResultBuilder retBuilder,
        Collection<String> allFunctionIds,
        Collection<String> allVariableIds,
        Collection<String> allContingencyIds,
        int count,
        ResultsSelector selector, List<? extends SensitivityOfTo> filtered

    ) {
        retBuilder.allFunctionIds(allFunctionIds.stream().sorted().collect(Collectors.toList()));
        retBuilder.allVariableIds(allVariableIds.stream().sorted().collect(Collectors.toList()));
        if (allContingencyIds != null) {
            retBuilder.allContingencyIds(allContingencyIds.stream().sorted().collect(Collectors.toList()));
        }
        retBuilder.filteredSensitivitiesCount(filtered.size());
        retBuilder.sensitivities(chunkIt(selector, filtered));
        retBuilder.totalSensitivitiesCount(count);
    }

    private static <T extends SensitivityOfTo> List<T> chunkIt(ResultsSelector selector, List<T> befores) {
        Integer byRunOf = selector.getChunkSize();
        if (byRunOf != null && byRunOf > 0) {
            int offset = selector.getOffset() == null ? 0 : selector.getOffset();
            int start = Math.max(offset, 0);
            int overEnd = Math.min(offset + byRunOf, befores.size());
            return befores.subList(start, overEnd);
        }
        return befores;
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

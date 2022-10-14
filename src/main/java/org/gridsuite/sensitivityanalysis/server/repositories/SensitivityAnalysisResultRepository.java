/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import java.io.UncheckedIOException;
import java.time.LocalDateTime;

import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.sensitivityanalysis.server.RestTemplateConfig;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityOfTo;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityWithContingency;
import org.gridsuite.sensitivityanalysis.server.entities.AnalysisResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ContingencyEmbeddable;
import org.gridsuite.sensitivityanalysis.server.entities.GlobalStatusEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ResultEntity;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityEmbeddable;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityFactorP;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public class SensitivityAnalysisResultRepository {

    public static final double MINIMUM_SENSITIVITY = 0.1;
    private GlobalStatusRepository globalStatusRepository;

    private final ResultRepository resultRepository;
    private final AnalysisResultRepository analysisResultRepository;

    private final ObjectMapper objectMapper = RestTemplateConfig.objectMapper();

    public SensitivityAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                               ResultRepository resultRepository,
                                               AnalysisResultRepository analysisResultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
        this.analysisResultRepository = analysisResultRepository;
    }

    private static ResultEntity toResultEntity(UUID resultUuid, String result) {
        return new ResultEntity(resultUuid, result);
    }

    private static AnalysisResultEntity toAnalysisResultEntity(UUID resultUuid, SensitivityAnalysisResult result) {
        List<SensitivityFactorP> factors = result.getFactors().stream().map(f ->
            new SensitivityFactorP(f.getFunctionType(), f.getFunctionId(),
                f.getVariableType(), f.getVariableId(), f.isVariableSet(),
                f.getContingencyContext().getContextType(), f.getContingencyContext().getContingencyId()))
            .collect(Collectors.toList());
        List<ContingencyEmbeddable> contingencies = result.getContingencyStatuses().stream().map(cs ->
                new ContingencyEmbeddable(cs.getContingencyId(), cs.getStatus()))
            .collect(Collectors.toList());
        List<SensitivityEmbeddable> sensitivities = result.getValues().stream()
            .filter(v -> v.getValue() >= SensitivityAnalysisResultRepository.MINIMUM_SENSITIVITY)
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

        try {
            resultRepository.save(toResultEntity(resultUuid, result != null ? objectMapper.writeValueAsString(result) : null));
            if (result != null) {
                analysisResultRepository.save(toAnalysisResultEntity(resultUuid, result));
            }
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
        analysisResultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        resultRepository.deleteAll();
        analysisResultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public String find(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        ResultEntity resultEntity = resultRepository.findByResultUuid(resultUuid);
        return resultEntity != null ? resultEntity.getResult() : null;
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
        void consume(SensitivityEmbeddable sar, ContingencyEmbeddable c, SensitivityFactorP f);
    }

    private void apply(AnalysisResultEntity sas, Collection<String> funcIds, Collection<String> varIds,
        List<ContingencyEmbeddable> cs, List<SensitivityFactorP> fs,
        SensitivityConsumer handle) {
        for (SensitivityEmbeddable sar : sas.getSensitivities()) {
            int fi = sar.getFactorIndex();
            SensitivityFactorP f = fs.get(fi);
            if (varIds != null && !varIds.contains(f.getVariableId())) {
                continue;
            }
            if (funcIds != null && !funcIds.contains(f.getFunctionId())) {
                continue;
            }

            int ci = sar.getContingencyIndex();
            ContingencyEmbeddable c = ci < 0 ? null : cs.get(ci);

            handle.consume(sar, c, f);
        }
    }

    public List<SensitivityOfTo> getSensitivities(UUID resultUuid, Collection<String> funcIds, Collection<String> varIds,
        SensitivityFunctionType sensitivityFunctionType) {

        List<SensitivityOfTo> ret = new ArrayList<>();

        AnalysisResultEntity sas = analysisResultRepository.findByResultUuid(resultUuid);
        if (sas == null) {
            return null;
        }

        List<ContingencyEmbeddable> cs = sas.getContingencies();
        List<SensitivityFactorP> fs = sas.getFactors();

        if (funcIds != null && fs.stream().noneMatch(f -> funcIds.contains(f.getFunctionId()))) {
            return ret;
        }
        if (varIds != null && fs.stream().noneMatch(f -> varIds.contains(f.getVariableId()))) {
            return ret;
        }
        if (sensitivityFunctionType != null && fs.stream().noneMatch(f -> f.getFunctionType() == sensitivityFunctionType)) {
            return ret;
        }

        apply(sas, funcIds, varIds, cs, fs, (sar, c, f) -> ret.add(SensitivityOfTo.builder()
            .funcId(f.getFunctionId())
            .varId(f.getVariableId())
            .varIsAFilter(f.isVariableSet())
            .value(sar.getValue())
            .functionReference(sar.getFunctionReference())
            .build()));
        return ret;
    }

    public List<SensitivityWithContingency> getSensitivities(UUID resultUuid,
        Collection<String> funcIds, Collection<String> varIds, Collection<String> contingencies,
        SensitivityFunctionType sensitivityFunctionType) {

        List<SensitivityWithContingency> ret = new ArrayList<>();
        AnalysisResultEntity sas = analysisResultRepository.findByResultUuid(resultUuid);
        if (sas == null) {
            return null;
        }

        List<ContingencyEmbeddable> cs = sas.getContingencies();
        List<SensitivityFactorP> fs = sas.getFactors();

        if (contingencies != null && cs.stream().noneMatch(c -> contingencies.contains(c.getId()))) {
            return ret;
        }
        if (funcIds != null && fs.stream().noneMatch(f -> funcIds.contains(f.getFunctionId()))) {
            return ret;
        }
        if (varIds != null && fs.stream().noneMatch(f -> varIds.contains(f.getVariableId()))) {
            return ret;
        }
        if (sensitivityFunctionType != null && fs.stream().noneMatch(f -> f.getFunctionType() == sensitivityFunctionType)) {
            return ret;
        }

        Map<Pair<String, String>, SensitivityOfTo> before = new HashMap<>();
        apply(sas, funcIds, varIds, cs, fs, (sar, c, f) -> {
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

        apply(sas, funcIds, varIds, cs, fs, (sar, c, f) -> {
            if (c == null) {
                return;
            }
            SensitivityOfTo b = before.get(Pair.of(f.getFunctionId(), f.getVariableId()));
            SensitivityWithContingency r = SensitivityWithContingency.toBuilder(b)
                .funcId(f.getFunctionId())
                .varId(f.getVariableId())
                .varIsAFilter(f.isVariableSet())
                .valueAfter(sar.getValue())
                .functionReferenceAfter(sar.getFunctionReference())
                .contingencyId(c.getId())
                .build();
            ret.add(r);
        });

        return ret;
    }
}

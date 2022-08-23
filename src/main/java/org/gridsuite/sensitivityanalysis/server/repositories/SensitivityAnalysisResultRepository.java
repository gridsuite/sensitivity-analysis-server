/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import org.gridsuite.sensitivityanalysis.server.entities.GlobalStatusEntity;
import org.gridsuite.sensitivityanalysis.server.entities.ResultEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Repository
public class SensitivityAnalysisResultRepository {

    private GlobalStatusRepository globalStatusRepository;

    private ResultRepository resultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SensitivityAnalysisResultRepository(GlobalStatusRepository globalStatusRepository,
                                               ResultRepository resultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
    }

    private static ResultEntity toResultEntity(UUID resultUuid, String result) {
        return new ResultEntity(resultUuid, result);
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        System.out.println("******** insertStatus : resultUuid = " + resultUuids.get(0) + " status = " + status);
        globalStatusRepository.saveAll(resultUuids.stream()
            .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void insert(UUID resultUuid, SensitivityAnalysisResult result) {
        Objects.requireNonNull(resultUuid);
        Objects.requireNonNull(result);

        try {
            System.out.println("******** insert : resultUuid = " + resultUuid + " result = " + objectMapper.writeValueAsString(result));
            resultRepository.save(toResultEntity(resultUuid, objectMapper.writeValueAsString(result)));
        } catch (JsonProcessingException e) {
            System.out.println("******** insert : exception !!!!");
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        resultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public SensitivityAnalysisResult find(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        System.out.println("******** find : resultUuid = " + resultUuid);
        ResultEntity resultEntity = resultRepository.findByResultUuid(resultUuid);
        if (resultEntity != null) {
            try {
                return objectMapper.readValue(resultEntity.getResult(), SensitivityAnalysisResult.class);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            throw new PowsyblException("Sensitivity analysis result not found !!");
        }
    }

    @Transactional(readOnly = true)
    public String findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return globalEntity.getStatus();
        } else {
            System.out.println("********** find status : not found !!!!!!");
            return null;
        }
    }
}

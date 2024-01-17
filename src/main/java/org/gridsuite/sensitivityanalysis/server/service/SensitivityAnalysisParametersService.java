/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisParametersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
@Service
public class SensitivityAnalysisParametersService {

    private final SensitivityAnalysisParametersRepository sensitivityAnalysisParametersRepository;

    public SensitivityAnalysisParametersService(SensitivityAnalysisParametersRepository sensitivityAnalysisParametersRepository) {
        this.sensitivityAnalysisParametersRepository = sensitivityAnalysisParametersRepository;
    }

    public UUID createDefaultParameters() {
        return createParameters(SensitivityAnalysisParametersInfos.builder().build());
    }

    public UUID createParameters(SensitivityAnalysisParametersInfos parametersInfos) {
        return sensitivityAnalysisParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    public Optional<UUID> duplicateParameters(UUID sourceParametersId) {
        return sensitivityAnalysisParametersRepository.findById(sourceParametersId)
            .map(SensitivityAnalysisParametersEntity::copy)
            .map(entity -> {
                sensitivityAnalysisParametersRepository.save(entity);
                return Optional.of(entity.getId());
            })
            .orElse(Optional.empty());
    }

    public Optional<SensitivityAnalysisParametersInfos> getParameters(UUID parametersUuid) {
        return sensitivityAnalysisParametersRepository.findById(parametersUuid).map(SensitivityAnalysisParametersEntity::toInfos);
    }

    public List<SensitivityAnalysisParametersInfos> getAllParameters() {
        return sensitivityAnalysisParametersRepository.findAll().stream().map(SensitivityAnalysisParametersEntity::toInfos).toList();
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, SensitivityAnalysisParametersInfos parametersInfos) {
        sensitivityAnalysisParametersRepository.findById(parametersUuid).orElseThrow().update(parametersInfos);
    }

    public void deleteParameters(UUID parametersUuid) {
        sensitivityAnalysisParametersRepository.deleteById(parametersUuid);
    }

    public SensitivityAnalysisInputData buildInputData(UUID parametersUuid, LoadFlowParametersValues loadFlowParametersValues) {

        Objects.requireNonNull(loadFlowParametersValues);

        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersUuid != null ?
            sensitivityAnalysisParametersRepository.findById(parametersUuid)
            .map(SensitivityAnalysisParametersEntity::toInfos)
            .orElse(SensitivityAnalysisParametersInfos.builder().build())
            :
            SensitivityAnalysisParametersInfos.builder().build();

        SensitivityAnalysisParameters sensitivityAnalysisParameters = SensitivityAnalysisParameters.load();
        sensitivityAnalysisParameters.setAngleFlowSensitivityValueThreshold(sensitivityAnalysisParametersInfos.getAngleFlowSensitivityValueThreshold());
        sensitivityAnalysisParameters.setFlowFlowSensitivityValueThreshold(sensitivityAnalysisParametersInfos.getFlowFlowSensitivityValueThreshold());
        sensitivityAnalysisParameters.setFlowVoltageSensitivityValueThreshold(sensitivityAnalysisParametersInfos.getFlowVoltageSensitivityValueThreshold());
        sensitivityAnalysisParameters.setLoadFlowParameters(loadFlowParametersValues.getCommonParameters());

        SensitivityAnalysisInputData sensitivityAnalysisInputData = new SensitivityAnalysisInputData();
        sensitivityAnalysisInputData.setParameters(sensitivityAnalysisParameters);
        sensitivityAnalysisInputData.setLoadFlowSpecificParameters(loadFlowParametersValues.getSpecificParameters());
        sensitivityAnalysisInputData.setSensitivityInjectionsSets(sensitivityAnalysisParametersInfos.getSensitivityInjectionsSet()
            .stream()
            .filter(SensitivityInjectionsSet::isActivated)
            .collect(Collectors.toList()));
        sensitivityAnalysisInputData.setSensitivityInjections(sensitivityAnalysisParametersInfos.getSensitivityInjection()
            .stream()
            .filter(SensitivityInjection::isActivated)
            .collect(Collectors.toList()));
        sensitivityAnalysisInputData.setSensitivityHVDCs(sensitivityAnalysisParametersInfos.getSensitivityHVDC()
            .stream()
            .filter(SensitivityHVDC::isActivated)
            .collect(Collectors.toList()));
        sensitivityAnalysisInputData.setSensitivityPSTs(sensitivityAnalysisParametersInfos.getSensitivityPST()
            .stream()
            .filter(SensitivityPST::isActivated)
            .collect(Collectors.toList()));
        sensitivityAnalysisInputData.setSensitivityNodes(sensitivityAnalysisParametersInfos.getSensitivityNodes()
            .stream()
            .filter(SensitivityNodes::isActivated)
            .collect(Collectors.toList()));

        return sensitivityAnalysisInputData;
    }
}

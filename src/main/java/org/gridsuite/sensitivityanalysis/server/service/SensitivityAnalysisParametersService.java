/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.ws.commons.computation.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisParametersRepository;
import org.gridsuite.sensitivityanalysis.server.service.nonevacuatedenergy.NonEvacuatedEnergyRunContext;
import org.springframework.beans.factory.annotation.Value;
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

    private final LoadFlowService loadFlowService;

    private final String defaultProvider;

    public SensitivityAnalysisParametersService(@Value("${sensitivity-analysis.default-provider}") String defaultProvider,
                                                SensitivityAnalysisParametersRepository sensitivityAnalysisParametersRepository,
                                                LoadFlowService loadFlowService) {
        this.defaultProvider = defaultProvider;
        this.sensitivityAnalysisParametersRepository = sensitivityAnalysisParametersRepository;
        this.loadFlowService = loadFlowService;
    }

    public UUID createDefaultParameters() {
        return createParameters(getDefauSensitivityAnalysisParametersInfos());
    }

    public UUID createParameters(SensitivityAnalysisParametersInfos parametersInfos) {
        return sensitivityAnalysisParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    @Transactional
    public Optional<UUID> duplicateParameters(UUID sourceParametersId) {
        return sensitivityAnalysisParametersRepository.findById(sourceParametersId)
            .map(SensitivityAnalysisParametersEntity::copy)
            .map(sensitivityAnalysisParametersRepository::save)
            .map(SensitivityAnalysisParametersEntity::getId);
    }

    public Optional<SensitivityAnalysisParametersInfos> getParameters(UUID parametersUuid) {
        return sensitivityAnalysisParametersRepository.findById(parametersUuid).map(SensitivityAnalysisParametersEntity::toInfos);
    }

    public List<SensitivityAnalysisParametersInfos> getAllParameters() {
        return sensitivityAnalysisParametersRepository.findAll().stream().map(SensitivityAnalysisParametersEntity::toInfos).toList();
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, SensitivityAnalysisParametersInfos parametersInfos) {
        SensitivityAnalysisParametersEntity sensitivityAnalysisParametersEntity = sensitivityAnalysisParametersRepository.findById(parametersUuid).orElseThrow();
        //if the parameters is null it means it's a reset to defaultValues
        if (parametersInfos == null) {
            sensitivityAnalysisParametersEntity.update(getDefauSensitivityAnalysisParametersInfos());
        } else {
            sensitivityAnalysisParametersEntity.update(parametersInfos);
        }
    }

    public void deleteParameters(UUID parametersUuid) {
        sensitivityAnalysisParametersRepository.deleteById(parametersUuid);
    }

    public SensitivityAnalysisInputData buildInputData(SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos, UUID loadFlowParametersUuid) {

        Objects.requireNonNull(loadFlowParametersUuid);

        LoadFlowParametersValues loadFlowParametersValues = loadFlowService.getLoadFlowParameters(loadFlowParametersUuid, sensitivityAnalysisParametersInfos.getProvider());
        SensitivityAnalysisParameters sensitivityAnalysisParameters = SensitivityAnalysisParameters.load();
        sensitivityAnalysisParameters.setAngleFlowSensitivityValueThreshold(sensitivityAnalysisParametersInfos.getAngleFlowSensitivityValueThreshold());
        sensitivityAnalysisParameters.setFlowFlowSensitivityValueThreshold(sensitivityAnalysisParametersInfos.getFlowFlowSensitivityValueThreshold());
        sensitivityAnalysisParameters.setFlowVoltageSensitivityValueThreshold(sensitivityAnalysisParametersInfos.getFlowVoltageSensitivityValueThreshold());
        sensitivityAnalysisParameters.setLoadFlowParameters(loadFlowParametersValues.commonParameters());

        SensitivityAnalysisInputData sensitivityAnalysisInputData = new SensitivityAnalysisInputData();
        sensitivityAnalysisInputData.setParameters(sensitivityAnalysisParameters);
        sensitivityAnalysisInputData.setLoadFlowSpecificParameters(loadFlowParametersValues.specificParameters());
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

    public SensitivityAnalysisParametersInfos getDefauSensitivityAnalysisParametersInfos() {
        return SensitivityAnalysisParametersInfos.builder().provider(defaultProvider).build();
    }

    public NonEvacuatedEnergyRunContext createNonEvacuatedEnergyRunContext(UUID networkUuid, String variantId,
                                                          String receiver,
                                                          ReportInfos reportInfos,
                                                          String userId,
                                                          String provider,
                                                          UUID loadFlowParametersUuid,
                                                          NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData) {
        NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext = new NonEvacuatedEnergyRunContext(networkUuid,
                variantId,
                nonEvacuatedEnergyInputData,
                receiver,
                provider != null ? provider : "default-provider", // TODO : remove test on provider null when fix in powsybl-ws-commons will handle null provider
                reportInfos,
                userId);

        // complete nonEvacuatedEnergyRunContext with loadFlowParameters
        completeNonEvacuatedEnergyRunContext(nonEvacuatedEnergyRunContext, loadFlowParametersUuid);

        return nonEvacuatedEnergyRunContext;
    }

    private void completeNonEvacuatedEnergyRunContext(NonEvacuatedEnergyRunContext nonEvacuatedEnergyRunContext, UUID loadFlowParametersUuid) {
        LoadFlowParametersValues loadFlowParametersValues = loadFlowService.getLoadFlowParameters(loadFlowParametersUuid, nonEvacuatedEnergyRunContext.getProvider());
        nonEvacuatedEnergyRunContext.getNonEvacuatedEnergyInputData().setLoadFlowSpecificParameters(loadFlowParametersValues.specificParameters());
        nonEvacuatedEnergyRunContext.getNonEvacuatedEnergyInputData().getParameters().setLoadFlowParameters(loadFlowParametersValues.commonParameters());
    }

    public SensitivityAnalysisRunContext createRunContext(UUID networkUuid, String variantId,
                                                          String receiver,
                                                          ReportInfos reportInfos,
                                                          String userId,
                                                          UUID parametersUuid,
                                                          UUID loadFlowParametersUuid) {
        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersUuid != null
                ? getParameters(parametersUuid)
                .orElse(getDefauSensitivityAnalysisParametersInfos())
                : getDefauSensitivityAnalysisParametersInfos();

        if (sensitivityAnalysisParametersInfos.getProvider() == null) {
            sensitivityAnalysisParametersInfos.setProvider(getDefauSensitivityAnalysisParametersInfos().getProvider());
        }

        SensitivityAnalysisInputData inputData = buildInputData(sensitivityAnalysisParametersInfos, loadFlowParametersUuid);

        return new SensitivityAnalysisRunContext(networkUuid,
                variantId,
                receiver,
                reportInfos,
                userId,
                sensitivityAnalysisParametersInfos.getProvider(),
                inputData);
    }
}

/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;
import org.gridsuite.sensitivityanalysis.server.repositories.SensitivityAnalysisParametersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
@Service
public class SensitivityAnalysisParametersService {

    private final SensitivityAnalysisParametersRepository sensitivityAnalysisParametersRepository;

    private final LoadFlowService loadFlowService;

    private final DirectoryService directoryService;

    private final String defaultProvider;

    public SensitivityAnalysisParametersService(@Value("${sensitivity-analysis.default-provider}") String defaultProvider,
                                                SensitivityAnalysisParametersRepository sensitivityAnalysisParametersRepository,
                                                LoadFlowService loadFlowService, DirectoryService directoryService) {
        this.defaultProvider = defaultProvider;
        this.sensitivityAnalysisParametersRepository = sensitivityAnalysisParametersRepository;
        this.loadFlowService = loadFlowService;
        this.directoryService = directoryService;
    }

    public UUID createDefaultParameters() {
        return createParameters(getDefauSensitivityAnalysisParametersInfos());
    }

    public UUID createParameters(SensitivityAnalysisParametersInfos parametersInfos) {
        return sensitivityAnalysisParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    @Transactional
    public Optional<UUID> duplicateParameters(UUID sourceParametersId, String userId) {
        return sensitivityAnalysisParametersRepository.findById(sourceParametersId)
            .map(entity -> copy(entity, userId))
            .map(sensitivityAnalysisParametersRepository::save)
            .map(SensitivityAnalysisParametersEntity::getId);
    }

    /**
     * Copy used to duplicate in DB with .save.
     * The ID is changed. The date is updated.
     *
     * @return a copy of the entity
     */
    public SensitivityAnalysisParametersEntity copy(SensitivityAnalysisParametersEntity entity, String userId) {
        return getSensitivityAnalysisParametersInfos(entity, userId).toEntity();
    }

    @Transactional(readOnly = true)
    public Optional<SensitivityAnalysisParametersInfos> getParameters(UUID parametersUuid, String userId) {
        return getParameters(sensitivityAnalysisParametersRepository.findById(parametersUuid), userId);
    }

    private Optional<SensitivityAnalysisParametersInfos> getParameters(Optional<SensitivityAnalysisParametersEntity> parametersEntity, String userId) {
        return parametersEntity.map(entity -> getSensitivityAnalysisParametersInfos(entity, userId));
    }

    @Transactional(readOnly = true)
    public List<SensitivityAnalysisParametersInfos> getAllParameters(String userId) {
        return sensitivityAnalysisParametersRepository.findAll().stream()
                .map(entity -> getSensitivityAnalysisParametersInfos(entity, userId))
                .toList();
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

    @Transactional(readOnly = true)
    public SensitivityAnalysisRunContext createRunContext(UUID networkUuid, String variantId,
                                                          String receiver,
                                                          ReportInfos reportInfos,
                                                          String userId,
                                                          UUID parametersUuid,
                                                          UUID loadFlowParametersUuid) {
        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersInfos = parametersUuid != null
                ? getParameters(sensitivityAnalysisParametersRepository.findById(parametersUuid), userId)
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

    public SensitivityAnalysisParametersInfos getSensitivityAnalysisParametersInfos(SensitivityAnalysisParametersEntity entity, String userId) {
        List<SensitivityInjectionsSet> sensiInjectionsSets = new ArrayList<>();
        entity.getSensitivityInjectionsSets().stream().map(sensitivityInjectionsSet -> new SensitivityInjectionsSet(
                fromEmbeddableContainerEquipments(sensitivityInjectionsSet.getMonitoredBranch(), userId),
                fromEmbeddableContainerEquipments(sensitivityInjectionsSet.getInjections(), userId),
                sensitivityInjectionsSet.getDistributionType(),
                fromEmbeddableContainerEquipments(sensitivityInjectionsSet.getContingencies(), userId),
                sensitivityInjectionsSet.isActivated()
        )).forEach(sensiInjectionsSets::add);

        List<SensitivityInjection> sensiInjections = new ArrayList<>();
        entity.getSensitivityInjections().stream().map(sensitivityInjection -> new SensitivityInjection(
                fromEmbeddableContainerEquipments(sensitivityInjection.getMonitoredBranch(), userId),
                fromEmbeddableContainerEquipments(sensitivityInjection.getInjections(), userId),
                fromEmbeddableContainerEquipments(sensitivityInjection.getContingencies(), userId),
                sensitivityInjection.isActivated()
        )).forEach(sensiInjections::add);

        List<SensitivityHVDC> sensiHvdcs = new ArrayList<>();
        entity.getSensitivityHVDCs().stream().map(sensitivityHvdc -> new SensitivityHVDC(
                fromEmbeddableContainerEquipments(sensitivityHvdc.getMonitoredBranch(), userId),
                sensitivityHvdc.getSensitivityType(),
                fromEmbeddableContainerEquipments(sensitivityHvdc.getInjections(), userId),
                fromEmbeddableContainerEquipments(sensitivityHvdc.getContingencies(), userId),
                sensitivityHvdc.isActivated()
        )).forEach(sensiHvdcs::add);

        List<SensitivityPST> sensiPsts = new ArrayList<>();
        entity.getSensitivityPSTs().stream().map(sensitivityPst -> new SensitivityPST(
                fromEmbeddableContainerEquipments(sensitivityPst.getMonitoredBranch(), userId),
                sensitivityPst.getSensitivityType(),
                fromEmbeddableContainerEquipments(sensitivityPst.getInjections(), userId),
                fromEmbeddableContainerEquipments(sensitivityPst.getContingencies(), userId),
                sensitivityPst.isActivated()
        )).forEach(sensiPsts::add);

        List<SensitivityNodes> sensiNodes = new ArrayList<>();
        entity.getSensitivityNodes().stream().map(sensitivityNode -> new SensitivityNodes(
                fromEmbeddableContainerEquipments(sensitivityNode.getMonitoredBranch(), userId),
                fromEmbeddableContainerEquipments(sensitivityNode.getInjections(), userId),
                fromEmbeddableContainerEquipments(sensitivityNode.getContingencies(), userId),
                sensitivityNode.isActivated()
        )).forEach(sensiNodes::add);

        return SensitivityAnalysisParametersInfos.builder()
                .uuid(entity.getId())
                .provider(entity.getProvider())
                .flowFlowSensitivityValueThreshold(entity.getFlowFlowSensitivityValueThreshold())
                .angleFlowSensitivityValueThreshold(entity.getAngleFlowSensitivityValueThreshold())
                .flowVoltageSensitivityValueThreshold(entity.getFlowVoltageSensitivityValueThreshold())
                .sensitivityInjectionsSet(sensiInjectionsSets)
                .sensitivityInjection(sensiInjections)
                .sensitivityHVDC(sensiHvdcs)
                .sensitivityPST(sensiPsts)
                .sensitivityNodes(sensiNodes)
                .build();
    }

    public List<EquipmentsContainer> fromEmbeddableContainerEquipments(List<UUID> containerIds, String userId) {
        if (containerIds == null) {
            return null;
        }
        Map<UUID, String> contingenciesInfos = directoryService.getElementNames(containerIds, userId);
        return containerIds.stream()
                .map(id -> new EquipmentsContainer(id, contingenciesInfos.get(id)))
                .toList();
    }
}

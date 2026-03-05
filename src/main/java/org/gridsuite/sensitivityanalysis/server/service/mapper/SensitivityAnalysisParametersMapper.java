/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service.mapper;

import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;
import org.gridsuite.sensitivityanalysis.server.entities.parameters.SensitivityAnalysisParametersEntity;
import org.gridsuite.sensitivityanalysis.server.service.DirectoryService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Service
public class SensitivityAnalysisParametersMapper {

    private final DirectoryService directoryService;

    public SensitivityAnalysisParametersMapper(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    public SensitivityAnalysisParametersInfos getSensitivityAnalysisParametersInfos(SensitivityAnalysisParametersEntity entity, String userId) {
        Map<UUID, String> allContainerNames = getAllContainerNames(entity, userId);

        List<SensitivityInjectionsSet> sensiInjectionsSets = entity.getSensitivityInjectionsSets().stream()
                .map(sensitivityInjectionsSet -> new SensitivityInjectionsSet(
                    toEquipmentContainerDTO(sensitivityInjectionsSet.getMonitoredBranch(), allContainerNames),
                    toEquipmentContainerDTO(sensitivityInjectionsSet.getInjections(), allContainerNames),
                    sensitivityInjectionsSet.getDistributionType(),
                    toEquipmentContainerDTO(sensitivityInjectionsSet.getContingencies(), allContainerNames),
                    sensitivityInjectionsSet.isActivated()))
                .toList();

        List<SensitivityInjection> sensiInjections = entity.getSensitivityInjections().stream()
                .map(sensitivityInjection -> new SensitivityInjection(
                    toEquipmentContainerDTO(sensitivityInjection.getMonitoredBranch(), allContainerNames),
                    toEquipmentContainerDTO(sensitivityInjection.getInjections(), allContainerNames),
                    toEquipmentContainerDTO(sensitivityInjection.getContingencies(), allContainerNames),
                    sensitivityInjection.isActivated()))
                .toList();

        List<SensitivityHVDC> sensiHvdcs = entity.getSensitivityHVDCs().stream()
                .map(sensitivityHvdc -> new SensitivityHVDC(
                    toEquipmentContainerDTO(sensitivityHvdc.getMonitoredBranch(), allContainerNames),
                    sensitivityHvdc.getSensitivityType(),
                    toEquipmentContainerDTO(sensitivityHvdc.getInjections(), allContainerNames),
                    toEquipmentContainerDTO(sensitivityHvdc.getContingencies(), allContainerNames),
                    sensitivityHvdc.isActivated()))
                .toList();

        List<SensitivityPST> sensiPsts = entity.getSensitivityPSTs().stream()
                .map(sensitivityPst -> new SensitivityPST(
                    toEquipmentContainerDTO(sensitivityPst.getMonitoredBranch(), allContainerNames),
                    sensitivityPst.getSensitivityType(),
                    toEquipmentContainerDTO(sensitivityPst.getInjections(), allContainerNames),
                    toEquipmentContainerDTO(sensitivityPst.getContingencies(), allContainerNames),
                    sensitivityPst.isActivated()))
                .toList();

        List<SensitivityNodes> sensiNodes = entity.getSensitivityNodes().stream()
                .map(sensitivityNode -> new SensitivityNodes(
                    toEquipmentContainerDTO(sensitivityNode.getMonitoredBranch(), allContainerNames),
                    toEquipmentContainerDTO(sensitivityNode.getInjections(), allContainerNames),
                    toEquipmentContainerDTO(sensitivityNode.getContingencies(), allContainerNames),
                    sensitivityNode.isActivated()))
                .toList();

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

    private List<EquipmentsContainer> toEquipmentContainerDTO(List<UUID> containerIds, Map<UUID, String> containerNames) {
        if (containerIds == null) {
            return null;
        }
        return containerIds.stream()
                .map(id -> new EquipmentsContainer(id, containerNames.get(id)))
                .toList();
    }

    private Map<UUID, String> getAllContainerNames(SensitivityAnalysisParametersEntity entity, String userId) {
        Set<UUID> allContainerIds = new HashSet<>();

        entity.getSensitivityInjectionsSets().forEach(sensitivityInjectionsSet -> {
            allContainerIds.addAll(sensitivityInjectionsSet.getMonitoredBranch());
            allContainerIds.addAll(sensitivityInjectionsSet.getInjections());
            allContainerIds.addAll(sensitivityInjectionsSet.getContingencies());
        });

        entity.getSensitivityInjections().forEach(sensitivityInjection -> {
            allContainerIds.addAll(sensitivityInjection.getMonitoredBranch());
            allContainerIds.addAll(sensitivityInjection.getInjections());
            allContainerIds.addAll(sensitivityInjection.getContingencies());
        });

        entity.getSensitivityHVDCs().forEach(sensitivityHvdc -> {
            allContainerIds.addAll(sensitivityHvdc.getMonitoredBranch());
            allContainerIds.addAll(sensitivityHvdc.getInjections());
            allContainerIds.addAll(sensitivityHvdc.getContingencies());
        });

        entity.getSensitivityPSTs().forEach(sensitivityPst -> {
            allContainerIds.addAll(sensitivityPst.getMonitoredBranch());
            allContainerIds.addAll(sensitivityPst.getInjections());
            allContainerIds.addAll(sensitivityPst.getContingencies());
        });

        entity.getSensitivityNodes().forEach(sensitivityNode -> {
            allContainerIds.addAll(sensitivityNode.getMonitoredBranch());
            allContainerIds.addAll(sensitivityNode.getInjections());
            allContainerIds.addAll(sensitivityNode.getContingencies());
        });

        return directoryService.getElementNames(allContainerIds, userId);
    }
}

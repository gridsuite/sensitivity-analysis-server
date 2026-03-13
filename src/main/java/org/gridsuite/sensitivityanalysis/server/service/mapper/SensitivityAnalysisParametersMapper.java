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
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Service
public class SensitivityAnalysisParametersMapper {

    public SensitivityAnalysisParametersInfos getSensitivityAnalysisParametersInfos(SensitivityAnalysisParametersEntity entity) {

        List<SensitivityInjectionsSet> sensiInjectionsSets = entity.getSensitivityInjectionsSets().stream()
                .map(sensitivityInjectionsSet -> new SensitivityInjectionsSet(
                    toEquipmentContainerDTO(sensitivityInjectionsSet.getMonitoredBranch()),
                    toEquipmentContainerDTO(sensitivityInjectionsSet.getInjections()),
                    sensitivityInjectionsSet.getDistributionType(),
                    toEquipmentContainerDTO(sensitivityInjectionsSet.getContingencies()),
                    sensitivityInjectionsSet.isActivated()))
                .toList();

        List<SensitivityInjection> sensiInjections = entity.getSensitivityInjections().stream()
                .map(sensitivityInjection -> new SensitivityInjection(
                    toEquipmentContainerDTO(sensitivityInjection.getMonitoredBranch()),
                    toEquipmentContainerDTO(sensitivityInjection.getInjections()),
                    toEquipmentContainerDTO(sensitivityInjection.getContingencies()),
                    sensitivityInjection.isActivated()))
                .toList();

        List<SensitivityHVDC> sensiHvdcs = entity.getSensitivityHVDCs().stream()
                .map(sensitivityHvdc -> new SensitivityHVDC(
                    toEquipmentContainerDTO(sensitivityHvdc.getMonitoredBranch()),
                    sensitivityHvdc.getSensitivityType(),
                    toEquipmentContainerDTO(sensitivityHvdc.getInjections()),
                    toEquipmentContainerDTO(sensitivityHvdc.getContingencies()),
                    sensitivityHvdc.isActivated()))
                .toList();

        List<SensitivityPST> sensiPsts = entity.getSensitivityPSTs().stream()
                .map(sensitivityPst -> new SensitivityPST(
                    toEquipmentContainerDTO(sensitivityPst.getMonitoredBranch()),
                    sensitivityPst.getSensitivityType(),
                    toEquipmentContainerDTO(sensitivityPst.getInjections()),
                    toEquipmentContainerDTO(sensitivityPst.getContingencies()),
                    sensitivityPst.isActivated()))
                .toList();

        List<SensitivityNodes> sensiNodes = entity.getSensitivityNodes().stream()
                .map(sensitivityNode -> new SensitivityNodes(
                    toEquipmentContainerDTO(sensitivityNode.getMonitoredBranch()),
                    toEquipmentContainerDTO(sensitivityNode.getInjections()),
                    toEquipmentContainerDTO(sensitivityNode.getContingencies()),
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

    private List<EquipmentsContainer> toEquipmentContainerDTO(List<UUID> containerIds) {
        if (containerIds == null) {
            return null;
        }
        return containerIds.stream()
                .map(id -> new EquipmentsContainer(id, id.toString()))
                .toList();
    }
}

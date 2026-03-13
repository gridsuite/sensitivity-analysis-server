/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities.parameters;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "sensitivityAnalysisParameters")
public class SensitivityAnalysisParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "provider")
    private String provider;

    @Column(name = "flowFlowSensitivityValueThreshold")
    private double flowFlowSensitivityValueThreshold = 0.0;

    @Column(name = "angleFlowSensitivityValueThreshold")
    private double angleFlowSensitivityValueThreshold = 0.0;

    @Column(name = "flowVoltageSensitivityValueThreshold")
    private double flowVoltageSensitivityValueThreshold = 0.0;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorWithDistribTypeEntity> sensitivityInjectionsSets = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorForInjectionEntity> sensitivityInjections = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorWithSensiTypeForHvdcEntity> sensitivityHVDCs = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorWithSensiTypeForPstEntity> sensitivityPSTs = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorForNodeEntity> sensitivityNodes = new ArrayList<>();

    public SensitivityAnalysisParametersEntity(@NonNull SensitivityAnalysisParametersInfos parametersInfos) {
        assignAttributes(parametersInfos);
    }

    public void update(@NonNull SensitivityAnalysisParametersInfos parametersInfos) {
        assignAttributes(parametersInfos);
    }

    public void assignAttributes(@NonNull SensitivityAnalysisParametersInfos parametersInfos) {
        this.provider = parametersInfos.getProvider();
        this.flowFlowSensitivityValueThreshold = parametersInfos.getFlowFlowSensitivityValueThreshold();
        this.angleFlowSensitivityValueThreshold = parametersInfos.getAngleFlowSensitivityValueThreshold();
        this.flowVoltageSensitivityValueThreshold = parametersInfos.getFlowVoltageSensitivityValueThreshold();
        this.sensitivityInjectionsSets.clear();
        this.sensitivityInjectionsSets.addAll(convertInjectionsSets(parametersInfos.getSensitivityInjectionsSet()));
        this.sensitivityInjections.clear();
        this.sensitivityInjections.addAll(convertInjections(parametersInfos.getSensitivityInjection()));
        this.sensitivityHVDCs.clear();
        this.sensitivityHVDCs.addAll(convertHdvcs(parametersInfos.getSensitivityHVDC()));
        this.sensitivityPSTs.clear();
        this.sensitivityPSTs.addAll(convertPsts(parametersInfos.getSensitivityPST()));
        this.sensitivityNodes.clear();
        this.sensitivityNodes.addAll(convertNodes(parametersInfos.getSensitivityNodes()));
    }

    private List<SensitivityFactorWithDistribTypeEntity> convertInjectionsSets(List<SensitivityInjectionsSet> sensitivityInjectionsSets) {
        List<SensitivityFactorWithDistribTypeEntity> sensitivityInjectionsSetEntities = new ArrayList<>();
        if (sensitivityInjectionsSets != null) {
            for (SensitivityInjectionsSet sensitivityInjectionsSet : sensitivityInjectionsSets) {
                SensitivityFactorWithDistribTypeEntity entity = new SensitivityFactorWithDistribTypeEntity();
                entity.setDistributionType(sensitivityInjectionsSet.getDistributionType());
                entity.setMonitoredBranch(sensitivityInjectionsSet.getMonitoredBranches());
                entity.setInjections(sensitivityInjectionsSet.getInjections());
                entity.setContingencies(sensitivityInjectionsSet.getContingencies());
                entity.setActivated(sensitivityInjectionsSet.isActivated());
                sensitivityInjectionsSetEntities.add(entity);
            }
        }
        return sensitivityInjectionsSetEntities;
    }

    private List<SensitivityFactorForInjectionEntity> convertInjections(List<SensitivityInjection> sensitivityInjections) {
        List<SensitivityFactorForInjectionEntity> sensitivityInjectionEntities = new ArrayList<>();
        if (sensitivityInjections != null) {
            for (SensitivityInjection sensitivityInjection : sensitivityInjections) {
                SensitivityFactorForInjectionEntity entity = new SensitivityFactorForInjectionEntity();
                entity.setMonitoredBranch(sensitivityInjection.getMonitoredBranches());
                entity.setInjections(sensitivityInjection.getInjections());
                entity.setContingencies(sensitivityInjection.getContingencies());
                entity.setActivated(sensitivityInjection.isActivated());
                sensitivityInjectionEntities.add(entity);
            }
        }
        return sensitivityInjectionEntities;
    }

    private List<SensitivityFactorWithSensiTypeForHvdcEntity> convertHdvcs(List<SensitivityHVDC> sensitivityHvdcs) {
        List<SensitivityFactorWithSensiTypeForHvdcEntity> sensitivityHvdcEntities = new ArrayList<>();
        if (sensitivityHvdcs != null) {
            for (SensitivityHVDC sensitivityHvdc : sensitivityHvdcs) {
                SensitivityFactorWithSensiTypeForHvdcEntity entity = new SensitivityFactorWithSensiTypeForHvdcEntity();
                entity.setMonitoredBranch(sensitivityHvdc.getMonitoredBranches());
                entity.setInjections(sensitivityHvdc.getHvdcs());
                entity.setSensitivityType(sensitivityHvdc.getSensitivityType());
                entity.setContingencies(sensitivityHvdc.getContingencies());
                entity.setActivated(sensitivityHvdc.isActivated());
                sensitivityHvdcEntities.add(entity);
            }
        }
        return sensitivityHvdcEntities;
    }

    private List<SensitivityFactorWithSensiTypeForPstEntity> convertPsts(List<SensitivityPST> sensitivityPsts) {
        List<SensitivityFactorWithSensiTypeForPstEntity> sensitivityPstEntities = new ArrayList<>();
        if (sensitivityPsts != null) {
            for (SensitivityPST sensitivityPst : sensitivityPsts) {
                SensitivityFactorWithSensiTypeForPstEntity entity = new SensitivityFactorWithSensiTypeForPstEntity();
                entity.setSensitivityType(sensitivityPst.getSensitivityType());
                entity.setMonitoredBranch(sensitivityPst.getMonitoredBranches());
                entity.setInjections(sensitivityPst.getPsts());
                entity.setContingencies(sensitivityPst.getContingencies());
                entity.setActivated(sensitivityPst.isActivated());
                sensitivityPstEntities.add(entity);
            }
        }
        return sensitivityPstEntities;
    }

    private List<SensitivityFactorForNodeEntity> convertNodes(List<SensitivityNodes> sensitivityNodes) {
        List<SensitivityFactorForNodeEntity> sensitivityNodeEntities = new ArrayList<>();
        if (sensitivityNodes != null) {
            for (SensitivityNodes sensitivityNode : sensitivityNodes) {
                SensitivityFactorForNodeEntity entity = new SensitivityFactorForNodeEntity();
                entity.setMonitoredBranch(sensitivityNode.getMonitoredVoltageLevels());
                entity.setInjections(sensitivityNode.getEquipmentsInVoltageRegulation());
                entity.setContingencies(sensitivityNode.getContingencies());
                entity.setActivated(sensitivityNode.isActivated());
                sensitivityNodeEntities.add(entity);
            }
        }
        return sensitivityNodeEntities;
    }
}

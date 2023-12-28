/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.entities.parameters;

import jakarta.persistence.*;
import lombok.*;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.SensitivityAnalysisParametersInfos;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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

    @Column(name = "date")
    private ZonedDateTime date;

    @Column(name = "name")
    private String name;

    @Column(name = "flowFlowSensitivityValueThreshold")
    private double flowFlowSensitivityValueThreshold;

    @Column(name = "angleFlowSensitivityValueThreshold")
    private double angleFlowSensitivityValueThreshold;

    @Column(name = "flowVoltageSensitivityValueThreshold")
    private double flowVoltageSensitivityValueThreshold;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorWithDistribTypeEntity> sensitivityInjectionsSet = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorForInjectionEntity> sensitivityInjections = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorWithSensiTypeForHvdcEntity> sensitivityHvdc = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorWithSensiTypeForPstEntity> sensitivityPST = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_analysis_parameters_id")
    private List<SensitivityFactorForNodeEntity> sensitivityNodes = new ArrayList<>();

    public SensitivityAnalysisParametersEntity(@NonNull SensitivityAnalysisParametersInfos parametersInfos) {
        this.date = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        assignAttributes(parametersInfos);
    }

    /**
     * Copy used to duplicate in DB with .save.
     * The ID is changed. The date is updated.
     *
     * @return a copy of the entity
     */
    @Override
    public SensitivityAnalysisParametersEntity clone() {
        return SensitivityAnalysisParametersEntity.builder()
            .date(ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS))
            .name(this.name)
            .flowFlowSensitivityValueThreshold(this.flowFlowSensitivityValueThreshold)
            .angleFlowSensitivityValueThreshold(this.angleFlowSensitivityValueThreshold)
            .flowVoltageSensitivityValueThreshold(this.flowVoltageSensitivityValueThreshold)
            .sensitivityInjectionsSet(new ArrayList<>(this.sensitivityInjectionsSet))
            .sensitivityInjections(new ArrayList<>(this.sensitivityInjections))
            .sensitivityHvdc(new ArrayList<>(this.sensitivityHvdc))
            .sensitivityPST(new ArrayList<>(this.sensitivityPST))
            .sensitivityNodes(new ArrayList<>(this.sensitivityNodes))
            .build();
    }

    public void update(@NonNull SensitivityAnalysisParametersInfos parametersInfos) {
        assignAttributes(parametersInfos);
    }

    public void assignAttributes(@NonNull SensitivityAnalysisParametersInfos parametersInfos) {
        this.name = parametersInfos.getName();
        this.flowFlowSensitivityValueThreshold = parametersInfos.getFlowFlowSensitivityValueThreshold();
        this.angleFlowSensitivityValueThreshold = parametersInfos.getAngleFlowSensitivityValueThreshold();
        this.flowVoltageSensitivityValueThreshold = parametersInfos.getFlowVoltageSensitivityValueThreshold();
        this.sensitivityInjectionsSet.clear();
        this.sensitivityInjectionsSet.addAll(convertInjectionsSets(parametersInfos.getSensitivityInjectionsSet()));
        this.sensitivityInjections.clear();
        this.sensitivityInjections.addAll(convertInjections(parametersInfos.getSensitivityInjection()));
        this.sensitivityHvdc.clear();
        this.sensitivityHvdc.addAll(convertHdvcs(parametersInfos.getSensitivityHVDC()));
        this.sensitivityPST.clear();
        this.sensitivityPST.addAll(convertPsts(parametersInfos.getSensitivityPST()));
        this.sensitivityNodes.clear();
        this.sensitivityNodes.addAll(convertNodes(parametersInfos.getSensitivityNodes()));
    }

    private List<SensitivityFactorWithDistribTypeEntity> convertInjectionsSets(List<SensitivityInjectionsSet> sensitivityInjectionsSets) {
        List<SensitivityFactorWithDistribTypeEntity> sensitivityInjectionsSetEntities = new ArrayList<>();
        if (sensitivityInjectionsSets != null) {
            for (SensitivityInjectionsSet sensitivityInjectionsSet : sensitivityInjectionsSets) {
                SensitivityFactorWithDistribTypeEntity entity = new SensitivityFactorWithDistribTypeEntity();
                entity.setDistributionType(sensitivityInjectionsSet.getDistributionType());
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjectionsSet.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjectionsSet.getInjections()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjectionsSet.getContingencies()));
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
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjection.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjection.getInjections()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjection.getContingencies()));
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
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityHvdc.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityHvdc.getHvdcs()));
                entity.setSensitivityType(sensitivityHvdc.getSensitivityType());
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityHvdc.getContingencies()));
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
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityPst.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityPst.getPsts()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityPst.getContingencies()));
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
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityNode.getMonitoredVoltageLevels()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityNode.getEquipmentsInVoltageRegulation()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityNode.getContingencies()));
                entity.setActivated(sensitivityNode.isActivated());
                sensitivityNodeEntities.add(entity);
            }
        }
        return sensitivityNodeEntities;
    }

    public SensitivityAnalysisParametersInfos toInfos() {

        List<SensitivityInjectionsSet> sensitivityInjectionsSets = new ArrayList<>();
        this.sensitivityInjectionsSet.stream().map(sensitivityInjectionsSet -> new SensitivityInjectionsSet(
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjectionsSet.getMonitoredBranch()),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjectionsSet.getInjections()),
            sensitivityInjectionsSet.getDistributionType(),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjectionsSet.getContingencies()),
            sensitivityInjectionsSet.isActivated()
        )).forEach(sensitivityInjectionsSets::add);

        List<SensitivityInjection> sensitivityInjections = new ArrayList<>();
        this.sensitivityInjections.stream().map(sensitivityInjection -> new SensitivityInjection(
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjection.getMonitoredBranch()),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjection.getInjections()),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjection.getContingencies()),
            sensitivityInjection.isActivated()
        )).forEach(sensitivityInjections::add);

        List<SensitivityHVDC> sensitivityHvdcs = new ArrayList<>();
        this.sensitivityHvdc.stream().map(sensitivityHvdc -> new SensitivityHVDC(
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityHvdc.getMonitoredBranch()),
            sensitivityHvdc.getSensitivityType(),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityHvdc.getInjections()),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityHvdc.getContingencies()),
            sensitivityHvdc.isActivated()
        )).forEach(sensitivityHvdcs::add);

        List<SensitivityPST> sensitivityPsts = new ArrayList<>();
        this.sensitivityPST.stream().map(sensitivityPst -> new SensitivityPST(
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityPst.getMonitoredBranch()),
            sensitivityPst.getSensitivityType(),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityPst.getInjections()),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityPst.getContingencies()),
            sensitivityPst.isActivated()
        )).forEach(sensitivityPsts::add);

        List<SensitivityNodes> sensitivityNodes = new ArrayList<>();
        this.sensitivityNodes.stream().map(sensitivityNode -> new SensitivityNodes(
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityNode.getMonitoredBranch()),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityNode.getInjections()),
            EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityNode.getContingencies()),
            sensitivityNode.isActivated()
        )).forEach(sensitivityNodes::add);

        return SensitivityAnalysisParametersInfos.builder()
            .uuid(this.id)
            .date(this.date)
            .name(this.name)
            .flowFlowSensitivityValueThreshold(this.flowFlowSensitivityValueThreshold)
            .angleFlowSensitivityValueThreshold(this.angleFlowSensitivityValueThreshold)
            .flowVoltageSensitivityValueThreshold(this.flowVoltageSensitivityValueThreshold)
            .sensitivityInjectionsSet(sensitivityInjectionsSets)
            .sensitivityInjection(sensitivityInjections)
            .sensitivityHVDC(sensitivityHvdcs)
            .sensitivityPST(sensitivityPsts)
            .sensitivityNodes(sensitivityNodes)
            .build();
    }
}

/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.sensitivityanalysis.server.dto.*;
import org.gridsuite.sensitivityanalysis.server.dto.parameters.FactorCount;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@Service
public class SensitivityAnalysisFactorCountService {

    private final ActionsService actionsService;
    private final FilterService filterService;

    public SensitivityAnalysisFactorCountService(ActionsService actionsService,
                                                 FilterService filterService) {
        this.actionsService = actionsService;
        this.filterService = filterService;
    }

    private record Factor(
            List<UUID> monitoredEquipmentIds,
            List<UUID> variableIds,
            List<UUID> contingencyIds,
            FactorType type
    ) {
    }

    private enum FactorType {
        INJECTIONS,
        INJECTIONS_SET,
        HVDC,
        PST,
        NODES
    }

    public FactorCount getFactorCount(
            UUID networkUuid,
            String variantId,
            List<SensitivityInjectionsSet> injectionsSets,
            List<SensitivityInjection> injections,
            List<SensitivityHVDC> hvdcs,
            List<SensitivityPST> psts,
            List<SensitivityNodes> nodes
    ) {
        Objects.requireNonNull(injectionsSets);
        Objects.requireNonNull(injections);
        Objects.requireNonNull(hvdcs);
        Objects.requireNonNull(psts);
        Objects.requireNonNull(nodes);
        if (CollectionUtils.isEmpty(injectionsSets)
                && CollectionUtils.isEmpty(injections)
                && CollectionUtils.isEmpty(hvdcs)
                && CollectionUtils.isEmpty(psts)
                && CollectionUtils.isEmpty(nodes)) {
            return new FactorCount(0, 0);
        }

        List<Factor> factors = getFactors(
                injectionsSets,
                injections,
                hvdcs,
                psts,
                nodes
        );

        Map<String, Long> equipmentCounts = fetchIdentifiableCounts(factors, networkUuid, variantId);
        Map<String, Long> contingencyCounts = fetchContingencyCounts(factors, networkUuid, variantId);

        return computeFactorCounts(factors, equipmentCounts, contingencyCounts);
    }

    private List<Factor> getFactors(
            List<SensitivityInjectionsSet> injectionsSets,
            List<SensitivityInjection> injections,
            List<SensitivityHVDC> hvdcs,
            List<SensitivityPST> psts,
            List<SensitivityNodes> nodes
    ) {
        List<Factor> factors = new ArrayList<>();

        injectionsSets.stream()
            .filter(SensitivityInjectionsSet::isActivated)
            .forEach(injectionsSet -> factors.add(new Factor(
                                        extractContainerIds(injectionsSet.getMonitoredBranches()),
                                        null,
                                        extractContainerIds(injectionsSet.getContingencies()),
                                        FactorType.INJECTIONS_SET
                                )
                        )
            );

        injections.stream()
            .filter(SensitivityInjection::isActivated)
            .forEach(injection -> factors.add(new Factor(
                                        extractContainerIds(injection.getMonitoredBranches()),
                                        extractContainerIds(injection.getInjections()),
                                        extractContainerIds(injection.getContingencies()),
                                        FactorType.INJECTIONS
                                )
                        )
            );

        hvdcs.stream()
            .filter(SensitivityHVDC::isActivated)
            .forEach(hvdc -> factors.add(new Factor(
                                        extractContainerIds(hvdc.getMonitoredBranches()),
                                        extractContainerIds(hvdc.getHvdcs()),
                                        extractContainerIds(hvdc.getContingencies()),
                                        FactorType.HVDC
                                )
                        )
            );

        psts.stream()
            .filter(SensitivityPST::isActivated)
            .forEach(pst -> factors.add(new Factor(
                                        extractContainerIds(pst.getMonitoredBranches()),
                                        extractContainerIds(pst.getPsts()),
                                        extractContainerIds(pst.getContingencies()),
                                        FactorType.PST
                                )
                        )
            );

        nodes.stream()
            .filter(SensitivityNodes::isActivated)
            .forEach(node -> factors.add(new Factor(
                                        extractContainerIds(node.getMonitoredVoltageLevels()),
                                        extractContainerIds(node.getEquipmentsInVoltageRegulation()),
                                        extractContainerIds(node.getContingencies()),
                                        FactorType.NODES
                                )
                        )
            );

        return factors;
    }

    private List<UUID> extractContainerIds(List<EquipmentsContainer> containers) {
        return containers.stream()
                .map(EquipmentsContainer::getContainerId)
                .toList();
    }

    private Map<String, Long> fetchIdentifiableCounts(
            List<Factor> factors,
            UUID networkUuid,
            String variantId
    ) {
        Map<String, List<UUID>> identifiableIdsByKey = new HashMap<>();

        for (int i = 0; i < factors.size(); i++) {
            Factor factor = factors.get(i);
            identifiableIdsByKey.put(monitoredEquipmentsKey(i), factor.monitoredEquipmentIds());

            if (factor.type() != FactorType.INJECTIONS_SET && factor.variableIds() != null) {
                identifiableIdsByKey.put(variablesKey(i), factor.variableIds());
            }
        }

        return filterService.getIdentifiablesCountByGroup(
                SensitivityFactorsIdsByGroup.builder().ids(identifiableIdsByKey).build(),
                networkUuid,
                variantId
        );
    }

    private Map<String, Long> fetchContingencyCounts(
            List<Factor> factors,
            UUID networkUuid,
            String variantId
    ) {
        Map<String, List<UUID>> contingencyIdsByKey = new HashMap<>();

        for (int i = 0; i < factors.size(); i++) {
            List<UUID> contingencyIds = factors.get(i).contingencyIds();
            if (contingencyIds != null && !contingencyIds.isEmpty()) {
                contingencyIdsByKey.put(contingenciesKey(i), contingencyIds);
            }
        }

        if (contingencyIdsByKey.isEmpty()) {
            return Map.of();
        }

        return actionsService.getContingencyCountByGroup(
                SensitivityFactorsIdsByGroup.builder().ids(contingencyIdsByKey).build(),
                networkUuid,
                variantId);
    }

    private FactorCount computeFactorCounts(
            List<Factor> factors,
            Map<String, Long> equipmentCounts,
            Map<String, Long> contingencyCounts
    ) {
        long totalVariableCount = 0;
        long totalResultCount = 0;

        for (int i = 0; i < factors.size(); i++) {
            Factor factor = factors.get(i);

            long monitoredEquipmentCount = equipmentCounts.get(monitoredEquipmentsKey(i));
            long contingencyMultiplier = 1 + contingencyCounts.getOrDefault(contingenciesKey(i), 0L);

            switch (factor.type()) {
                case INJECTIONS_SET -> {
                    totalResultCount += monitoredEquipmentCount * contingencyMultiplier;
                    totalVariableCount += 1;
                }
                case NODES -> {
                    long variableCount = equipmentCounts.get(variablesKey(i));
                    // Empirical factor: 2 BUS/BBS per node to avoid network traversal
                    totalResultCount += monitoredEquipmentCount * variableCount * contingencyMultiplier * 2;
                    totalVariableCount += variableCount;
                }
                default -> {
                    long variableCount = equipmentCounts.get(variablesKey(i));
                    totalResultCount += monitoredEquipmentCount * variableCount * contingencyMultiplier;
                    totalVariableCount += variableCount;
                }
            }
        }
        return new FactorCount(totalVariableCount, totalResultCount);
    }

    private static String monitoredEquipmentsKey(int index) {
        return "monitored-" + index;
    }

    private static String variablesKey(int index) {
        return "variables-" + index;
    }

    private static String contingenciesKey(int index) {
        return "contingencies-" + index;
    }
}

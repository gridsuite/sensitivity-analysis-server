/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.service.AbstractFilterService;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.sensitivityanalysis.server.dto.FilterEquipments;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityFactorsIdsByGroup;
import org.gridsuite.sensitivityanalysis.server.entities.SensitivityResultEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class FilterService extends AbstractFilterService {
    private static final String QUERY_PARAM_VARIANT_ID = "variantId";

    public FilterService(
            NetworkStoreService networkStoreService,
            @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        super(networkStoreService, filterServerBaseUri);
    }

    public Map<String, Long> getIdentifiablesCount(SensitivityFactorsIdsByGroup factorsIds, UUID networkUuid, String variantId) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + FILTER_API_VERSION + "/filters/identifiables-count")
                .queryParam(NETWORK_UUID, networkUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        factorsIds.getIds().forEach((key, value) -> uriComponentsBuilder.queryParam(String.format("ids[%s]", key), value));

        var path = uriComponentsBuilder.build().toUriString();

        return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Long>>() {
        }).getBody();
    }

    public List<FilterEquipments> getFilterEquipments(List<UUID> filterUuids, UUID networkUuid, String variantId) {
        Objects.requireNonNull(filterUuids);
        Objects.requireNonNull(networkUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + FILTER_API_VERSION + "/filters/export")
                .queryParam(IDS, filterUuids)
                .queryParam(NETWORK_UUID, networkUuid.toString());
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.build().toUriString();

        return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<FilterEquipments>>() {
                }).getBody();
    }

    public List<IdentifiableAttributes> getIdentifiablesFromFilters(List<UUID> filterUuids, UUID networkUuid, String variantId) {
        List<FilterEquipments> filterEquipments = getFilterEquipments(filterUuids, networkUuid, variantId);

        List<IdentifiableAttributes> mergedIdentifiables = new ArrayList<>();
        for (FilterEquipments filterEquipment : filterEquipments) {
            mergedIdentifiables.addAll(filterEquipment.getIdentifiableAttributes());
        }

        return mergedIdentifiables;
    }

    public List<IdentifiableAttributes> getIdentifiablesFromFilter(UUID filterUuid, UUID networkUuid, String variantId) {
        return getIdentifiablesFromFilters(List.of(filterUuid), networkUuid, variantId);
    }

    public List<ResourceFilterDTO> getResourceFilters(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        Network network = getNetwork(networkUuid, variantId);
        List<AbstractFilter> genericFilters = getFilters(globalFilter.getGenericFilter());

        Map<EquipmentType, List<String>> subjectIdsByEquipmentType = filterEquipmentsByType(
                network, globalFilter, genericFilters, List.of(EquipmentType.LINE, EquipmentType.TWO_WINDINGS_TRANSFORMER)
        );

        List<String> allSubjectIds = subjectIdsByEquipmentType.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();

        return allSubjectIds.isEmpty() ? List.of() :
                List.of(new ResourceFilterDTO(
                        ResourceFilterDTO.DataType.TEXT,
                        ResourceFilterDTO.Type.IN,
                        allSubjectIds,
                        SensitivityResultEntity.Fields.functionId
                ));
    }

    /**
     * Filters equipments by type and returns map of IDs grouped by equipment type
     */
    private Map<EquipmentType, List<String>> filterEquipmentsByType(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            List<EquipmentType> equipmentTypes) {

        Map<EquipmentType, List<String>> result = new EnumMap<>(EquipmentType.class);

        for (EquipmentType equipmentType : equipmentTypes) {
            List<String> filteredIds = extractFilteredEquipmentIds(network, globalFilter, genericFilters, equipmentType);
            if (!filteredIds.isEmpty()) {
                result.put(equipmentType, filteredIds);
            }
        }

        return result;
    }

    /**
     * Extracts filtered equipment IDs by applying expert and generic filters
     */
    private List<String> extractFilteredEquipmentIds(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            EquipmentType equipmentType) {

        List<List<String>> allFilterResults = new ArrayList<>();

        // Extract IDs from expert filter
        ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
        if (expertFilter != null) {
            allFilterResults.add(filterNetwork(expertFilter, network));
        }

        // Extract IDs from generic filters
        for (AbstractFilter filter : genericFilters) {
            List<String> filterResult = extractEquipmentIdsFromGenericFilter(filter, equipmentType, network);
            if (!filterResult.isEmpty()) {
                allFilterResults.add(filterResult);
            }
        }

        // Combine results with appropriate logic
        // Expert filters use OR between them, generic filters use AND
        return combineFilterResults(allFilterResults, !genericFilters.isEmpty());
    }

    @Override
    protected List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case LINE, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
            case VOLTAGE_LEVEL -> List.of(FieldType.NOMINAL_VOLTAGE);
            default -> List.of();
        };
    }

    @Override
    protected List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case VOLTAGE_LEVEL, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.COUNTRY);
            case LINE -> List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2);
            default -> List.of();
        };
    }

    @Override
    protected List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
        return equipmentType == EquipmentType.LINE ?
                List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2) :
                List.of(FieldType.SUBSTATION_PROPERTIES);
    }
}

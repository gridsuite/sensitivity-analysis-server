/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.service.AbstractFilterService;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.FilterUuidExpertRule;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
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

        Map<EquipmentType, List<String>> subjectIdsByEquipmentType = processEquipmentTypes(
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

    private Map<EquipmentType, List<String>> processEquipmentTypes(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            List<EquipmentType> equipmentTypes
    ) {
        Map<EquipmentType, List<String>> result = new EnumMap<>(EquipmentType.class);

        for (EquipmentType equipmentType : equipmentTypes) {
            List<String> intersectedIds = processEquipmentType(network, globalFilter, genericFilters, equipmentType);
            if (!intersectedIds.isEmpty()) {
                result.put(equipmentType, intersectedIds);
            }
        }

        return result;
    }

    private List<String> processEquipmentType(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            EquipmentType equipmentType
    ) {
        Set<String> expertFilterResults = new HashSet<>();
        ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
        if (expertFilter != null) {
            expertFilterResults.addAll(filterNetwork(expertFilter, network));
        }

        Set<String> genericFilterResults = new HashSet<>();
        for (AbstractFilter filter : genericFilters) {
            List<String> filterIds = processGenericFilter(filter, equipmentType, network);
            genericFilterResults.addAll(filterIds);
        }

        if (!expertFilterResults.isEmpty() && !genericFilterResults.isEmpty()) {
            expertFilterResults.retainAll(genericFilterResults);
            return new ArrayList<>(expertFilterResults);
        }

        return !expertFilterResults.isEmpty() ?
                new ArrayList<>(expertFilterResults) :
                new ArrayList<>(genericFilterResults);
    }

    private List<String> processGenericFilter(AbstractFilter filter, EquipmentType equipmentType, Network network) {
        if (filter.getEquipmentType() == equipmentType) {
            return filterNetwork(filter, network);
        } else if (filter.getEquipmentType() == EquipmentType.VOLTAGE_LEVEL) {
            ExpertFilter voltageFilter = buildExpertFilterWithVoltageLevelIdsCriteria(filter.getId(), equipmentType);
            return filterNetwork(voltageFilter, network);
        }
        return List.of();
    }

    private ExpertFilter buildExpertFilter(GlobalFilter globalFilter, EquipmentType equipmentType) {
        List<AbstractExpertRule> andRules = new ArrayList<>();

        List<AbstractExpertRule> nominalVRules = createNominalVoltageRules(
                globalFilter.getNominalV(),
                getNominalVoltageFieldType(equipmentType)
        );
        createOrCombination(nominalVRules).ifPresent(andRules::add);

        List<AbstractExpertRule> countryCodRules = createCountryCodeRules(
                globalFilter.getCountryCode(),
                getCountryCodeFieldType(equipmentType)
        );
        createOrCombination(countryCodRules).ifPresent(andRules::add);

        if (globalFilter.getSubstationProperty() != null) {
            List<AbstractExpertRule> propertiesRules = createSubstationPropertyRules(
                    globalFilter.getSubstationProperty(),
                    equipmentType
            );
            createOrCombination(propertiesRules).ifPresent(andRules::add);
        }

        return andRules.isEmpty() ? null :
                new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType,
                        createCombination(CombinatorType.AND, andRules));
    }

    private List<AbstractExpertRule> createNominalVoltageRules(List<String> nominalVoltageList, List<FieldType> nominalFieldTypes) {
        return nominalFieldTypes.stream()
                .flatMap(fieldType -> createNumberExpertRules(nominalVoltageList, fieldType).stream())
                .toList();
    }

    private List<AbstractExpertRule> createCountryCodeRules(List<Country> countryCodeList, List<FieldType> countryCodeFieldTypes) {
        return countryCodeFieldTypes.stream()
                .flatMap(fieldType -> createEnumExpertRules(countryCodeList, fieldType).stream())
                .toList();
    }

    private List<AbstractExpertRule> createSubstationPropertyRules(
            Map<String, List<String>> substationProperties,
            EquipmentType equipmentType
    ) {
        return substationProperties.entrySet().stream()
                .flatMap(entry -> getSubstationPropertiesFieldTypes(equipmentType).stream()
                        .map(fieldType -> createPropertiesRule(entry.getKey(), entry.getValue(), fieldType)))
                .toList();
    }

    private ExpertFilter buildExpertFilterWithVoltageLevelIdsCriteria(UUID filterUuid, EquipmentType equipmentType) {
        AbstractExpertRule voltageLevelId1Rule = createVoltageLevelIdRule(filterUuid, TwoSides.ONE);
        AbstractExpertRule voltageLevelId2Rule = createVoltageLevelIdRule(filterUuid, TwoSides.TWO);
        AbstractExpertRule orCombination = createCombination(CombinatorType.OR,
                List.of(voltageLevelId1Rule, voltageLevelId2Rule));
        return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, orCombination);
    }

    private AbstractExpertRule createVoltageLevelIdRule(UUID filterUuid, TwoSides side) {
        return FilterUuidExpertRule.builder()
                .operator(OperatorType.IS_PART_OF)
                .field(side == TwoSides.ONE ? FieldType.VOLTAGE_LEVEL_ID_1 : FieldType.VOLTAGE_LEVEL_ID_2)
                .values(Set.of(filterUuid.toString()))
                .build();
    }

    protected List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case LINE, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
            case VOLTAGE_LEVEL -> List.of(FieldType.NOMINAL_VOLTAGE);
            default -> List.of();
        };
    }

    protected List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case VOLTAGE_LEVEL, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.COUNTRY);
            case LINE -> List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2);
            default -> List.of();
        };
    }

    protected List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
        return equipmentType == EquipmentType.LINE ?
                List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2) :
                List.of(FieldType.SUBSTATION_PROPERTIES);
    }
}

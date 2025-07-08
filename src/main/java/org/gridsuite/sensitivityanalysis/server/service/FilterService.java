/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.FilterLoader;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.*;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class FilterService implements FilterLoader {
    public static final String FILTERS_NOT_FOUND = "Filters not found";
    static final String FILTER_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private final RestTemplate restTemplate = new RestTemplate();
    private final String filterServerBaseUri;
    public static final String NETWORK_UUID = "networkUuid";

    public static final String IDS = "ids";
    private static final String QUERY_PARAM_VARIANT_ID = "variantId";

    private final NetworkStoreService networkStoreService;

    public FilterService(NetworkStoreService networkStoreService, @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        this.networkStoreService = networkStoreService;
        this.filterServerBaseUri = filterServerBaseUri;
    }

    public List<AbstractFilter> getFilters(List<UUID> filtersUuids) {
        if (CollectionUtils.isEmpty(filtersUuids)) {
            return List.of();
        }

        String ids = filtersUuids.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + FILTER_API_VERSION + "/filters/metadata")
                .queryParam("ids", ids)
                .buildAndExpand()
                .toUriString();

        try {
            return restTemplate.exchange(
                    filterServerBaseUri + path,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<AbstractFilter>>() { }
            ).getBody();
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException(FILTERS_NOT_FOUND + " [" + filtersUuids + "]");
        }
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            if (network == null) {
                throw new PowsyblException("Network '" + networkUuid + "' not found");
            }
            if (StringUtils.isNotBlank(variantId)) {
                network.getVariantManager().setWorkingVariant(variantId);
            }
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private List<String> filterNetwork(AbstractFilter filter, Network network) {
        return FilterServiceUtils.getIdentifiableAttributes(filter, network, this)
                .stream()
                .map(org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes::getId)
                .toList();
    }

    private List<AbstractExpertRule> createNumberExpertRules(List<String> values, FieldType fieldType) {
        return values == null ? List.of() :
                values.stream()
                        .map(value -> NumberExpertRule.builder()
                                .value(Double.valueOf(value))
                                .field(fieldType)
                                .operator(OperatorType.EQUALS)
                                .build())
                        .collect(Collectors.toList());
    }

    private AbstractExpertRule createPropertiesRule(String property, List<String> propertiesValues, FieldType fieldType) {
        return PropertiesExpertRule.builder()
                .combinator(CombinatorType.OR)
                .operator(OperatorType.IN)
                .field(fieldType)
                .propertyName(property)
                .propertyValues(propertiesValues)
                .build();
    }

    private List<AbstractExpertRule> createEnumExpertRules(List<Country> values, FieldType fieldType) {
        return values == null ? List.of() :
                values.stream()
                        .map(value -> EnumExpertRule.builder()
                                .value(value.toString())
                                .field(fieldType)
                                .operator(OperatorType.EQUALS)
                                .build())
                        .collect(Collectors.toList());
    }

    private AbstractExpertRule createCombination(CombinatorType combinatorType, List<AbstractExpertRule> rules) {
        return CombinatorExpertRule.builder()
                .combinator(combinatorType)
                .rules(rules)
                .build();
    }

    private Optional<AbstractExpertRule> createOrCombination(List<AbstractExpertRule> rules) {
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rules.size() > 1 ?
                createCombination(CombinatorType.OR, rules) :
                rules.getFirst());
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

    private List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case LINE, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
            case VOLTAGE_LEVEL -> List.of(FieldType.NOMINAL_VOLTAGE);
            default -> List.of();
        };
    }

    private List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case VOLTAGE_LEVEL, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.COUNTRY);
            case LINE -> List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2);
            default -> List.of();
        };
    }

    private List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
        return equipmentType == EquipmentType.LINE ?
                List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2) :
                List.of(FieldType.SUBSTATION_PROPERTIES);
    }
}

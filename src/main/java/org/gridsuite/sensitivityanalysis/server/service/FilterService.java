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
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
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

    public List<IdentifiableAttributes> getIdentifiablesFromFilters(List<UUID> filterUuids, UUID networkUuid, String variantId) {
        List<FilterEquipments> filterEquipments = getFilterEquipements(filterUuids, networkUuid, variantId);

        List<IdentifiableAttributes> mergedIdentifiables = new ArrayList<>();
        for (FilterEquipments filterEquipment : filterEquipments) {
            mergedIdentifiables.addAll(filterEquipment.getIdentifiableAttributes());
        }

        return mergedIdentifiables;
    }

    public List<IdentifiableAttributes> getIdentifiablesFromFilter(UUID filterUuid, UUID networkUuid, String variantId) {
        return getIdentifiablesFromFilters(List.of(filterUuid), networkUuid, variantId);
    }

    public List<FilterEquipments> getFilterEquipements(List<UUID> filterUuids, UUID networkUuid, String variantId) {
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

    public List<AbstractFilter> getFilters(List<UUID> filtersUuids) {
        if (CollectionUtils.isEmpty(filtersUuids)) {
            return List.of();
        }
        var ids = "?ids=" + filtersUuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_API_VERSION + "/filters/metadata" + ids)
                .buildAndExpand()
                .toUriString();
        try {
            return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<AbstractFilter>>() { }).getBody();
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException(FILTERS_NOT_FOUND + " [" + filtersUuids + "]");
        }
    }

    private List<AbstractExpertRule> createNumberExpertRules(List<String> values, FieldType fieldType) {
        List<AbstractExpertRule> rules = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                rules.add(NumberExpertRule.builder()
                        .value(Double.valueOf(value))
                        .field(fieldType)
                        .operator(OperatorType.EQUALS)
                        .build());
            }
        }
        return rules;
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
        List<AbstractExpertRule> rules = new ArrayList<>();
        if (values != null) {
            for (Country value : values) {
                rules.add(EnumExpertRule.builder()
                        .value(value.toString())
                        .field(fieldType)
                        .operator(OperatorType.EQUALS)
                        .build());
            }
        }
        return rules;
    }

    private List<AbstractExpertRule> createNominalVoltageRules(List<String> nominalVoltageList, List<FieldType> nominalFieldTypes) {
        List<AbstractExpertRule> nominalVoltageRules = new ArrayList<>();
        for (FieldType fieldType : nominalFieldTypes) {
            nominalVoltageRules.addAll(createNumberExpertRules(nominalVoltageList, fieldType));
        }
        return nominalVoltageRules;
    }

    private List<AbstractExpertRule> createCountryCodeRules(List<Country> countryCodeList, List<FieldType> countryCodeFieldTypes) {
        List<AbstractExpertRule> countryCodeRules = new ArrayList<>();
        for (FieldType fieldType : countryCodeFieldTypes) {
            countryCodeRules.addAll(createEnumExpertRules(countryCodeList, fieldType));
        }
        return countryCodeRules;
    }

    private List<AbstractExpertRule> createPropertiesRules(String property, List<String> propertiesValues, List<FieldType> propertiesFieldTypes) {
        List<AbstractExpertRule> propertiesRules = new ArrayList<>();
        for (FieldType fieldType : propertiesFieldTypes) {
            propertiesRules.add(createPropertiesRule(property, propertiesValues, fieldType));
        }
        return propertiesRules;
    }

    private List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        boolean isLineOrTwoWT = equipmentType.equals(EquipmentType.LINE) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLineOrTwoWT) {
            return List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
        }
        if (equipmentType.equals(EquipmentType.VOLTAGE_LEVEL)) {
            return List.of(FieldType.NOMINAL_VOLTAGE);
        }
        return List.of();
    }

    private List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        boolean isLVoltageLevelOrTwoWT = equipmentType.equals(EquipmentType.VOLTAGE_LEVEL) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLVoltageLevelOrTwoWT) {
            return List.of(FieldType.COUNTRY);
        }
        if (equipmentType.equals(EquipmentType.LINE)) {
            return List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2);

        }
        return List.of();
    }

    private List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
        if (equipmentType.equals(EquipmentType.LINE)) {
            return List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2);
        }
        return List.of(FieldType.SUBSTATION_PROPERTIES);
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
        if (network == null) {
            throw new PowsyblException("Network '" + networkUuid + "' not found");
        }
        if (variantId != null) {
            network.getVariantManager().setWorkingVariant(variantId);
        }
        return network;
    }

    @NotNull
    private static List<String> filterNetwork(AbstractFilter filter, Network network, FilterLoader filterLoader) {
        return FilterServiceUtils.getIdentifiableAttributes(filter, network, filterLoader)
                .stream()
                .map(org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes::getId)
                .toList();
    }

    public List<ResourceFilterDTO> getResourceFiltersForSensitivity(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {

        Network network = getNetwork(networkUuid, variantId);

        final List<AbstractFilter> genericFilters = getFilters(globalFilter.getGenericFilter());

        EnumMap<EquipmentType, List<String>> subjectIdsByEquipmentType = new EnumMap<>(EquipmentType.class);
        List<EquipmentType> equipmentTypes = List.of(EquipmentType.LINE, EquipmentType.TWO_WINDINGS_TRANSFORMER);
        for (EquipmentType equipmentType : equipmentTypes) {
            List<List<String>> idsFilteredThroughEachFilter = new ArrayList<>();

            ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
            if (expertFilter != null) {
                List<String> expertIds = filterNetwork(expertFilter, network, this);
                if (!expertIds.isEmpty()) {
                    idsFilteredThroughEachFilter.add(expertIds);
                }
            }

            for (AbstractFilter filter : genericFilters) {
                if (filter.getEquipmentType() == equipmentType) {
                    List<String> genericIds = filterNetwork(filter, network, this);
                    if (!genericIds.isEmpty()) {
                        idsFilteredThroughEachFilter.add(genericIds);
                    }
                } else if (filter.getEquipmentType() == EquipmentType.VOLTAGE_LEVEL) {
                    ExpertFilter expertFilterWithVoltageLevelIdsCriteria = buildExpertFilterWithVoltageLevelIdsCriteria(filter.getId(), equipmentType);
                    List<String> voltageIds = filterNetwork(expertFilterWithVoltageLevelIdsCriteria, network, this);
                    if (!voltageIds.isEmpty()) {
                        idsFilteredThroughEachFilter.add(voltageIds);
                    }
                }
            }

            if (idsFilteredThroughEachFilter.isEmpty()) {
                continue;
            }

            List<String> finalIds = idsFilteredThroughEachFilter.get(0);
            for (int i = 1; i < idsFilteredThroughEachFilter.size(); i++) {
                finalIds = finalIds.stream()
                        .filter(idsFilteredThroughEachFilter.get(i)::contains)
                        .collect(Collectors.toList());
            }

            if (!finalIds.isEmpty()) {
                subjectIdsByEquipmentType.put(equipmentType, finalIds);
            }
        }

        List<String> subjectIdsFromEvalFilter = new ArrayList<>();
        subjectIdsByEquipmentType.values().forEach(idsList ->
                Optional.ofNullable(idsList).ifPresent(subjectIdsFromEvalFilter::addAll)
        );

        return (subjectIdsFromEvalFilter.isEmpty()) ? List.of() :
                List.of(new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.IN, subjectIdsFromEvalFilter, "functionId"));
    }

    private ExpertFilter buildExpertFilter(GlobalFilter globalFilter, EquipmentType equipmentType) {
        List<AbstractExpertRule> andRules = new ArrayList<>();

        List<AbstractExpertRule> nominalVRules = createNominalVoltageRules(globalFilter.getNominalV(), getNominalVoltageFieldType(equipmentType));
        createOrCombination(nominalVRules).ifPresent(andRules::add);

        List<AbstractExpertRule> countryCodRules = createCountryCodeRules(globalFilter.getCountryCode(), getCountryCodeFieldType(equipmentType));
        createOrCombination(countryCodRules).ifPresent(andRules::add);

        if (globalFilter.getSubstationProperty() != null) {
            List<AbstractExpertRule> propertiesRules = new ArrayList<>();
            globalFilter.getSubstationProperty().forEach((propertyName, propertiesValues) ->
                    propertiesRules.addAll(createPropertiesRules(
                            propertyName,
                            propertiesValues,
                            getSubstationPropertiesFieldTypes(equipmentType)
                    )));
            createOrCombination(propertiesRules).ifPresent(andRules::add);
        }

        if (andRules.isEmpty()) {
            return null;
        }

        AbstractExpertRule andCombination = createCombination(CombinatorType.AND, andRules);

        return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, andCombination);
    }

    private ExpertFilter buildExpertFilterWithVoltageLevelIdsCriteria(UUID filterUuid, EquipmentType equipmentType) {
        AbstractExpertRule voltageLevelId1Rule = createVoltageLevelIdRule(filterUuid, TwoSides.ONE);
        AbstractExpertRule voltageLevelId2Rule = createVoltageLevelIdRule(filterUuid, TwoSides.TWO);
        AbstractExpertRule orCombination = createCombination(CombinatorType.OR, List.of(voltageLevelId1Rule, voltageLevelId2Rule));
        return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, orCombination);
    }

    private AbstractExpertRule createVoltageLevelIdRule(UUID filterUuid, TwoSides side) {
        return FilterUuidExpertRule.builder()
                .operator(OperatorType.IS_PART_OF)
                .field(side == TwoSides.ONE ? FieldType.VOLTAGE_LEVEL_ID_1 : FieldType.VOLTAGE_LEVEL_ID_2)
                .values(Set.of(filterUuid.toString()))
                .build();
    }

    private AbstractExpertRule createCombination(CombinatorType combinatorType, List<AbstractExpertRule> rules) {
        return CombinatorExpertRule.builder().combinator(combinatorType).rules(rules).build();
    }

    private Optional<AbstractExpertRule> createOrCombination(List<AbstractExpertRule> rules) {
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rules.size() > 1 ? createCombination(CombinatorType.OR, rules) : rules.getFirst());
    }
}

/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.sensitivityanalysis.server.dto.FilterEquipments;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityFactorsIdsByGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class FilterService {

    static final String FILTER_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private final RestTemplate restTemplate = new RestTemplate();
    private String filterServerBaseUri;
    public static final String NETWORK_UUID = "networkUuid";

    public static final String IDS = "ids";
    private static final String QUERY_PARAM_VARIANT_ID = "variantId";

    @Autowired
    public FilterService(@Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
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
        return getIdentifiablesCount(factorsIds.getIds(), networkUuid, variantId);
    }

    public Map<String, Long> getIdentifiablesCountForACategory(List<UUID> factorsIds, String category, UUID networkUuid, String variantId) {
        return getIdentifiablesCount(Collections.singletonMap(category, factorsIds), networkUuid, variantId);
    }

    private Map<String, Long> getIdentifiablesCount(Map<String, List<UUID>> identifiablesFilterIds, UUID networkUuid, String variantId) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + FILTER_API_VERSION + "/filters/identifiables-count")
            .queryParam(NETWORK_UUID, networkUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        identifiablesFilterIds.forEach((key, value) -> uriComponentsBuilder.queryParam(String.format("ids[%s]", key), value));

        var path = uriComponentsBuilder.build().toUriString();

        return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Long>>() {
        }).getBody();
    }
}

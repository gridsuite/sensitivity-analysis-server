/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.sensitivityanalysis.server.dto.ContingencyListExportResult;
import org.gridsuite.sensitivityanalysis.server.dto.SensitivityFactorsIdsByGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class ActionsService {

    static final String ACTIONS_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private String actionsServerBaseUri;
    private static final String NETWORK_UUID = "networkUuid";
    private static final String QUERY_PARAM_VARIANT_ID = "variantId";
    private static final String QUERY_PARAM_CONTINGENCY_LIST_IDS = "contingencyListIds";

    private final RestTemplate restTemplate;

    public ActionsService(@Value("${gridsuite.services.actions-server.base-uri:http://actions-server/}") String actionsServerBaseUri, RestTemplate restTemplate) {
        this.actionsServerBaseUri = actionsServerBaseUri;
        this.restTemplate = restTemplate;
    }

    public void setActionsServerBaseUri(String actionsServerBaseUri) {
        this.actionsServerBaseUri = actionsServerBaseUri;
    }

    public Map<String, Long> getContingencyCountByGroup(SensitivityFactorsIdsByGroup contingencyListIdsByGroup, UUID networkUuid, String variantId) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/count/by-group")
                .queryParam(NETWORK_UUID, networkUuid);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SensitivityFactorsIdsByGroup> httpEntity = new HttpEntity<>(contingencyListIdsByGroup, headers);

        return restTemplate.exchange(actionsServerBaseUri + path, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<Map<String, Long>>() { }).getBody();
    }

    public ContingencyListExportResult getContingencyList(List<UUID> contingencyListIds, UUID networkUuid, String variantId) {
        Objects.requireNonNull(contingencyListIds);
        for (UUID uuid : contingencyListIds) {
            Objects.requireNonNull(uuid, "UUID in the list cannot be null");
        }
        Objects.requireNonNull(networkUuid);

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + ACTIONS_API_VERSION + "/contingency-lists/export")
                .queryParam(NETWORK_UUID, networkUuid.toString());
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        uriComponentsBuilder.queryParam(QUERY_PARAM_CONTINGENCY_LIST_IDS, contingencyListIds);
        var path = uriComponentsBuilder.build().toUriString();

        return restTemplate.exchange(actionsServerBaseUri + path, HttpMethod.GET, null,
                new ParameterizedTypeReference<ContingencyListExportResult>() {
                }).getBody();
    }
}

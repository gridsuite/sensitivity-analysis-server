/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.sensitivityanalysis.server.dto.IdentifiableAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class FilterService {

    static final String FILTER_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private final RestTemplate restTemplate = new RestTemplate();
    private String filterServerBaseUri;
    private static final String QUERY_PARAM_VARIANT_ID = "variantId";

    @Autowired
    public FilterService(@Value("${backing-services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        this.filterServerBaseUri = filterServerBaseUri;
    }

    public List<IdentifiableAttributes> getIdentifiablesFromFilter(UUID uuid, UUID networkUuid, String variantId) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(networkUuid);

        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + FILTER_API_VERSION + "/filters/{id}/export")
            .queryParam("networkUuid", networkUuid.toString());
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(uuid).toUriString();

        return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null,
            new ParameterizedTypeReference<List<IdentifiableAttributes>>() {
            }).getBody();
    }
}

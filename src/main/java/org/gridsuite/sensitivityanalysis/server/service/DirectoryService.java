/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import lombok.Setter;
import org.gridsuite.sensitivityanalysis.server.dto.ElementAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.gridsuite.computation.service.NotificationService.HEADER_USER_ID;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Service
public class DirectoryService {
    static final String DIRECTORY_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    @Setter
    private String baseUri;

    private final RestTemplate restTemplate;

    public DirectoryService(@Value("${gridsuite.services.directory-server.base-uri:http://directory-server/}") String baseUri, RestTemplate restTemplate) {
        this.baseUri = baseUri;
        this.restTemplate = restTemplate;
    }

    public Map<UUID, String> getElementNames(Set<UUID> elementUuids, String userId) {
        Objects.requireNonNull(elementUuids);

        if (elementUuids.isEmpty()) {
            return Map.of();
        }

        URI path = UriComponentsBuilder
                .fromPath(DELIMITER + DIRECTORY_API_VERSION + "/elements")
                .queryParam("ids", elementUuids)
                .queryParam("strictMode", "false") // to ignore non existing elements error
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            List<ElementAttributes> elementAttributes =
                    restTemplate.exchange(
                            baseUri + path,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            new ParameterizedTypeReference<List<ElementAttributes>>() { }
                    ).getBody();

            return elementAttributes == null ? Map.of()
                    : elementAttributes.stream().collect(Collectors.toMap(ElementAttributes::getElementUuid, ElementAttributes::getElementName));

        } catch (HttpClientErrorException e) {
            return Map.of();
        }
    }
}

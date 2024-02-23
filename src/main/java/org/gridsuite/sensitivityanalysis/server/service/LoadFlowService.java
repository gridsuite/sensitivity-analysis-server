/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.service;

import org.gridsuite.sensitivityanalysis.server.dto.parameters.LoadFlowParametersValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Service
public class LoadFlowService {

    static final String LAODFLOW_API_VERSION = "v1";

    private static final String DELIMITER = "/";

    private String baseUri;

    private final RestTemplate restTemplate;

    @Autowired
    public LoadFlowService(@Value("${gridsuite.services.loadflow-server.base-uri:http://loadflow-server/}") String baseUri, RestTemplate restTemplate) {
        this.baseUri = baseUri;
        this.restTemplate = restTemplate;
    }

    public void setLoadFlowServiceBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public LoadFlowParametersValues getLoadFlowParameters(UUID parametersUuid, String provider) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LAODFLOW_API_VERSION + "/parameters/{parametersUuid}/values")
            .queryParam("provider", provider)
            .buildAndExpand(parametersUuid).toUriString();
        return restTemplate.getForObject(baseUri + path, LoadFlowParametersValues.class);
    }
}

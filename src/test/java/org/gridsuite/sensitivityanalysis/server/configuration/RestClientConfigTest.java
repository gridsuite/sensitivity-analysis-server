/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.sensitivityanalysis.server.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientConfigTest {
    @Test
    void suppliesJackson2WhenMissing() {
        RestClient.Builder builder = RestClient.builder().messageConverters(converters ->
            converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance));
        assertNonNumericNumbers(builder);
    }

    @Test
    void replacesExistingJackson2() {
        RestClient.Builder builder = RestClient.builder().messageConverters(converters -> {
            converters.clear();
            converters.add(new MappingJackson2HttpMessageConverter());
        });
        assertNonNumericNumbers(builder);
    }

    private void assertNonNumericNumbers(RestClient.Builder builder) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = new RestClientConfig().restClient(builder);
        server.expect(requestTo("/value")).andRespond(withSuccess("NaN", MediaType.APPLICATION_JSON));
        assertEquals(Double.NaN, client.get().uri("/value").retrieve().body(Double.class));
        server.verify();
    }
}

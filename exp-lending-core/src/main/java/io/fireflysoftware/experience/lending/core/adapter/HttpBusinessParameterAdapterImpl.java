/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.experience.lending.core.adapter;

import io.fireflysoftware.domain.configuration.sdk.api.BusinessParametersApi;
import io.fireflysoftware.domain.configuration.sdk.invoker.ApiClient;
import io.fireflysoftware.experience.lending.core.product.BusinessParameterPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reads one business parameter through the generated domain-core-configuration SDK.
 * The operation is {@code GET /api/v1/parameters/by-key/{key}}.
 * This class does not call core-common-config-mgmt and does not copy the sandbox table.
 */
@Component("httpBusinessParameterAdapter")
public class HttpBusinessParameterAdapterImpl implements BusinessParameterPort {

    private final BusinessParametersApi parameters;

    @Autowired
    public HttpBusinessParameterAdapterImpl(
            @Value("${api-configuration.domain-platform.configuration.base-path:http://localhost:8094}") String basePath) {
        ApiClient client = new ApiClient();
        client.setBasePath(trim(basePath));
        this.parameters = new BusinessParametersApi(client);
    }

    HttpBusinessParameterAdapterImpl(BusinessParametersApi parameters) {
        this.parameters = parameters;
    }

    @Override
    public Mono<String> findByKey(String key) {
        return parameters.getBusinessParameterByKey(key)
                .flatMap(parameter -> {
                    String value = parameter == null ? null : parameter.getValue();
                    return value == null ? Mono.empty() : Mono.just(value);
                });
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("domain-core-configuration base path is not set");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.experience.lending.core.product.CreditProductRoute;
import io.fireflysoftware.experience.lending.core.product.LendingCatalogPort;
import io.fireflysoftware.experience.lending.core.product.LoanCustomerOperation;
import io.fireflysoftware.experience.lending.core.product.ProductRoute;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic product routes. An installer replaces this class with domain-core-configuration.
 */
@Component("sandboxLendingCatalogAdapter")
@ConditionalOnProperty(name = "firefly.experience.lending.catalog", havingValue = "sandbox", matchIfMissing = true)
public class SandboxLendingCatalogAdapterImpl implements LendingCatalogPort {

    private final CreditProductRoute defaultRoute;
    private final Map<UUID, ProductRoute> products;
    private final Map<String, String> loanOperations;

    public SandboxLendingCatalogAdapterImpl() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource("lending-product-routes.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            this.defaultRoute = CreditProductRoute.valueOf(root.get("defaultRoute").asText());
            this.loanOperations = mapper.convertValue(root.get("loanServicing"), new TypeReference<>() {
            });
            Map<UUID, ProductRoute> loaded = new HashMap<>();
            for (JsonNode product : root.get("products")) {
                UUID id = UUID.fromString(product.get("productId").asText());
                loaded.put(id, new ProductRoute(
                        CreditProductRoute.valueOf(product.get("route").asText()),
                        product.get("createPath").asText(),
                        product.get("idField").asText(),
                        mapper.convertValue(product.get("attributes"), new TypeReference<>() {
                        }),
                        mapper.convertValue(product.get("operations"), new TypeReference<>() {
                        })));
            }
            this.products = Map.copyOf(loaded);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public Mono<ProductRoute> routeFor(UUID productId) {
        if (productId != null && products.containsKey(productId)) {
            return Mono.just(products.get(productId));
        }
        if (defaultRoute == CreditProductRoute.LOAN_ORIGINATION) {
            return Mono.just(ProductRoute.origination());
        }
        return Mono.error(new BusinessException(HttpStatus.CONFLICT, "ROUTE_NOT_CONFIGURED",
                "No product route is configured"));
    }

    @Override
    public Mono<String> loanOperation(LoanCustomerOperation operation) {
        String path = loanOperations.get(operation.name());
        if (path == null || path.isBlank()) {
            return Mono.error(new BusinessException(HttpStatus.CONFLICT, "JOURNEY_NOT_ENABLED",
                    operation.name() + " is not enabled"));
        }
        return Mono.just(path);
    }
}

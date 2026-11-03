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

package io.fireflysoftware.experience.lending.web.simulation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.json.JsonMapper;

import io.fireflysoftware.domain.core.pricing.engine.sdk.api.PricingEngineSimulationApi;
import io.fireflysoftware.domain.core.pricing.engine.sdk.model.SimulationCalculationResultDTO;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.LoanOriginationApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.SimulationDTO;
import io.fireflysoftware.domain.product.pricing.sdk.api.EligibilityApi;
import io.fireflysoftware.domain.product.pricing.sdk.api.PricingApi;
import io.fireflysoftware.experience.lending.core.simulation.services.*;
import io.fireflysoftware.experience.lending.core.simulation.services.impl.*;
import io.fireflysoftware.experience.lending.web.controllers.SimulationController;

import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.handler.GlobalExceptionHandler;
import org.fireflyframework.web.error.service.ErrorResponseNegotiator;
import org.fireflyframework.web.idempotency.cache.IdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.filter.IdempotencyWebFilter;
import org.fireflyframework.web.idempotency.model.CachedResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.EnableWebFlux;

import reactor.core.publisher.Mono;

import java.util.*;

class SimulationAvailabilityHttpTest {
    AnnotationConfigApplicationContext context;
    final List<AnnotationConfigApplicationContext> warmContexts = new ArrayList<>();
    WebTestClient http;
    PricingApi pricing;
    EligibilityApi eligibility;
    PricingEngineSimulationApi calculation;
    LoanOriginationApi origination;

    @BeforeEach
    void setup() {
        pricing = mock(PricingApi.class);
        eligibility = mock(EligibilityApi.class);
        calculation = mock(PricingEngineSimulationApi.class);
        origination = mock(LoanOriginationApi.class);
        when(pricing.registerPricing(any(), any()))
                .thenReturn(Mono.just(Map.of("configValue", "10000")));
        when(calculation.calculateSimulation(any(), any()))
                .thenReturn(Mono.just(new SimulationCalculationResultDTO()));
        when(origination.persistLendingSimulation(any(), any()))
                .thenReturn(Mono.just(new SimulationDTO()));
        context = new AnnotationConfigApplicationContext();
        context.registerBean("productPricingApi", PricingApi.class, () -> pricing);
        context.registerBean(
                "productPricingEligibilityApi", EligibilityApi.class, () -> eligibility);
        context.registerBean(PricingEngineSimulationApi.class, () -> calculation);
        context.registerBean(LoanOriginationApi.class, () -> origination);
        context.register(SimulationServiceImpl.class, SimulationConfigurationServiceImpl.class);
        context.refresh();
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var errors =
                new GlobalExceptionHandler(
                        new ExceptionConverterService(List.of()),
                        Optional.empty(),
                        new ErrorHandlingProperties(),
                        Optional.empty(),
                        Optional.empty(),
                        new MockEnvironment(),
                        mapper,
                        new ErrorResponseNegotiator(mapper),
                        Optional.empty());
        http =
                WebTestClient.bindToController(
                                new SimulationController(
                                        context.getBean(SimulationService.class),
                                        context.getBean(SimulationConfigurationService.class)))
                        .controllerAdvice(errors)
                        .build();
        clearInvocations(pricing, eligibility, calculation, origination);
    }

    @AfterEach
    void close() {
        warmContexts.forEach(AnnotationConfigApplicationContext::close);
        context.close();
    }

    void unavailable(String path, String body) {
        http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("LENDING_TERMS_UNAVAILABLE")
                .jsonPath("$.message")
                .isEqualTo("Verified lending product terms are unavailable")
                .jsonPath("$.simulationId")
                .doesNotExist()
                .jsonPath("$.annualRate")
                .doesNotExist()
                .jsonPath("$.monthlyPayment")
                .doesNotExist()
                .jsonPath("$.offerId")
                .doesNotExist()
                .jsonPath("$.productConfigurationId")
                .doesNotExist();
        verifyNoInteractions(pricing, eligibility, calculation, origination);
    }

    @Test
    void realSimulationServiceReturnsExplicitUnavailableWithoutAdministrativeWrites() {
        unavailable(
                "/api/v1/experience/lending/simulations",
                "{\"amount\":10000,\"term\":24,\"productType\":\"PERSONAL_LOAN\"}");
    }

    @Test
    void realConfigureServiceReturnsUnavailableWithoutPersistingUnverifiedCalculation() {
        unavailable(
                "/api/v1/experience/lending/simulations/configure",
                "{\"requestedAmount\":10000,\"term\":24,\"productType\":\"PERSONAL_LOAN\",\"purpose\":\"car\"}");
    }

    @Test
    void existingBeanValidationReturnsBadRequestWithoutCallingEitherDownstream() {
        http.post()
                .uri("/api/v1/experience/lending/simulations/configure")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"requestedAmount\":-1,\"term\":0,\"productType\":\"PERSONAL_LOAN\"}")
                .exchange()
                .expectStatus()
                .isBadRequest();
        verifyNoInteractions(pricing, eligibility, calculation, origination);
    }

    @Test
    void existingServiceCrossFieldValidationReturnsItsTypedBadRequest() {
        http.post()
                .uri("/api/v1/experience/lending/simulations/configure")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        "{\"requestedAmount\":10000,\"term\":24,\"productType\":\"PERSONAL_LOAN\"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("SIMULATION_INVALID_CONFIG");
        verifyNoInteractions(pricing, eligibility, calculation, origination);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    static class WebConfiguration {}

    @RestController
    static class HistoricalSimulationController {
        int calls;

        @PostMapping(
                value = {
                    "/api/v1/experience/lending/simulations",
                    "/api/v1/experience/lending/simulations/configure"
                },
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<Map<String, Object>> historical(
                @RequestBody Map<String, Object> ignoredBody) {
            calls++;
            return ResponseEntity.status(201)
                    .body(
                            Map.of(
                                    "simulationId",
                                    "00000000-0000-4000-8000-000000000001",
                                    "annualRate",
                                    10000));
        }
    }

    WebTestClient cachedClient(Object controller, IdempotencyCache cache) {
        var app = new AnnotationConfigApplicationContext();
        warmContexts.add(app);
        app.register(WebConfiguration.class);
        if (controller instanceof HistoricalSimulationController historical) {
            app.registerBean(HistoricalSimulationController.class, () -> historical);
        } else {
            app.registerBean(SimulationController.class, () -> (SimulationController) controller);
        }
        var mapper = JsonMapper.builder().findAndAddModules().build();
        app.registerBean(
                GlobalExceptionHandler.class,
                () ->
                        new GlobalExceptionHandler(
                                new ExceptionConverterService(List.of()),
                                Optional.empty(),
                                new ErrorHandlingProperties(),
                                Optional.empty(),
                                Optional.empty(),
                                new MockEnvironment(),
                                mapper,
                                new ErrorResponseNegotiator(mapper),
                                Optional.empty()));
        var properties = new IdempotencyProperties();
        properties.setEnabled(true);
        properties.setNamespace("synthetic-lending-simulation-compatibility");
        app.registerBean(
                IdempotencyWebFilter.class,
                () ->
                        new IdempotencyWebFilter(
                                properties,
                                cache,
                                exchange -> Mono.just("synthetic-current-scope"),
                                () ->
                                        new ArrayList<>(
                                                app.getBeansOfType(HandlerMapping.class)
                                                        .values())));
        app.refresh();
        return WebTestClient.bindToApplicationContext(app).build();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/experience/lending/simulations",
                "/api/v1/experience/lending/simulations/configure"
            })
    void historicalCachedSuccessCannotBypassTheCorrectedUnavailableBoundary(String path) {
        var cache = mock(IdempotencyCache.class);
        var retained = new HashMap<String, CachedResponse>();
        when(cache.get(any()))
                .thenAnswer(call -> Mono.justOrEmpty(retained.get(call.getArgument(0))));
        when(cache.put(any(), any()))
                .thenAnswer(
                        call -> {
                            retained.put(call.getArgument(0), call.getArgument(1));
                            return Mono.empty();
                        });
        String body =
                path.endsWith("configure")
                        ? "{\"requestedAmount\":10000,\"term\":24,\"productType\":\"PERSONAL_LOAN\",\"purpose\":\"car\"}"
                        : "{\"amount\":10000,\"term\":24,\"productType\":\"PERSONAL_LOAN\"}";
        var historical = new HistoricalSimulationController();
        var old = cachedClient(historical, cache);
        old.post()
                .uri(path)
                .header("X-Idempotency-Key", "synthetic-same-operation")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isCreated();
        org.assertj.core.api.Assertions.assertThat(retained).hasSize(1);
        old.post()
                .uri(path)
                .header("X-Idempotency-Key", "synthetic-same-operation")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.annualRate")
                .isEqualTo(10000);
        org.assertj.core.api.Assertions.assertThat(historical.calls).isEqualTo(1);
        clearInvocations(cache);
        var current =
                cachedClient(
                        new SimulationController(
                                context.getBean(SimulationService.class),
                                context.getBean(SimulationConfigurationService.class)),
                        cache);
        current.post()
                .uri(path)
                .header("X-Idempotency-Key", "synthetic-same-operation")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("LENDING_TERMS_UNAVAILABLE")
                .jsonPath("$.simulationId")
                .doesNotExist()
                .jsonPath("$.annualRate")
                .doesNotExist();
        verifyNoInteractions(cache, pricing, eligibility, calculation, origination);
    }
}

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

package io.fireflysoftware.experience.lending.core.simulation.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.fireflysoftware.domain.product.pricing.sdk.api.EligibilityApi;
import io.fireflysoftware.domain.product.pricing.sdk.api.PricingApi;
import io.fireflysoftware.experience.lending.core.simulation.commands.CheckEligibilityCommand;
import io.fireflysoftware.experience.lending.core.simulation.commands.CreateSimulationCommand;
import io.fireflysoftware.experience.lending.core.simulation.services.impl.SimulationServiceImpl;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class SimulationServiceImplTest {

    @Mock private PricingApi pricingApi;

    @Mock private EligibilityApi eligibilityApi;

    private SimulationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SimulationServiceImpl(eligibilityApi);
    }

    @Test
    void createSimulation_requiresVerifiedOwnerTermsWithoutPricingWrites() {
        var command =
                new CreateSimulationCommand(
                        new BigDecimal("10000"), 24, "PERSONAL_LOAN", UUID.randomUUID());
        StepVerifier.create(service.createSimulation(command))
                .expectErrorSatisfies(
                        error ->
                                assertThat(error)
                                        .isInstanceOfSatisfying(
                                                BusinessException.class,
                                                unavailable -> {
                                                    assertThat(unavailable.getStatus())
                                                            .isEqualTo(
                                                                    HttpStatus.SERVICE_UNAVAILABLE);
                                                    assertThat(unavailable.getCode())
                                                            .isEqualTo("LENDING_TERMS_UNAVAILABLE");
                                                }))
                .verify();
        verifyNoInteractions(pricingApi, eligibilityApi);
    }

    @Test
    void createSimulation_repeatedSubscriptionsDoNotCallAnUpstream() {
        var command =
                new CreateSimulationCommand(
                        new BigDecimal("10000"), 24, "PERSONAL_LOAN", UUID.randomUUID());
        var result = service.createSimulation(command);
        StepVerifier.create(result).expectError(BusinessException.class).verify();
        StepVerifier.create(result).expectError(BusinessException.class).verify();
        verifyNoInteractions(pricingApi, eligibilityApi);
    }

    @Test
    void checkEligibility_delegatesToEligibilityApi_andMapsResult() {
        var productId = UUID.randomUUID();
        var partyId = UUID.randomUUID();
        var evaluationId = UUID.randomUUID();
        var apiResponse =
                new io.fireflysoftware.domain.product.pricing.sdk.model.EligibilityResultDTO()
                        .evaluationId(evaluationId)
                        .eligible(true)
                        .maxAmount(new BigDecimal("50000"));

        var command = new CheckEligibilityCommand();
        command.setPartyId(partyId);
        command.setProductId(productId);
        command.setRequestedAmount(new BigDecimal("30000"));

        when(eligibilityApi.evaluateEligibility(eq(productId), any(), any()))
                .thenReturn(Mono.just(apiResponse));

        StepVerifier.create(service.checkEligibility(command))
                .assertNext(
                        result -> {
                            assertThat(result.getEvaluationId()).isEqualTo(evaluationId);
                            assertThat(result.isEligible()).isTrue();
                            assertThat(result.getMaxAmount()).isEqualByComparingTo("50000");
                            assertThat(result.getReasons()).isEmpty();
                        })
                .verifyComplete();
    }

    @Test
    void checkEligibility_mapsIneligibleResponse() {
        var productId = UUID.randomUUID();
        var apiResponse =
                new io.fireflysoftware.domain.product.pricing.sdk.model.EligibilityResultDTO()
                        .evaluationId(UUID.randomUUID())
                        .eligible(false)
                        .reasons(java.util.List.of("AMOUNT_ABOVE_MAXIMUM"));

        var command = new CheckEligibilityCommand();
        command.setPartyId(UUID.randomUUID());
        command.setProductId(productId);
        command.setRequestedAmount(new BigDecimal("999999"));

        when(eligibilityApi.evaluateEligibility(eq(productId), any(), any()))
                .thenReturn(Mono.just(apiResponse));

        StepVerifier.create(service.checkEligibility(command))
                .assertNext(
                        result -> {
                            assertThat(result.isEligible()).isFalse();
                            assertThat(result.getReasons()).containsExactly("AMOUNT_ABOVE_MAXIMUM");
                        })
                .verifyComplete();
    }
}

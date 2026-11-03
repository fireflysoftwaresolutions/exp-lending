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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.fireflysoftware.domain.core.pricing.engine.sdk.api.PricingEngineSimulationApi;
import io.fireflysoftware.domain.core.pricing.engine.sdk.model.SimulationCalculationResultDTO;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.LoanOriginationApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.SimulationDTO;
import io.fireflysoftware.domain.product.pricing.sdk.api.EligibilityApi;
import io.fireflysoftware.domain.product.pricing.sdk.api.PricingApi;
import io.fireflysoftware.experience.lending.core.simulation.commands.*;
import io.fireflysoftware.experience.lending.core.simulation.services.impl.*;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;

class SimulationAvailabilityTest {
    AnnotationConfigApplicationContext context;
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
                .thenReturn(
                        Mono.just(
                                Map.of(
                                        "productConfigurationId",
                                        UUID.randomUUID().toString(),
                                        "configValue",
                                        "10000")));
        when(calculation.calculateSimulation(any(), any()))
                .thenReturn(
                        Mono.just(
                                new SimulationCalculationResultDTO()
                                        .monthlyPayment(new BigDecimal("450"))));
        when(origination.persistLendingSimulation(any(), any()))
                .thenReturn(Mono.just(new SimulationDTO().simulationId(UUID.randomUUID())));
        context = new AnnotationConfigApplicationContext();
        context.registerBean("productPricingApi", PricingApi.class, () -> pricing);
        context.registerBean(
                "productPricingEligibilityApi", EligibilityApi.class, () -> eligibility);
        context.registerBean(PricingEngineSimulationApi.class, () -> calculation);
        context.registerBean(LoanOriginationApi.class, () -> origination);
        context.register(SimulationServiceImpl.class, SimulationConfigurationServiceImpl.class);
        context.refresh();
        clearInvocations(pricing, eligibility, calculation, origination);
    }

    @AfterEach
    void close() {
        context.close();
    }

    static void unavailable(reactor.core.publisher.Signal<?> signal) {
        assertThat(signal.isOnError()).as("no fabricated successful calculation").isTrue();
        assertThat(signal.getThrowable())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                            assertThat(error.getCode()).isEqualTo("LENDING_TERMS_UNAVAILABLE");
                            assertThat(error.getMessage())
                                    .isEqualTo("Verified lending product terms are unavailable");
                            assertThat(error.getMetadata()).isEmpty();
                        });
    }

    @Test
    void simulationNeverRegistersAdministrativePricingOrInventsARate() {
        var command =
                new CreateSimulationCommand(
                        new BigDecimal("10000"), 24, "PERSONAL_LOAN", UUID.randomUUID());
        var signal =
                context.getBean(SimulationService.class)
                        .createSimulation(command)
                        .materialize()
                        .block();
        assertAll(
                () -> unavailable(signal),
                () -> verifyNoInteractions(pricing, eligibility, calculation, origination));
    }

    static ConfigureSimulationCommand valid(String profile) {
        return ConfigureSimulationCommand.builder()
                .productType(profile)
                .productId(UUID.randomUUID())
                .requestedAmount(new BigDecimal("10000"))
                .term(24)
                .purpose("car")
                .sector("manufacturing")
                .assetType("equipment")
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERSONAL_LOAN", "LEASING"})
    void configurationNeverCalculatesOrPersistsUnverifiedValues(String profile) {
        var signal =
                context.getBean(SimulationConfigurationService.class)
                        .configure(valid(profile))
                        .materialize()
                        .block();
        assertAll(
                () -> unavailable(signal),
                () -> verifyNoInteractions(pricing, eligibility, calculation, origination));
    }

    @ParameterizedTest
    @ValueSource(strings = {"purpose", "sector", "assetType"})
    void existingCrossFieldFailuresRemainBadRequestWithoutDownstreamCalls(String missing) {
        var command = valid(missing.equals("purpose") ? "PERSONAL_LOAN" : "LEASING");
        switch (missing) {
            case "purpose" -> command.setPurpose(" ");
            case "sector" -> command.setSector(null);
            case "assetType" -> command.setAssetType("");
        }
        var signal =
                context.getBean(SimulationConfigurationService.class)
                        .configure(command)
                        .materialize()
                        .block();
        assertThat(signal.getThrowable())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> {
                            assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(error.getCode()).isEqualTo("SIMULATION_INVALID_CONFIG");
                        });
        verifyNoInteractions(pricing, eligibility, calculation, origination);
    }
}

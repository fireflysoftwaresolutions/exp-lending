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

package io.fireflysoftware.experience.lending.core.simulation.services.impl;

import io.fireflysoftware.domain.product.pricing.sdk.api.EligibilityApi;
import io.fireflysoftware.domain.product.pricing.sdk.model.EvaluateEligibilityCommand;
import io.fireflysoftware.experience.lending.core.simulation.commands.CheckEligibilityCommand;
import io.fireflysoftware.experience.lending.core.simulation.commands.CreateSimulationCommand;
import io.fireflysoftware.experience.lending.core.simulation.queries.EligibilityResultDTO;
import io.fireflysoftware.experience.lending.core.simulation.queries.SimulationResultDTO;
import io.fireflysoftware.experience.lending.core.simulation.services.SimulationService;

import lombok.extern.slf4j.Slf4j;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Keeps simulation unavailable until verified owner terms can be observed. Eligibility remains
 * delegated to the Product Pricing Eligibility API.
 */
@Slf4j
@Service
public class SimulationServiceImpl implements SimulationService {

    private final EligibilityApi eligibilityApi;

    public SimulationServiceImpl(
            @Qualifier("productPricingEligibilityApi") EligibilityApi eligibilityApi) {
        this.eligibilityApi = eligibilityApi;
    }

    @Override
    public Mono<SimulationResultDTO> createSimulation(CreateSimulationCommand command) {
        return Mono.error(
                new BusinessException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "LENDING_TERMS_UNAVAILABLE",
                        "Verified lending product terms are unavailable"));
    }

    @Override
    public Mono<EligibilityResultDTO> checkEligibility(CheckEligibilityCommand command) {
        log.debug("Checking eligibility for productId={}", command.getProductId());

        EvaluateEligibilityCommand body =
                new EvaluateEligibilityCommand()
                        .partyId(command.getPartyId())
                        .productId(command.getProductId())
                        .requestedAmount(command.getRequestedAmount());

        return eligibilityApi
                .evaluateEligibility(command.getProductId(), body, UUID.randomUUID().toString())
                .map(
                        result ->
                                EligibilityResultDTO.builder()
                                        .evaluationId(result.getEvaluationId())
                                        .eligible(Boolean.TRUE.equals(result.getEligible()))
                                        .maxAmount(result.getMaxAmount())
                                        .reasons(
                                                result.getReasons() != null
                                                        ? result.getReasons()
                                                        : List.of())
                                        .build());
    }
}

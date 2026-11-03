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

package io.fireflysoftware.experience.lending.web.controllers;

import io.fireflysoftware.experience.lending.core.simulation.commands.CheckEligibilityCommand;
import io.fireflysoftware.experience.lending.core.simulation.commands.ConfigureSimulationCommand;
import io.fireflysoftware.experience.lending.core.simulation.commands.CreateSimulationCommand;
import io.fireflysoftware.experience.lending.core.simulation.queries.ConfiguredSimulationDTO;
import io.fireflysoftware.experience.lending.core.simulation.queries.EligibilityResultDTO;
import io.fireflysoftware.experience.lending.core.simulation.queries.SimulationResultDTO;
import io.fireflysoftware.experience.lending.core.simulation.services.SimulationConfigurationService;
import io.fireflysoftware.experience.lending.core.simulation.services.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.fireflyframework.web.error.models.ErrorResponse;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

/** REST controller exposing the loan simulation and product eligibility evaluation endpoints. */
@RestController
@RequestMapping("/api/v1/experience/lending")
@RequiredArgsConstructor
@Tag(name = "Lending - Simulation & Eligibility")
public class SimulationController {

    private final SimulationService simulationService;
    private final SimulationConfigurationService simulationConfigurationService;

    @DisableIdempotency
    @PostMapping(
            value = "/simulations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create Simulation",
            parameters =
                    @Parameter(
                            name = "X-Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description =
                                    "Accepted for SDK compatibility and ignored. Current verified"
                                        + " owner terms are required for simulation availability.",
                            schema = @Schema(type = "string")),
            description =
                    "Currently returns LENDING_TERMS_UNAVAILABLE until exact verified product terms"
                            + " are available.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Historical success schema retained for compatible clients",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = SimulationResultDTO.class))),
        @ApiResponse(
                responseCode = "503",
                description = "LENDING_TERMS_UNAVAILABLE",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<SimulationResultDTO>> createSimulation(
            @RequestBody CreateSimulationCommand command) {
        return simulationService
                .createSimulation(command)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(result));
    }

    @DisableIdempotency
    @PostMapping(
            value = "/simulations/configure",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "configureLendingSimulation",
            summary = "Configure Simulation",
            parameters =
                    @Parameter(
                            name = "X-Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description =
                                    "Accepted for SDK compatibility and ignored. Current verified"
                                        + " owner terms are required for simulation availability.",
                            schema = @Schema(type = "string")),
            description =
                    "Validates configuration and returns LENDING_TERMS_UNAVAILABLE until exact"
                            + " verified product terms are available. No calculation is persisted.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Historical success schema retained for compatible clients",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ConfiguredSimulationDTO.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(
                responseCode = "503",
                description = "LENDING_TERMS_UNAVAILABLE",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ResponseEntity<ConfiguredSimulationDTO>> configureSimulation(
            @Valid @RequestBody ConfigureSimulationCommand command) {
        return simulationConfigurationService
                .configure(command)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(result));
    }

    @PostMapping(
            value = "/eligibility",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Check Eligibility",
            description =
                    "Evaluates whether a party is eligible for a given product and requested"
                            + " amount.")
    public Mono<ResponseEntity<EligibilityResultDTO>> checkEligibility(
            @RequestBody CheckEligibilityCommand command) {
        return simulationService.checkEligibility(command).map(ResponseEntity::ok);
    }
}

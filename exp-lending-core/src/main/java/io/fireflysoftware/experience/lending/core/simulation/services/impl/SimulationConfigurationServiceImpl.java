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

import io.fireflysoftware.experience.lending.core.simulation.commands.ConfigureSimulationCommand;
import io.fireflysoftware.experience.lending.core.simulation.queries.ConfiguredSimulationDTO;
import io.fireflysoftware.experience.lending.core.simulation.services.SimulationConfigurationService;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/** Validates configuration inputs without persisting an unverified calculation. */
@Service
public class SimulationConfigurationServiceImpl implements SimulationConfigurationService {
    private static final String PRODUCT_TYPE_PERSONAL_LOAN = "PERSONAL_LOAN";
    private static final String PRODUCT_TYPE_LEASING = "LEASING";

    @Override
    public Mono<ConfiguredSimulationDTO> configure(ConfigureSimulationCommand command) {
        return Mono.fromCallable(() -> validate(command))
                .then(
                        Mono.error(
                                new BusinessException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "LENDING_TERMS_UNAVAILABLE",
                                        "Verified lending product terms are unavailable")));
    }

    private ConfigureSimulationCommand validate(ConfigureSimulationCommand cmd) {
        String productType = cmd.getProductType();
        if (PRODUCT_TYPE_PERSONAL_LOAN.equals(productType)) {
            if (isBlank(cmd.getPurpose())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "SIMULATION_INVALID_CONFIG",
                        "purpose is required when productType=PERSONAL_LOAN");
            }
        } else if (PRODUCT_TYPE_LEASING.equals(productType)) {
            if (isBlank(cmd.getSector()) || isBlank(cmd.getAssetType())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "SIMULATION_INVALID_CONFIG",
                        "sector and assetType are required when productType=LEASING");
            }
        }
        return cmd;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

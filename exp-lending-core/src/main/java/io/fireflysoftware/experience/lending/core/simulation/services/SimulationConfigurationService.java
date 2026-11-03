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

import io.fireflysoftware.experience.lending.core.simulation.commands.ConfigureSimulationCommand;
import io.fireflysoftware.experience.lending.core.simulation.queries.ConfiguredSimulationDTO;

import reactor.core.publisher.Mono;

/**
 * Validates simulation configuration before verified owner terms are available. Unverified
 * calculations must not be persisted.
 */
public interface SimulationConfigurationService {

    /**
     * Retains existing input validation and explicitly reports unavailable owner terms.
     *
     * @param command validated input from the caller
     * @return a {@link Mono} failing with LENDING_TERMS_UNAVAILABLE after validation
     */
    Mono<ConfiguredSimulationDTO> configure(ConfigureSimulationCommand command);
}

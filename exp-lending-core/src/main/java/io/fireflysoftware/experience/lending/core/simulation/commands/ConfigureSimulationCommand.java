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

package io.fireflysoftware.experience.lending.core.simulation.commands;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input retained for lending simulation configuration. Existing validation remains active while
 * verified owner terms are unavailable; no unverified calculation is persisted.
 *
 * <p>Cross-field rules (validated in the service layer):
 *
 * <ul>
 *   <li>{@code productType=PERSONAL_LOAN} requires {@code purpose}.
 *   <li>{@code productType=LEASING} requires {@code sector} and {@code assetType}.
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigureSimulationCommand {

    /** Product type discriminator. Currently {@code PERSONAL_LOAN} or {@code LEASING}. */
    @NotBlank
    @Pattern(regexp = "^(PERSONAL_LOAN|LEASING)$")
    private String productType;

    /** Optional explicit product identifier. Its omission does not select a default product. */
    private UUID productId;

    /** Requested principal amount. Must be strictly positive. */
    @NotNull @Positive private BigDecimal requestedAmount;

    /** Term in months. */
    @NotNull
    @Min(1)
    private Integer term;

    /** Required for {@code PERSONAL_LOAN}: e.g. {@code car}, {@code homeRenovation}. */
    private String purpose;

    /** Required for {@code LEASING}: business sector. */
    private String sector;

    /** Required for {@code LEASING}: asset type. */
    private String assetType;
}

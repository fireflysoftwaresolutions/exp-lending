/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record ApplicationInitialContractError(@NotNull UUID actionId,@NotNull String code,@NotNull Outcome outcome,@NotNull String recovery){
    @Schema(name="ApplicationInitialContractOutcome") public enum Outcome {REFUSED,CONFLICT,OUTCOME_UNKNOWN,COMMITTED_RESPONSE_UNAVAILABLE,OBSERVATION_UNAVAILABLE}
}

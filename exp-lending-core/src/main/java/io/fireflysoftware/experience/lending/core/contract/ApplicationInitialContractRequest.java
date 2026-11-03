/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
/** Original action and optional existing contract only. Party and owner terms come from authenticated owners. */
public record ApplicationInitialContractRequest(@NotNull UUID actionId,UUID existingContractId) {
    public ApplicationInitialContractRequest {requireId(actionId);if(existingContractId!=null)requireId(existingContractId);}
    static UUID requireId(UUID value){if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Nonzero original identity required");return value;}
}

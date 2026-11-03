/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
/** Historical Core Contract link receipt. Does not imply activation, signing or a Loan row transition. */
public record ApplicationInitialContractReceipt(@NotNull UUID actionId,@NotNull UUID applicationId,@NotNull UUID resourceTenantId,@NotNull UUID partyId,
        @NotNull UUID roleInContractId,@NotNull UUID contractId,@NotNull UUID contractPartyId,@NotNull UUID productId,@NotNull String currencyCode,
        @NotNull String purpose,@NotNull String contractStatusAtLink,boolean contractCreated,
        @Schema(ref="#/components/schemas/FireflyInstant",requiredMode=Schema.RequiredMode.REQUIRED) Instant linkedAt,@NotNull String intentDigest,boolean replayed) {
    public ApplicationInitialContractReceipt {
        for(UUID id:Arrays.asList(actionId,applicationId,resourceTenantId,partyId,roleInContractId,contractId,contractPartyId,productId))ApplicationInitialContractRequest.requireId(id);
        if(currencyCode==null||!currencyCode.matches("[A-Z]{3}")||!"INITIAL_PRIMARY_LOAN_CONTRACT".equals(purpose)||!Set.of("DRAFT","ACTIVE").contains(contractStatusAtLink)||linkedAt==null||intentDigest==null||!intentDigest.matches("[0-9a-f]{64}")||contractCreated&&!"DRAFT".equals(contractStatusAtLink))throw new IllegalArgumentException("Exact immutable initial Core Contract receipt required");
    }
    @Override public String toString(){return "ApplicationInitialContractReceipt[redacted]";}
}

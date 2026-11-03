/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ApplicationInitialContractReceiptTest {
    private static ApplicationInitialContractReceipt receipt(UUID action,String purpose,String status,boolean created){return new ApplicationInitialContractReceipt(action,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"EUR",purpose,status,created,Instant.parse("2026-11-02T00:00:00.123456Z"),"a".repeat(64),false);}
    @Test void originalNonzeroActionAndOptionalExistingIdentityAreMandatory(){assertThrows(IllegalArgumentException.class,()->new ApplicationInitialContractRequest(new UUID(0,0),null));assertThrows(IllegalArgumentException.class,()->new ApplicationInitialContractRequest(UUID.randomUUID(),new UUID(0,0)));var action=UUID.randomUUID();assertEquals(action,new ApplicationInitialContractRequest(action,null).actionId());}
    @Test void immutableOwnerReceiptPreservesActualIdentitiesAndInstant(){var action=UUID.randomUUID();var value=receipt(action,"INITIAL_PRIMARY_LOAN_CONTRACT","DRAFT",true);assertEquals(action,value.actionId());assertEquals(Instant.parse("2026-11-02T00:00:00.123456Z"),value.linkedAt());assertTrue(value.contractCreated());assertFalse(value.toString().contains(action.toString()));}
    @Test void unrelatedPurposeAndInventedCreatedActiveStatusAreRejected(){assertThrows(IllegalArgumentException.class,()->receipt(UUID.randomUUID(),"GUARANTEE","DRAFT",true));assertThrows(IllegalArgumentException.class,()->receipt(UUID.randomUUID(),"INITIAL_PRIMARY_LOAN_CONTRACT","ACTIVE",true));assertDoesNotThrow(()->receipt(UUID.randomUUID(),"INITIAL_PRIMARY_LOAN_CONTRACT","ACTIVE",false));}
    @Test void knownCommitFailureRetainsOriginalActionAndRecovery(){var action=UUID.randomUUID();var failure=new ApplicationInitialContractFailure(action,ApplicationInitialContractError.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);assertEquals(action,failure.actionId());assertEquals(ApplicationInitialContractError.Outcome.COMMITTED_RESPONSE_UNAVAILABLE,failure.outcome());assertEquals(503,failure.getStatus().value());assertThrows(IllegalArgumentException.class,()->new ApplicationInitialContractPreworkRefusal(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));}
}

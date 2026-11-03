/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake.impl;

import io.fireflysoftware.experience.lending.core.intake.*;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.DraftLoanIntakeReceipt;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Typed receipt projection only; these cases do not exercise Domain transport or authenticate its receiver. */
class DraftApplicationReceiptProjectionTest {
    private static final String DIGEST="a".repeat(64);
    private static final Instant CREATED=Instant.parse("2026-11-01T10:00:00.123456Z");
    private final DraftApplicationRequest request=new DraftApplicationRequest(UUID.fromString("b693b1ad-8cd5-4dc9-b4d1-1da08bcc634c"),
        UUID.fromString("a8531d6e-6ed8-4ff2-afc0-a7fdcd8fe2bd"),UUID.fromString("3a5cf1ed-56cc-414d-be59-e2710af1aa94"),
        UUID.fromString("0582907d-1c4a-4452-8da7-5a72d5768871"),new BigDecimal("1234.50"),24,"EUR","vehicle");
    private DraftLoanIntakeReceipt owner(){return new DraftLoanIntakeReceipt().requestId(request.requestId()).applicationId(UUID.fromString("bd300421-c92e-4d39-9f0c-a60243372ec8"))
        .simulationId(request.simulationId()).productId(request.productId()).partyId(request.partyId()).requestedAmount(request.requestedAmount())
        .term(request.term()).currency(request.currency()).purpose(request.purpose()).initialStatusCode("DRAFT").initialSubStatusCode("PENDING_DOCUMENTS")
        .createdAt(LocalDateTime.ofInstant(CREATED,ZoneOffset.UTC)).replayed(false).intentDigest(DIGEST);}
    @Test void actualGeneratedDomainReceiptKeepsOpaqueOwnerDigestAndUtcMicrosecondTime(){
        var value=TokenExchangedDomainDraftIntakePortImpl.receipt(request,owner());
        assertThat(value.intentDigest()).isEqualTo(DIGEST);assertThat(value.createdAt()).isEqualTo(CREATED);
        assertThat(value.requestId()).isEqualTo(request.requestId());assertThat(value.applicationId()).isEqualTo(owner().getApplicationId());
        assertThat(value.requestedAmount()).isEqualByComparingTo(request.requestedAmount());assertThat(value.replayed()).isFalse();
        assertThat(TokenExchangedDomainDraftIntakePortImpl.receipt(request,owner().intentDigest("f".repeat(64))).intentDigest()).isEqualTo("f".repeat(64));
    }
    @Test void absentOrMalformedOwnerDigestCannotBecomeAnExperienceReceipt(){
        for(String digest:Arrays.asList(null,"not-a-digest","F".repeat(64),"a".repeat(63)))
            assertThatThrownBy(()->TokenExchangedDomainDraftIntakePortImpl.receipt(request,owner().intentDigest(digest))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void changedIdentityOrTermsCannotBeProjectedAsTheRequestedIntake(){
        for(Consumer<DraftLoanIntakeReceipt> change:List.<Consumer<DraftLoanIntakeReceipt>>of(value->value.setProductId(UUID.randomUUID()),
            value->value.setPartyId(UUID.randomUUID()),value->value.setRequestedAmount(new BigDecimal("1234.51")),value->value.setTerm(25),
            value->value.setCurrency("USD"),value->value.setPurpose(null),value->value.setInitialStatusCode("APPROVED"))){
            var receipt=owner();change.accept(receipt);
            assertThatThrownBy(()->TokenExchangedDomainDraftIntakePortImpl.receipt(request,receipt)).isInstanceOf(ResponseStatusException.class);
        }
    }
    @Test void replayKeepsExactlyTheSameOwnerIdentityAndDigest(){
        var created=TokenExchangedDomainDraftIntakePortImpl.receipt(request,owner());
        var replayed=TokenExchangedDomainDraftIntakePortImpl.receipt(request,owner().replayed(true));
        assertThat(replayed).usingRecursiveComparison().ignoringFields("replayed").isEqualTo(created);assertThat(replayed.replayed()).isTrue();
    }
}

/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake.impl;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import static org.assertj.core.api.Assertions.*;

/** Controlled framework error-envelope contracts only; no authenticated Domain transport acceptance. */
class DraftApplicationOutcomeContractTest {
    private final UUID action=UUID.randomUUID();
    private String code="LENDING_INTAKE_COMMITTED_RESPONSE_UNAVAILABLE";
    private String metadata(){return "{\"requestId\":\""+action+"\",\"outcome\":\"COMMITTED\",\"recovery\":\"SAME_REQUEST_ID_EXACT_REPLAY\",\"intentDigest\":\""+"a".repeat(64)+"\",\"causeMessage\":\"private-marker\"}";}
    private WebClientResponseException response(String body){var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);return WebClientResponseException.create(503,"Service Unavailable",headers,body.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);}
    @Test void standardFrameworkEnvelopeKeepsOnlyOriginalSafeRecoveryFields(){
        var error=TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response("{\"status\":503,\"code\":\""+code+"\",\"metadata\":"+metadata()+",\"message\":\"private-marker\"}"),action);
        assertThat(error).isNotNull();assertThat(error.getCode()).isEqualTo(code);assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(error.getMetadata()).containsEntry("requestId",action.toString()).containsEntry("outcome","COMMITTED").containsEntry("intentDigest","a".repeat(64)).doesNotContainKey("causeMessage");
        assertThat(error.getMessage()).doesNotContain("private-marker");assertThat(error.getCause()).isNull();
    }
    @Test void genuineFrameworkProblemEnvelopeRetainsTheSameCommittedOutcome(){
        var error=TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response("{\"status\":503,\"extensions\":{\"code\":\""+code+"\",\"metadata\":"+metadata()+"}}"),action);
        assertThat(error).isNotNull();assertThat(error.getMetadata()).containsEntry("requestId",action.toString()).containsEntry("outcome","COMMITTED");
    }
    @Test void anotherActionOrAmbiguousEnvelopeCannotClaimOriginalActionCompletion(){
        String envelope="{\"status\":503,\"code\":\""+code+"\",\"metadata\":"+metadata()+"}";
        assertThat(TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response(envelope),UUID.randomUUID())).isNull();
        assertThat(TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response(envelope.replace("\"status\":503","\"status\":401")),action)).isNull();
        assertThat(TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response(envelope.replace("\"outcome\":\"COMMITTED\"","\"outcome\":\"UNKNOWN\"")),action)).isNull();
        assertThat(TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response(envelope+"{}"),action)).isNull();
    }
    @Test void duplicateJsonAndOversizedBodyAreRejectedWithoutExposingOwnerData(){
        assertThat(TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response("{\"status\":503,\"status\":503,\"code\":\""+code+"\",\"metadata\":"+metadata()+"}"),action)).isNull();
        assertThat(TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response(" ".repeat(16385)),action)).isNull();
    }
    @Test void lostCurrentAuthorityStripsDigestWithoutConvertingCommittedToRefused(){
        var error=TokenExchangedDomainDraftIntakePortImpl.ownerOutcome(response("{\"status\":503,\"code\":\""+code+"\",\"metadata\":"+metadata()+"}"),action);
        var redacted=TokenExchangedDomainDraftIntakePortImpl.withoutDigest(error);
        assertThat(redacted.getCode()).isEqualTo(code);assertThat(redacted.getMetadata()).doesNotContainKey("intentDigest").containsEntry("outcome","COMMITTED").containsEntry("requestId",action.toString());
    }
}

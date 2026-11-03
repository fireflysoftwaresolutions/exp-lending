/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence.impl;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Real generated SDK/HTTP codec and constructed caller contracts. The responder is not an owning business Main. */
class DomainApplicationEvidenceSdkTest {
    @Test void genuineSdkSendsFullBoundaryBytesAndOriginalActionAndKeepsDistinctOwnerIds()throws Exception{
        try(var fixture=new FixtureImpl("success")){byte[] bytes=new byte[2097152];new Random(17).nextBytes(bytes);var request=fixture.request(bytes);StepVerifier.create(fixture.port.upload(fixture.application,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.original)))
            .assertNext(receipt->{assertThat(receipt.actionId()).isEqualTo(fixture.action);assertThat(receipt.documentId()).isEqualTo(fixture.document).isNotEqualTo(receipt.associationId());assertThat(receipt.associationId()).isEqualTo(fixture.association);assertThat(receipt.storedAt()).isEqualTo(fixture.time);assertThat(receipt.attachedAt()).isEqualTo(fixture.time);assertThat(receipt.contentDigest()).isEqualTo(hash(bytes));}).verifyComplete();
            assertThat(fixture.ownerCalls.get()).isEqualTo(1);assertThat(fixture.lastBody.get().path("actionId").asText()).isEqualTo(fixture.action.toString());assertThat(Base64.getDecoder().decode(fixture.lastBody.get().path("content").asText())).isEqualTo(bytes);assertThat(fixture.lastBody.get().has("partyId")).isFalse();assertThat(fixture.lastBody.get().has("documentId")).isFalse();
        }
    }
    @Test void knownCommittedReceiptCannotBecomePreworkRefusalAfterCallerRevocation()throws Exception{
        try(var fixture=new FixtureImpl("revoke-after-response")){StepVerifier.create(fixture.port.upload(fixture.application,fixture.request(new byte[]{1,2,3})).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.original))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationEvidenceFailure.class);var failure=(ApplicationEvidenceFailure)error;assertThat(failure.outcome()).isEqualTo(ApplicationEvidenceError.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE);assertThat(failure.getMetadata()).containsEntry("actionId",fixture.action.toString()).doesNotContainKeys("storedDocumentId","associationId");}).verify();assertThat(fixture.ownerCalls.get()).isEqualTo(1);}
    }
    @Test void typedDomainPartialCustodyRetainsOriginalActionAndExplicitStoredDocumentId()throws Exception{
        try(var fixture=new FixtureImpl("partial")){StepVerifier.create(fixture.port.upload(fixture.application,fixture.request(new byte[]{1,2,3})).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.original))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationEvidenceFailure.class);var failure=(ApplicationEvidenceFailure)error;assertThat(failure.outcome()).isEqualTo(ApplicationEvidenceError.Outcome.DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE);assertThat(failure.getMetadata()).containsEntry("actionId",fixture.action.toString()).containsEntry("storedDocumentId",fixture.document.toString()).doesNotContainKeys("documentId","associationId");}).verify();assertThat(fixture.ownerCalls.get()).isEqualTo(1);}
    }
    @Test void untyped503AfterDispatchCannotBecomeAProvenRefusalOrAutomaticRetry()throws Exception{
        try(var fixture=new FixtureImpl("untyped")){StepVerifier.create(fixture.port.upload(fixture.application,fixture.request(new byte[]{1,2,3})).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.original))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationEvidenceFailure.class);assertThat(((ApplicationEvidenceFailure)error).outcome()).isEqualTo(ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN);}).verify();assertThat(fixture.ownerCalls.get()).isEqualTo(1);}
    }
    @Test void laterSameActionRequiresCurrentCallerAndCannotReuseEarlierSuccess()throws Exception{
        try(var fixture=new FixtureImpl("success")){var request=fixture.request(new byte[]{1,2,3});StepVerifier.create(fixture.port.upload(fixture.application,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.original))).expectNextCount(1).verifyComplete();fixture.original.setAuthenticated(false);StepVerifier.create(fixture.port.upload(fixture.application,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.original))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(((org.springframework.web.server.ResponseStatusException)error).getStatusCode().value()).isEqualTo(403);}).verify();assertThat(fixture.ownerCalls.get()).isEqualTo(1);}
    }
    @Test void ambiguousOrNonJsonProgressEnvelopesCannotResolveADispatchedWrite(){
        UUID action=UUID.randomUUID();String valid="{\"actionId\":\""+action+"\",\"code\":\"MANAGED_EVIDENCE_DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE\",\"outcome\":\"DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE\",\"recovery\":\"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY\"}";
        for(String body:List.of("{",valid+" {}",valid.replace("\"actionId\":","\"actionId\":\""+action+"\",\"actionId\":"))){assertThat(TokenExchangedDomainApplicationEvidencePortImpl.ownerError(action,error(body,"application/json"))).isNull();}
        assertThat(TokenExchangedDomainApplicationEvidencePortImpl.ownerError(action,error(valid,"text/plain"))).isNull();assertThat(TokenExchangedDomainApplicationEvidencePortImpl.ownerError(action,error(valid,null))).isNull();assertThat(TokenExchangedDomainApplicationEvidencePortImpl.ownerError(action,error(valid,"application/json"))).isNotNull();
    }
    static org.springframework.web.reactive.function.client.WebClientResponseException error(String body,String contentType){var headers=new org.springframework.http.HttpHeaders();if(contentType!=null)headers.set("Content-Type",contentType);return new org.springframework.web.reactive.function.client.WebClientResponseException(503,"Unavailable",headers,body.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);}
    @Test void initialTargetDecoderCannotHangOrDispatchOutsideTheOriginalFiveSecondBudget()throws Exception{
        try(var fixture=new FixtureImpl("decode-never")){StepVerifier.create(fixture.port.upload(fixture.application,fixture.request(new byte[]{1,2,3})).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.original))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationEvidenceFailure.class);assertThat(((ApplicationEvidenceFailure)error).outcome()).isEqualTo(ApplicationEvidenceError.Outcome.OBSERVATION_UNAVAILABLE);}).verify(Duration.ofSeconds(7));assertThat(fixture.ownerCalls.get()).isZero();}
    }
    static final class FixtureImpl implements AutoCloseable {
        final UUID application=UUID.randomUUID(),action=UUID.randomUUID(),party=UUID.randomUUID(),document=UUID.randomUUID(),association=UUID.randomUUID();final Instant time=Instant.parse("2026-11-02T00:00:00.123456Z");
        final AtomicInteger ownerCalls=new AtomicInteger();final AtomicReference<JsonNode> lastBody=new AtomicReference<>();final ObjectMapper mapper=new ObjectMapper();final HttpServer server;final JwtAuthenticationToken original;final TokenExchangedDomainApplicationEvidencePortImpl port;final String access,mode;
        FixtureImpl(String mode)throws Exception{
            this.mode=mode;byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);String clientSecret=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);access=UUID.randomUUID().toString();Instant now=Instant.now();String issuer="https://owned-issuer.invalid";String scope=TokenExchangedDomainApplicationEvidencePortImpl.ACTION;
            var jwt=Jwt.withTokenValue(UUID.randomUUID().toString()).header("alg","RS256").issuer(issuer).subject("opaque:controlled-human").audience(List.of("experience-lending")).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(60)).claim("client_id","lending-browser").claim("principal_kind","HUMAN").claim("tenant_id","owned-platform").claim("party_id",party.toString()).claim("scope",scope).build();
            var factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));original=new JwtAuthenticationToken(jwt,factory.fromClaims(jwt.getSubject(),issuer,jwt.getClaims()).toBuilder().tenantId("owned-platform").build(),List.of());
            var target=Jwt.withTokenValue(access).header("alg","RS256").issuer(issuer).subject(jwt.getSubject()).audience(List.of("domain-lending")).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(30)).claim("client_id","exp-lending").claim("principal_kind","HUMAN").claim("tenant_id","owned-platform").claim("party_id",party.toString()).claim("scope",scope).build();
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/token",exchange->{exchange.getRequestBody().readAllBytes();reply(exchange,200,Map.of("access_token",access,"token_type","Bearer","expires_in",30,"scope",scope));});server.createContext("/api/v1/managed-loan-evidence",this::owner);server.start();URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            var settings=new ApplicationEvidenceSettings(issuer,"experience-lending","lending-browser","owned-platform","domain-lending","exp-lending",uri,true);var registration=ClientRegistration.withRegistrationId("controlledDomainEvidence").clientId("exp-lending").clientSecret(clientSecret).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(uri+"/token").scope(scope).build();
            port=new TokenExchangedDomainApplicationEvidencePortImpl(settings,registration,value->mode.equals("decode-never")?Mono.never():value.equals(access)?Mono.just(target):Mono.error(new IllegalArgumentException("Foreign controlled token")),Clock.systemUTC(),System::nanoTime);
        }
        ApplicationEvidenceUpload request(byte[] bytes){return new ApplicationEvidenceUpload(action,"evidence.pdf","PAYSLIP","application/pdf",bytes);}
        void owner(HttpExchange exchange)throws java.io.IOException{
            ownerCalls.incrementAndGet();assertThat(Objects.equals(exchange.getRequestHeaders().getFirst("Authorization"),"Bearer "+access)).isTrue();assertThat(exchange.getRequestURI().getPath()).isEqualTo("/api/v1/managed-loan-evidence/applications/"+application+"/actions/"+action);
            var body=mapper.readTree(exchange.getRequestBody().readAllBytes());lastBody.set(body);byte[] content=Base64.getDecoder().decode(body.path("content").asText());
            if(mode.equals("untyped")){reply(exchange,503,Map.of("message","Unavailable"));return;}
            if(mode.equals("partial")){reply(exchange,503,Map.of("actionId",action,"code","MANAGED_EVIDENCE_DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE","outcome","DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE","recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY","documentId",document,"contentDigest",hash(content),"documentIntentDigest","a".repeat(64)));return;}
            var receipt=new LinkedHashMap<String,Object>();receipt.put("actionId",action);receipt.put("applicationId",application);receipt.put("partyId",party);receipt.put("documentId",document);receipt.put("associationId",association);receipt.put("fileName","evidence.pdf");receipt.put("documentTypeCode","PAYSLIP");receipt.put("mediaType","application/pdf");receipt.put("contentLength",content.length);receipt.put("contentDigest",hash(content));receipt.put("documentIntentDigest","a".repeat(64));receipt.put("associationIntentDigest","b".repeat(64));receipt.put("storedAt",time.toString());receipt.put("attachedAt",time.toString());receipt.put("associationState","ATTACHED");receipt.put("verificationStatus","PENDING");receipt.put("replayed",false);if(mode.equals("revoke-after-response"))original.setAuthenticated(false);reply(exchange,201,receipt);
        }
        void reply(HttpExchange exchange,int status,Object body)throws java.io.IOException{byte[] wire=mapper.writeValueAsBytes(body);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,wire.length);try(var stream=exchange.getResponseBody()){stream.write(wire);}}
        @Override public void close(){server.stop(0);}
    }
    static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}

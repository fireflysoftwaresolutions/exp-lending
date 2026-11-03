/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract.impl;
import io.fireflysoftware.experience.lending.core.contract.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Actual generated Domain34 HTTP/codec contract with constructed caller. Not an owning business Main. */
class DomainInitialContractSdkTest {
    @Test void genuineSdkPreservesOriginalActionActualReceiptAndInstantThroughPostAndReadonlyGet()throws Exception {
        try(var f=new FixtureImpl("success")){var request=new ApplicationInitialContractRequest(f.action,null);StepVerifier.create(f.port.link(f.application,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(f.original))).assertNext(r->{assertThat(r.actionId()).isEqualTo(f.action);assertThat(r.applicationId()).isEqualTo(f.application);assertThat(r.partyId()).isEqualTo(f.party);assertThat(r.contractId()).isEqualTo(f.contract);assertThat(r.linkedAt()).isEqualTo(f.linked);assertThat(r.contractCreated()).isTrue();}).verifyComplete();
            assertThat(f.lastBody.get().path("actionId").asText()).isEqualTo(f.action.toString());var keys=new HashSet<String>();f.lastBody.get().fieldNames().forEachRemaining(keys::add);assertThat(keys).contains("actionId").isSubsetOf(Set.of("actionId","existingContractId"));assertThat(f.lastBody.get().has("partyId")).isFalse();
            StepVerifier.create(f.port.observe(f.application,f.action).contextWrite(ReactiveSecurityContextHolder.withAuthentication(f.original))).assertNext(found->{assertThat(found).isPresent();assertThat(found.orElseThrow().contractId()).isEqualTo(f.contract);}).verifyComplete();assertThat(f.methods).containsExactly("POST","GET");assertThat(f.calls.get()).isEqualTo(2);assertThat(f.exchangeScopes).containsExactly(TokenExchangedDomainInitialContractPortImpl.LINK,TokenExchangedDomainInitialContractPortImpl.READ);
        }
    }
    @Test void absentReadIsTruthful204WithoutAnyMutationOrFabricatedReceipt()throws Exception {
        try(var f=new FixtureImpl("absent")){StepVerifier.create(f.port.observe(f.application,f.action).contextWrite(ReactiveSecurityContextHolder.withAuthentication(f.original))).assertNext(found->assertThat(found).isEmpty()).verifyComplete();assertThat(f.methods).containsExactly("GET");assertThat(f.calls.get()).isEqualTo(1);}
    }
    @Test void untyped503AfterActualDispatchRetainsUnknownAndDoesNotRetry()throws Exception {
        try(var f=new FixtureImpl("untyped")){failure(f,ApplicationInitialContractError.Outcome.OUTCOME_UNKNOWN);assertThat(f.calls.get()).isEqualTo(1);}
    }
    @Test void provenOwnerRefusalAndKnownCommitStayDifferentWithSameOriginalAction()throws Exception {
        try(var f=new FixtureImpl("REFUSED")){failure(f,ApplicationInitialContractError.Outcome.REFUSED);assertThat(f.calls.get()).isEqualTo(1);}try(var f=new FixtureImpl("COMMITTED_RESPONSE_UNAVAILABLE")){failure(f,ApplicationInitialContractError.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);assertThat(f.calls.get()).isEqualTo(1);}
    }
    @Test void duplicateTrailingForeignOrNonJsonErrorsCannotResolveTheDispatchedAction(){
        UUID action=UUID.randomUUID();String body="{\"actionId\":\""+action+"\",\"code\":\"LENDING_INITIAL_CONTRACT_REFUSED\",\"outcome\":\"REFUSED\",\"recovery\":\"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY\"}";
        assertThat(TokenExchangedDomainInitialContractPortImpl.ownerError(action,error(body,"application/json"))).isEqualTo(ApplicationInitialContractError.Outcome.REFUSED);
        for(String invalid:List.of("{",body+" {}",body.replace("\"actionId\":","\"actionId\":\""+action+"\",\"actionId\":"),body.replace(action.toString(),UUID.randomUUID().toString()),body.replace("LENDING_INITIAL_CONTRACT_REFUSED","LENDING_INITIAL_CONTRACT_OUTCOME_UNKNOWN")))assertThat(TokenExchangedDomainInitialContractPortImpl.ownerError(action,error(invalid,"application/json"))).isNull();
        assertThat(TokenExchangedDomainInitialContractPortImpl.ownerError(action,error(body,"text/plain"))).isNull();assertThat(TokenExchangedDomainInitialContractPortImpl.ownerError(action,error(body,null))).isNull();
    }
    @Test void laterSameActionRevalidatesCallerInsteadOfReusingAnEarlierSuccess()throws Exception {
        try(var f=new FixtureImpl("success")){StepVerifier.create(f.port.link(f.application,new ApplicationInitialContractRequest(f.action,null)).contextWrite(ReactiveSecurityContextHolder.withAuthentication(f.original))).expectNextCount(1).verifyComplete();f.original.setAuthenticated(false);StepVerifier.create(f.port.link(f.application,new ApplicationInitialContractRequest(f.action,null)).contextWrite(ReactiveSecurityContextHolder.withAuthentication(f.original))).expectError(ApplicationInitialContractPreworkRefusal.class).verify();assertThat(f.calls.get()).isEqualTo(1);}
    }
    @Test void decoderNeverEmitsIsBoundedAndNeverDispatchesAnOwner()throws Exception {
        try(var f=new FixtureImpl("decode-never")){failure(f,ApplicationInitialContractError.Outcome.REFUSED);assertThat(f.calls.get()).isZero();}
    }
    @Test void receiptObservedBeforeCallerRevocationRemainsKnownCommitted()throws Exception {
        try(var f=new FixtureImpl("revoke-after-response")){failure(f,ApplicationInitialContractError.Outcome.COMMITTED_RESPONSE_UNAVAILABLE);assertThat(f.calls.get()).isEqualTo(1);}
    }
    @Test void originalBudgetTimeoutAfterDispatchCannotBecomeAProvenRefusal()throws Exception {
        try(var f=new FixtureImpl("owner-never")){failure(f,ApplicationInitialContractError.Outcome.OUTCOME_UNKNOWN);assertThat(f.calls.get()).isEqualTo(1);}
    }
    private static void failure(FixtureImpl f,ApplicationInitialContractError.Outcome expected){StepVerifier.create(f.port.link(f.application,new ApplicationInitialContractRequest(f.action,null)).contextWrite(ReactiveSecurityContextHolder.withAuthentication(f.original))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationInitialContractFailure.class);var value=(ApplicationInitialContractFailure)error;assertThat(value.actionId()).isEqualTo(f.action);assertThat(value.outcome()).isEqualTo(expected);assertThat(value.getMetadata()).containsEntry("actionId",f.action.toString());}).verify(Duration.ofSeconds(7));}
    private static WebClientResponseException error(String body,String type){var headers=new HttpHeaders();if(type!=null)headers.set("Content-Type",type);return new WebClientResponseException(503,"Unavailable",headers,body.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);}
    static final class FixtureImpl implements AutoCloseable {
        final UUID application=UUID.randomUUID(),action=UUID.randomUUID(),party=UUID.randomUUID(),contract=UUID.randomUUID(),tenant=UUID.randomUUID(),role=UUID.randomUUID(),contractParty=UUID.randomUUID(),product=UUID.randomUUID();final Instant linked=Instant.parse("2026-11-02T00:00:00.123456Z");
        final AtomicInteger calls=new AtomicInteger();final AtomicReference<JsonNode> lastBody=new AtomicReference<>();final List<String> methods=Collections.synchronizedList(new ArrayList<>()),exchangeScopes=Collections.synchronizedList(new ArrayList<>());final ObjectMapper json=new ObjectMapper();final HttpServer server;final JwtAuthenticationToken original;final TokenExchangedDomainInitialContractPortImpl port;final String writeAccess=UUID.randomUUID().toString(),readAccess=UUID.randomUUID().toString(),mode;final List<HttpExchange> pending=Collections.synchronizedList(new ArrayList<>());
        FixtureImpl(String mode)throws Exception {
            this.mode=mode;byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);String clientSecret=Base64.getUrlEncoder().withoutPadding().encodeToString(secret),issuer="https://owned-issuer.invalid";Instant now=Instant.now();String link=TokenExchangedDomainInitialContractPortImpl.LINK,read=TokenExchangedDomainInitialContractPortImpl.READ;
            var jwt=Jwt.withTokenValue(UUID.randomUUID().toString()).header("alg","RS256").issuer(issuer).subject("opaque:controlled-human").audience(List.of("experience-lending")).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(60)).claim("client_id","lending-browser").claim("principal_kind","HUMAN").claim("tenant_id","owned-platform").claim("party_id",party.toString()).claim("scope",link+" "+read).build();
            var factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));original=new JwtAuthenticationToken(jwt,factory.fromClaims(jwt.getSubject(),issuer,jwt.getClaims()).toBuilder().tenantId("owned-platform").build(),List.of());
            var targets=new HashMap<String,Jwt>();for(String scope:List.of(link,read)){String access=scope.equals(link)?writeAccess:readAccess;targets.put(access,Jwt.withTokenValue(access).header("alg","RS256").issuer(issuer).subject(jwt.getSubject()).audience(List.of("domain-lending")).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(30)).claim("client_id","exp-lending").claim("principal_kind","HUMAN").claim("tenant_id","owned-platform").claim("party_id",party.toString()).claim("scope",scope).build());}
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/token",exchange->{String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);var form=new HashMap<String,String>();for(String pair:body.split("&")){var parts=pair.split("=",2);form.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}String scope=form.get("scope");assertThat(scope).isIn(link,read);exchangeScopes.add(scope);reply(exchange,200,Map.of("access_token",scope.equals(link)?writeAccess:readAccess,"token_type","Bearer","expires_in",30,"scope",scope));});server.createContext("/api/v1/initial-primary-loan-contracts",this::owner);server.start();URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            var settings=new ApplicationInitialContractSettings(issuer,"experience-lending","lending-browser","owned-platform","domain-lending","exp-lending",uri,true);
            var write=registration(uri,clientSecret,"write",link);var observe=registration(uri,clientSecret,"read",read);port=new TokenExchangedDomainInitialContractPortImpl(settings,write,observe,value->mode.equals("decode-never")?Mono.never():targets.containsKey(value)?Mono.just(targets.get(value)):Mono.error(new IllegalArgumentException("Foreign controlled token")),Clock.systemUTC(),System::nanoTime);
        }
        private static ClientRegistration registration(URI uri,String secret,String id,String scope){return ClientRegistration.withRegistrationId(id).clientId("exp-lending").clientSecret(secret).authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).tokenUri(uri+"/token").scope(scope).build();}
        void owner(HttpExchange exchange)throws java.io.IOException {
            calls.incrementAndGet();String method=exchange.getRequestMethod();methods.add(method);assertThat(exchange.getRequestURI().getPath()).isEqualTo("/api/v1/initial-primary-loan-contracts/applications/"+application+"/actions/"+action);assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer "+(method.equals("POST")?writeAccess:readAccess));byte[] body=exchange.getRequestBody().readAllBytes();if(method.equals("POST"))lastBody.set(json.readTree(body));else assertThat(body).isEmpty();
            if(mode.equals("owner-never")){pending.add(exchange);return;}if(mode.equals("absent")){exchange.sendResponseHeaders(204,-1);exchange.close();return;}if(mode.equals("untyped")){reply(exchange,503,Map.of("message","Unavailable"));return;}if(Set.of("REFUSED","COMMITTED_RESPONSE_UNAVAILABLE").contains(mode)){reply(exchange,503,Map.of("actionId",action,"code","LENDING_INITIAL_CONTRACT_"+mode,"outcome",mode,"recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));return;}
            var receipt=new LinkedHashMap<String,Object>();receipt.put("actionId",action);receipt.put("applicationId",application);receipt.put("resourceTenantId",tenant);receipt.put("partyId",party);receipt.put("roleInContractId",role);receipt.put("contractId",contract);receipt.put("contractPartyId",contractParty);receipt.put("productId",product);receipt.put("currencyCode","EUR");receipt.put("purpose","INITIAL_PRIMARY_LOAN_CONTRACT");receipt.put("contractStatusAtLink","DRAFT");receipt.put("contractCreated",true);receipt.put("linkedAt",linked.toString());receipt.put("intentDigest","a".repeat(64));receipt.put("replayed",false);if(mode.equals("revoke-after-response"))original.setAuthenticated(false);reply(exchange,method.equals("POST")?201:200,receipt);
        }
        void reply(HttpExchange exchange,int status,Object body)throws java.io.IOException {byte[] bytes=json.writeValueAsBytes(body);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);try(var stream=exchange.getResponseBody()){stream.write(bytes);}}
        @Override public void close(){synchronized(pending){pending.forEach(HttpExchange::close);}server.stop(0);}
    }
}

/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake.impl;

import io.fireflysoftware.experience.lending.core.intake.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.webflux.authentication.FireflyAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Constructed-token subject contracts only; native signature acceptance is covered separately by the WebFilter tests. */
class DraftApplicationSubjectVerifierTest {
    static final String SCOPE="lending:applications:draft:create",TENANT="opaque-platform-tenant",SUBJECT="opaque human é/customer@example.invalid";
    final Instant now=Instant.parse("2026-11-01T10:00:00Z");
    final DraftApplicationRequest request=new DraftApplicationRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("1234.50"),24,"EUR","vehicle");
    final ApplicationIntakeExchangeSettings settings=new ApplicationIntakeExchangeSettings("https://issuer.invalid","experience-lending","lending-browser",TENANT,
        "domain-lending","experience-lending",URI.create("http://127.0.0.1:39199"),true);
    final DraftApplicationSubjectVerifierImpl verifier=new DraftApplicationSubjectVerifierImpl(settings,Clock.fixed(now,ZoneOffset.UTC));
    Jwt jwt(boolean target,Map<String,Object> changes){var claims=new HashMap<String,Object>();
        claims.put("iss",settings.issuer());claims.put("sub",SUBJECT);claims.put("aud",List.of(target?settings.domainAudience():settings.incomingAudience()));
        claims.put("client_id",target?settings.domainClientId():settings.incomingClientId());claims.put("principal_kind","HUMAN");claims.put("tenant_id",TENANT);
        claims.put("party_id",request.partyId().toString());claims.put("scope",SCOPE);claims.put("nbf",now.minusSeconds(1));claims.put("exp",now.plusSeconds(target?30:60));claims.putAll(changes);
        return Jwt.withTokenValue("constructed-unit-token-not-signature-proof").header("alg","RS256").claims(values->values.putAll(claims)).build();}
    JwtAuthenticationToken authentication(Map<String,Object> changes){var jwt=jwt(false,changes);
        var factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));
        var principal=factory.fromClaims(jwt.getSubject(),jwt.getIssuer().toString(),jwt.getClaims()).toBuilder().tenantId(TENANT).build();
        var projected=new FireflyAuthenticationToken(principal);return new JwtAuthenticationToken(jwt,principal,projected.getAuthorities());}
    @Test void currentHumanKeepsTheOriginalNativeAuthenticationAndOpaqueSubject(){
        var original=authentication(Map.of());
        StepVerifier.create(verifier.current(request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(original))).assertNext(value->{
            assertThat(value).isSameAs(original);assertThat(value.getToken().getSubject()).isEqualTo(SUBJECT);
            assertThat(value.getToken().getClaimAsString("party_id")).isEqualTo(request.partyId().toString());
        }).verifyComplete();
        StepVerifier.create(verifier.revalidate(original,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(original))).verifyComplete();
    }
    @Test void absentOrUnauthenticatedNativeContextCannotSupplyHumanAuthority(){
        StepVerifier.create(verifier.current(request)).expectError(ResponseStatusException.class).verify();
        var unauthenticated=new JwtAuthenticationToken(jwt(false,Map.of()));
        StepVerifier.create(verifier.current(request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthenticated))).expectError(ResponseStatusException.class).verify();
    }
    @Test void changedHumanPartyTenantAudienceClientScopeOrCurrentWindowIsRefused(){
        for(var changed:List.<Map<String,Object>>of(Map.of("principal_kind","STAFF"),Map.of("party_id",request.productId().toString()),
            Map.of("tenant_id","different-tenant"),Map.of("aud",List.of("different-audience")),Map.of("client_id","different-client"),
            Map.of("scope","lending:applications:read"),Map.of("exp",now),Map.of("nbf",now.plusSeconds(1))))
            StepVerifier.create(verifier.current(request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(changed)))).expectError(ResponseStatusException.class).verify();
    }
    @Test void replacementAuthenticationAndRevokedOriginalCannotRevalidate(){
        var original=authentication(Map.of());
        StepVerifier.create(verifier.revalidate(original,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(Map.of())))).expectError(ResponseStatusException.class).verify();
        original.setAuthenticated(false);
        StepVerifier.create(verifier.revalidate(original,request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(original))).expectError(ResponseStatusException.class).verify();
    }
    @Test void exchangedClaimsKeepTheSameHumanPartyAndNarrowTargetScopeAndExpiry(){
        var original=jwt(false,Map.of());assertThatCode(()->verifier.requireTarget(jwt(true,Map.of()),request,original)).doesNotThrowAnyException();
        for(var changed:List.<Map<String,Object>>of(Map.of("sub","different human"),Map.of("party_id",request.productId().toString()),
            Map.of("principal_kind","STAFF"),Map.of("tenant_id","different-tenant"),Map.of("aud",List.of(settings.incomingAudience())),
            Map.of("client_id",settings.incomingClientId()),Map.of("scope",SCOPE+" lending:applications:read"),Map.of("exp",now.plusSeconds(61)),Map.of("nbf",now.plusSeconds(1))))
            assertThatThrownBy(()->verifier.requireTarget(jwt(true,changed),request,original)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void nativeTokenAndRichPrincipalMustDescribeExactlyTheSameClaims(){
        var original=authentication(Map.of());var changed=jwt(false,Map.of("party_id",request.productId().toString()));
        var mixed=new JwtAuthenticationToken(changed,original.getPrincipal(),original.getAuthorities());
        StepVerifier.create(verifier.current(request).contextWrite(ReactiveSecurityContextHolder.withAuthentication(mixed))).expectError(ResponseStatusException.class).verify();
    }
}

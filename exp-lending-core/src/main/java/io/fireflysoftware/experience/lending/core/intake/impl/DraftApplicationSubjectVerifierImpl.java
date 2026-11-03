/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake.impl;
import io.fireflysoftware.experience.lending.core.intake.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

public final class DraftApplicationSubjectVerifierImpl implements DraftApplicationSubjectVerifier {
    private static final String SCOPE="lending:applications:draft:create";
    private final ApplicationIntakeExchangeSettings settings;
    private final Clock clock;
    public DraftApplicationSubjectVerifierImpl(ApplicationIntakeExchangeSettings settings,Clock clock){this.settings=Objects.requireNonNull(settings);this.clock=Objects.requireNonNull(clock);}
    @Override public Mono<JwtAuthenticationToken> current(DraftApplicationRequest request){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication())
        .filter(auth->auth instanceof JwtAuthenticationToken && auth.isAuthenticated()).cast(JwtAuthenticationToken.class)
        .filter(auth->auth.getPrincipal() instanceof SecurityPrincipal principal && settings.issuer().equals(principal.issuer())
            && settings.platformTenant().equals(principal.tenantId()) && Objects.equals(auth.getToken().getSubject(),principal.subject())
            && auth.getToken().getClaims().equals(principal.claims()) && principal.hasScope(SCOPE))
        .switchIfEmpty(Mono.error(denied())).doOnNext(auth->requireSubject(auth.getToken(),request,false,null));}
    @Override public Mono<Void> revalidate(JwtAuthenticationToken original,DraftApplicationRequest request){return current(request)
        .filter(current->current==original).switchIfEmpty(Mono.error(denied())).then();}
    @Override public void requireTarget(Jwt target,DraftApplicationRequest request,Jwt original){requireSubject(target,request,true,original);}
    private void requireSubject(Jwt jwt,DraftApplicationRequest request,boolean target,Jwt original){
        Object rawScope=jwt.getClaims().get("scope");Set<String> scopes=rawScope instanceof String value?Set.copyOf(Arrays.asList(value.split(" +"))):Set.of();
        Instant now=clock.instant();
        if(jwt.getIssuer()==null || !settings.issuer().equals(jwt.getIssuer().toString()) || jwt.getSubject()==null || jwt.getSubject().isBlank()
            || !"HUMAN".equals(jwt.getClaims().get("principal_kind")) || request==null || request.partyId()==null || !request.partyId().toString().equals(jwt.getClaims().get("party_id"))
            || !settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            || !List.of(target?settings.domainAudience():settings.incomingAudience()).equals(jwt.getAudience())
            || !(target?settings.domainClientId():settings.incomingClientId()).equals(jwt.getClaims().get("client_id"))
            || jwt.getExpiresAt()==null || jwt.getNotBefore()==null || !now.isBefore(jwt.getExpiresAt()) || now.isBefore(jwt.getNotBefore())
            || !scopes.contains(SCOPE) || target && (!scopes.equals(Set.of(SCOPE)) || original==null || !Objects.equals(original.getSubject(),jwt.getSubject())
                || !Objects.equals(original.getClaims().get("party_id"),jwt.getClaims().get("party_id")) || jwt.getExpiresAt().isAfter(original.getExpiresAt())))throw denied();
    }
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current lending party authority required");}
}

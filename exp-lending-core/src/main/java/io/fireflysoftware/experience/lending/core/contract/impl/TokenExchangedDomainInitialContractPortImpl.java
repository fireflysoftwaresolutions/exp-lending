/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract.impl;
import io.fireflysoftware.experience.lending.core.contract.*;
import io.fireflysoftware.experience.lending.core.intake.ApplicationIntakeExchangeSettings;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.InitialLoanContractControllerApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.InitialLoanContractRequest;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.InitialLoanContractReceipt;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.InitialLoanContractError;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.http.*;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Uses the actual Domain34 SDK. No Core call, business store or inferred contractual success. */
public final class TokenExchangedDomainInitialContractPortImpl implements ApplicationInitialContractPort {
    public static final String LINK="lending:applications:initial-primary-contract:link",READ="lending:applications:initial-primary-contract:read";
    private final ApplicationInitialContractSettings settings;private final ClientRegistration writeRegistration,readRegistration;private final ReactiveJwtDecoder decoder;
    private final WebClientReactiveTokenExchangeTokenResponseClient exchange;private final WebClient transport;private final Clock clock;private final LongSupplier nanos;
    public TokenExchangedDomainInitialContractPortImpl(ApplicationInitialContractSettings settings,ClientRegistration writeRegistration,ClientRegistration readRegistration,ReactiveJwtDecoder decoder,Clock clock,LongSupplier nanos){
        this.settings=Objects.requireNonNull(settings);this.writeRegistration=Objects.requireNonNull(writeRegistration);this.readRegistration=Objects.requireNonNull(readRegistration);this.decoder=Objects.requireNonNull(decoder);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        registration(writeRegistration,LINK);registration(readRegistration,READ);
        var connector=new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        transport=ApiClient.buildWebClientBuilder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        var oauth=WebClient.builder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();exchange=new WebClientReactiveTokenExchangeTokenResponseClient();exchange.setWebClient(oauth);
        exchange.setBodyExtractor((message,context)->org.springframework.security.oauth2.core.web.reactive.function.OAuth2BodyExtractors.oauth2AccessTokenResponse().extract(message,new org.springframework.web.reactive.function.BodyExtractor.Context(){
            public List<org.springframework.http.codec.HttpMessageReader<?>> messageReaders(){return context.messageReaders();}
            public Optional<org.springframework.http.server.reactive.ServerHttpResponse> serverResponse(){return context.serverResponse();}
            public Map<String,Object> hints(){var hints=new HashMap<String,Object>(context.hints());hints.put(org.springframework.core.codec.Hints.SUPPRESS_LOGGING_HINT,true);return Map.copyOf(hints);}
        }));exchange.addParametersConverter(grant->{var parameters=new LinkedMultiValueMap<String,String>();parameters.set("audience",settings.domainAudience());return parameters;});
    }
    private void registration(ClientRegistration value,String scope){
        if(!AuthorizationGrantType.TOKEN_EXCHANGE.equals(value.getAuthorizationGrantType())||!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(value.getClientAuthenticationMethod())||!Set.of(scope).equals(value.getScopes())||!settings.domainClientId().equals(value.getClientId())||value.getClientSecret()==null||value.getClientSecret().isBlank()||value.getClientSecret().length()>4096)throw unavailable();
        ApplicationIntakeExchangeSettings.endpoint(java.net.URI.create(value.getProviderDetails().getTokenUri()),settings.ownedLoopbackSandbox());
    }
    @Override public Mono<ApplicationInitialContractReceipt> link(UUID application,ApplicationInitialContractRequest request){
        if(!id(application)||request==null||!id(request.actionId())||request.existingContractId()!=null&&!id(request.existingContractId()))return Mono.error(new ApplicationInitialContractPreworkRefusal(HttpStatus.BAD_REQUEST));
        return invoke(application,request.actionId(),request,false).map(Optional::orElseThrow);
    }
    @Override public Mono<Optional<ApplicationInitialContractReceipt>> observe(UUID application,UUID originalAction){
        if(!id(application)||!id(originalAction))return Mono.error(new ApplicationInitialContractPreworkRefusal(HttpStatus.BAD_REQUEST));return invoke(application,originalAction,null,true);
    }
    private Mono<Optional<ApplicationInitialContractReceipt>> invoke(UUID application,UUID action,ApplicationInitialContractRequest request,boolean read){return Mono.defer(()->{
        var operation=new OperationImpl(application,action,request,read,clock.instant(),nanos.getAsLong());
        return client(operation).flatMap(client->same(operation).then(Mono.defer(()->{
            var api=new InitialLoanContractControllerApi(client);Mono<ResponseEntity<InitialLoanContractReceipt>> call;
            if(read)call=api.observeInitialPrimaryLoanContractWithHttpInfo(application,action,null);
            else {var body=new InitialLoanContractRequest().actionId(action).existingContractId(request.existingContractId());call=api.linkInitialPrimaryLoanContractWithHttpInfo(application,action,body);}
            operation.dispatched.set(true);
            return call.timeout(remaining(operation)).switchIfEmpty(Mono.error(unavailable())).map(response->{
                if(read&&response.getStatusCode().value()==204){if(response.getBody()!=null)throw unavailable();return Optional.<ApplicationInitialContractReceipt>empty();}
                var value=receipt(application,action,party(operation.original.get()),request,response.getBody());operation.known.set(value);
                if(response.getStatusCode().value()!=(read?200:value.replayed()?200:201))throw unavailable();return Optional.of(value);
            }).flatMap(value->same(operation).then(Mono.fromCallable(()->{remaining(operation);return value;})));
        }))).timeout(remaining(operation)).onErrorResume(error->failure(operation,error));
    });}
    private Mono<ApiClient> client(OperationImpl operation){return authentication(operation.scope()).doOnNext(operation.original::set).flatMap(original->{
        requireSubject(original.getToken(),false,null,operation.scope());remaining(operation);
        return exchange.getTokenResponse(new TokenExchangeGrantRequest(operation.read?readRegistration:writeRegistration,original.getToken(),null)).timeout(remaining(operation)).switchIfEmpty(Mono.error(unavailable())).flatMap(response->{
            var token=response.getAccessToken();if(token==null||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())||!Set.of(operation.scope()).equals(token.getScopes()))return Mono.error(unavailable());
            return decoder.decode(token.getTokenValue()).timeout(remaining(operation)).switchIfEmpty(Mono.error(unavailable())).flatMap(target->{if(!token.getTokenValue().equals(target.getTokenValue()))throw denied();requireSubject(target,true,original.getToken(),operation.scope());
                return same(operation).then(Mono.fromCallable(()->{var client=new ApiClient(transport);client.setBasePath(settings.domainEndpoint().toASCIIString());client.setBearerToken(token.getTokenValue());return client;}));
            });
        });
    }).timeout(remaining(operation));}
    private Mono<JwtAuthenticationToken> authentication(String action){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication()).filter(value->value instanceof JwtAuthenticationToken&&value.isAuthenticated()).cast(JwtAuthenticationToken.class)
        .filter(value->value.getPrincipal() instanceof SecurityPrincipal principal&&settings.issuer().equals(principal.issuer())&&settings.platformTenant().equals(principal.tenantId())&&Objects.equals(value.getToken().getSubject(),principal.subject())&&value.getToken().getClaims().equals(principal.claims())&&principal.hasScope(action)).switchIfEmpty(Mono.error(denied())).doOnNext(value->requireSubject(value.getToken(),false,null,action));}
    private Mono<Void> same(OperationImpl operation){return authentication(operation.scope()).filter(current->current==operation.original.get()).switchIfEmpty(Mono.error(denied())).doOnNext(current->remaining(operation)).then();}
    private void requireSubject(Jwt jwt,boolean target,Jwt original,String action){
        Object rawScope=jwt.getClaims().get("scope"),rawParty=jwt.getClaims().get("party_id");Set<String> scopes=rawScope instanceof String text?Set.copyOf(Arrays.asList(text.split(" +"))):Set.of();Instant now=clock.instant();
        if(jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||jwt.getSubject()==null||jwt.getSubject().isBlank()||!"HUMAN".equals(jwt.getClaims().get("principal_kind"))||!(rawParty instanceof String)||!settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            ||!List.of(target?settings.domainAudience():settings.incomingAudience()).equals(jwt.getAudience())||!(target?settings.domainClientId():settings.incomingClientId()).equals(jwt.getClaims().get("client_id"))||jwt.getExpiresAt()==null||jwt.getNotBefore()==null||!now.isBefore(jwt.getExpiresAt())||now.isBefore(jwt.getNotBefore())||!scopes.contains(action)
            ||target&&(!scopes.equals(Set.of(action))||original==null||!Objects.equals(original.getSubject(),jwt.getSubject())||!Objects.equals(original.getClaims().get("party_id"),jwt.getClaims().get("party_id"))||jwt.getExpiresAt().isAfter(original.getExpiresAt())))throw denied();
        try{UUID value=UUID.fromString((String)rawParty);if(!id(value)||!value.toString().equals(rawParty))throw denied();}catch(IllegalArgumentException malformed){throw denied();}
    }
    private Mono<Optional<ApplicationInitialContractReceipt>> failure(OperationImpl operation,Throwable error){
        var complete=operation.known.get();var observed=error instanceof WebClientResponseException http?ownerError(operation.action,http):null;
        if(complete==null&&observed==null){
            if(!operation.dispatched.get()&&error instanceof ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return Mono.error(new ApplicationInitialContractPreworkRefusal(status.getStatusCode()));
            if(error instanceof WebClientResponseException http&&Set.of(400,401,403).contains(http.getStatusCode().value()))return Mono.error(new ApplicationInitialContractPreworkRefusal(http.getStatusCode()));
        }
        return Mono.defer(()->same(operation).timeout(remaining(operation))).thenReturn(true).onErrorReturn(false).flatMap(current->{
            var outcome=operation.read?ApplicationInitialContractError.Outcome.OBSERVATION_UNAVAILABLE:complete!=null?ApplicationInitialContractError.Outcome.COMMITTED_RESPONSE_UNAVAILABLE:observed!=null?observed:operation.dispatched.get()?ApplicationInitialContractError.Outcome.OUTCOME_UNKNOWN:ApplicationInitialContractError.Outcome.REFUSED;
            if(outcome==ApplicationInitialContractError.Outcome.CONFLICT&&!current)outcome=ApplicationInitialContractError.Outcome.OUTCOME_UNKNOWN;
            return Mono.error(new ApplicationInitialContractFailure(operation.action,outcome));
        });
    }
    static ApplicationInitialContractReceipt receipt(UUID application,UUID action,UUID party,ApplicationInitialContractRequest request,InitialLoanContractReceipt value){
        if(value==null||!action.equals(value.getActionId())||!application.equals(value.getApplicationId())||!party.equals(value.getPartyId())||value.getContractCreated()==null||value.getReplayed()==null)throw unavailable();
        if(request!=null&&(request.existingContractId()==null?!value.getContractCreated():value.getContractCreated()||!request.existingContractId().equals(value.getContractId())))throw unavailable();
        try{return new ApplicationInitialContractReceipt(value.getActionId(),value.getApplicationId(),value.getResourceTenantId(),value.getPartyId(),value.getRoleInContractId(),value.getContractId(),value.getContractPartyId(),value.getProductId(),value.getCurrencyCode(),value.getPurpose(),value.getContractStatusAtLink(),value.getContractCreated(),value.getLinkedAt(),value.getIntentDigest(),value.getReplayed());}catch(RuntimeException invalid){throw unavailable();}
    }
    static ApplicationInitialContractError.Outcome ownerError(UUID action,WebClientResponseException error){
        if(!Set.of(409,503).contains(error.getStatusCode().value()))return null;byte[] body=error.getResponseBodyAsByteArray();if(body.length<1||body.length>16384)return null;
        try{var type=error.getHeaders().getContentType();if(type==null||!type.getType().equals("application")||!Set.of("json","problem+json").contains(type.getSubtype()))return null;
            var json=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var value=json.readValue(body,InitialLoanContractError.class);
            if(!action.equals(value.getActionId())||value.getOutcome()==null||!"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY".equals(value.getRecovery()))return null;
            var outcome=ApplicationInitialContractError.Outcome.valueOf(value.getOutcome().getValue());if(!("LENDING_INITIAL_CONTRACT_"+outcome).equals(value.getCode())||(error.getStatusCode().value()==409)!=(outcome==ApplicationInitialContractError.Outcome.CONFLICT))return null;return outcome;
        }catch(Exception malformed){return null;}
    }
    private Duration remaining(OperationImpl operation){long elapsed=nanos.getAsLong()-operation.observedNanos;Instant until=operation.observed.plusSeconds(5);var original=operation.original.get();if(original!=null&&original.getToken().getExpiresAt().isBefore(until))until=original.getToken().getExpiresAt();Duration wall=Duration.between(clock.instant(),until),monotonic=Duration.between(operation.observed,until).minusNanos(elapsed);if(elapsed<0||wall.isNegative()||wall.isZero()||monotonic.isNegative()||monotonic.isZero())throw unavailable();return wall.compareTo(monotonic)<0?wall:monotonic;}
    private static UUID party(JwtAuthenticationToken original){return UUID.fromString((String)original.getToken().getClaims().get("party_id"));}
    private static boolean id(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current original application and signed party authority required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Original Domain initial contract response unavailable");}
    private static final class OperationImpl {final UUID application,action;final ApplicationInitialContractRequest request;final boolean read;final Instant observed;final long observedNanos;final AtomicReference<JwtAuthenticationToken> original=new AtomicReference<>();final AtomicReference<ApplicationInitialContractReceipt> known=new AtomicReference<>();final AtomicBoolean dispatched=new AtomicBoolean();OperationImpl(UUID application,UUID action,ApplicationInitialContractRequest request,boolean read,Instant observed,long observedNanos){this.application=application;this.action=action;this.request=request;this.read=read;this.observed=observed;this.observedNanos=observedNanos;}String scope(){return read?READ:LINK;}}
    @Override public String toString(){return "TokenExchangedDomainInitialContractPortImpl[redacted]";}
}

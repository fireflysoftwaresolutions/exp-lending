/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence.impl;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import io.fireflysoftware.experience.lending.core.intake.ApplicationIntakeExchangeSettings;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.ManagedLoanEvidenceControllerApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
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

/** Uses the actual generated Domain32 contract. Experience never calls a Core owner or persists business data. */
public final class TokenExchangedDomainApplicationEvidencePortImpl implements ApplicationEvidencePort {
    public static final String ACTION="lending:applications:evidence:upload";
    private final ApplicationEvidenceSettings settings;private final ClientRegistration registration;private final ReactiveJwtDecoder decoder;
    private final WebClientReactiveTokenExchangeTokenResponseClient exchange;private final WebClient transport;private final Clock clock;private final LongSupplier nanos;
    public TokenExchangedDomainApplicationEvidencePortImpl(ApplicationEvidenceSettings settings,ClientRegistration registration,ReactiveJwtDecoder decoder,Clock clock,LongSupplier nanos){
        this.settings=Objects.requireNonNull(settings);this.registration=Objects.requireNonNull(registration);this.decoder=Objects.requireNonNull(decoder);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        if(!AuthorizationGrantType.TOKEN_EXCHANGE.equals(registration.getAuthorizationGrantType())||!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(registration.getClientAuthenticationMethod())||!Set.of(ACTION).equals(registration.getScopes())||!settings.domainClientId().equals(registration.getClientId())||registration.getClientSecret()==null||registration.getClientSecret().isBlank()||registration.getClientSecret().length()>4096)throw unavailable();
        ApplicationIntakeExchangeSettings.endpoint(java.net.URI.create(registration.getProviderDetails().getTokenUri()),settings.ownedLoopbackSandbox());
        var connector=new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        transport=ApiClient.buildWebClientBuilder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(3145728);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        var oauth=WebClient.builder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();exchange=new WebClientReactiveTokenExchangeTokenResponseClient();exchange.setWebClient(oauth);
        exchange.setBodyExtractor((message,context)->org.springframework.security.oauth2.core.web.reactive.function.OAuth2BodyExtractors.oauth2AccessTokenResponse().extract(message,new org.springframework.web.reactive.function.BodyExtractor.Context(){
            public List<org.springframework.http.codec.HttpMessageReader<?>> messageReaders(){return context.messageReaders();}
            public Optional<org.springframework.http.server.reactive.ServerHttpResponse> serverResponse(){return context.serverResponse();}
            public Map<String,Object> hints(){var hints=new HashMap<String,Object>(context.hints());hints.put(org.springframework.core.codec.Hints.SUPPRESS_LOGGING_HINT,true);return Map.copyOf(hints);}
        }));exchange.addParametersConverter(grant->{var parameters=new LinkedMultiValueMap<String,String>();parameters.set("audience",settings.domainAudience());return parameters;});
    }
    @Override public Mono<ApplicationEvidenceReceipt> upload(UUID application,ApplicationEvidenceUpload request){
        if(request==null||!id(application)||!id(request.actionId()))return Mono.error(invalid());
        var captured=new ApplicationEvidenceUpload(request.actionId(),request.fileName(),request.documentTypeCode(),request.mediaType(),request.content());
        if(captured.content()==null||captured.content().length<1||captured.content().length>2097152)return Mono.error(invalid());
        return invoke(application,captured.actionId(),captured);
    }
    @Override public Mono<ApplicationEvidenceReceipt> recover(UUID application,UUID action){if(!id(application)||!id(action))return Mono.error(invalid());return invoke(application,action,null);}
    private Mono<ApplicationEvidenceReceipt> invoke(UUID application,UUID action,ApplicationEvidenceUpload request){return Mono.defer(()->{
        var operation=new OperationImpl(application,action,clock.instant(),nanos.getAsLong());
        return client(operation).flatMap(client->same(operation).then(Mono.defer(()->{
            operation.dispatched.set(true);
            var api=new ManagedLoanEvidenceControllerApi(client);
            Mono<ResponseEntity<ManagedEvidenceReceipt>> call=request==null?api.recoverManagedLoanEvidenceWithHttpInfo(application,action):api.storeManagedLoanEvidenceWithHttpInfo(application,action,new ManagedEvidenceUploadRequest().actionId(action).fileName(request.fileName()).documentTypeCode(request.documentTypeCode()).mediaType(request.mediaType()).content(request.content()));
            return call.timeout(remaining(operation)).switchIfEmpty(Mono.error(unavailable())).map(response->{
                var receipt=receipt(application,action,party(operation.original.get()),request,response.getBody());operation.known.set(receipt);
                if(response.getStatusCode().value()!=(receipt.replayed()?200:201))throw unavailable();return receipt;
            }).flatMap(receipt->same(operation).then(Mono.fromCallable(()->{remaining(operation);return receipt;})));
        }))).timeout(remaining(operation)).onErrorResume(error->failure(operation,error));
    });}
    private Mono<ApiClient> client(OperationImpl operation){return authentication().doOnNext(operation.original::set).flatMap(original->{
        requireSubject(original.getToken(),false,null);remaining(operation);
        return exchange.getTokenResponse(new TokenExchangeGrantRequest(registration,original.getToken(),null)).timeout(remaining(operation)).switchIfEmpty(Mono.error(unavailable())).flatMap(response->{
            var token=response.getAccessToken();if(token==null||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())||!Set.of(ACTION).equals(token.getScopes()))return Mono.error(unavailable());
            return decoder.decode(token.getTokenValue()).timeout(remaining(operation)).switchIfEmpty(Mono.error(unavailable())).flatMap(target->{if(!token.getTokenValue().equals(target.getTokenValue()))throw denied();requireSubject(target,true,original.getToken());
                return same(operation).then(Mono.fromCallable(()->{var client=new ApiClient(transport);client.setBasePath(settings.domainEndpoint().toASCIIString());client.setBearerToken(token.getTokenValue());return client;}));
            });
        });
    }).timeout(remaining(operation));}
    private Mono<JwtAuthenticationToken> authentication(){return ReactiveSecurityContextHolder.getContext().map(context->context.getAuthentication()).filter(value->value instanceof JwtAuthenticationToken&&value.isAuthenticated()).cast(JwtAuthenticationToken.class)
        .filter(value->value.getPrincipal() instanceof SecurityPrincipal principal&&settings.issuer().equals(principal.issuer())&&settings.platformTenant().equals(principal.tenantId())&&Objects.equals(value.getToken().getSubject(),principal.subject())&&value.getToken().getClaims().equals(principal.claims())&&principal.hasScope(ACTION)).switchIfEmpty(Mono.error(denied())).doOnNext(value->requireSubject(value.getToken(),false,null));}
    private Mono<Void> same(OperationImpl operation){return authentication().filter(current->current==operation.original.get()).switchIfEmpty(Mono.error(denied())).doOnNext(current->remaining(operation)).then();}
    private void requireSubject(Jwt jwt,boolean target,Jwt original){
        Object rawScope=jwt.getClaims().get("scope"),rawParty=jwt.getClaims().get("party_id");Set<String> scopes=rawScope instanceof String text?Set.copyOf(Arrays.asList(text.split(" +"))):Set.of();Instant now=clock.instant();
        if(jwt.getIssuer()==null||!settings.issuer().equals(jwt.getIssuer().toString())||jwt.getSubject()==null||jwt.getSubject().isBlank()||!"HUMAN".equals(jwt.getClaims().get("principal_kind"))||!(rawParty instanceof String signedParty)||!settings.platformTenant().equals(jwt.getClaims().get("tenant_id"))
            ||!List.of(target?settings.domainAudience():settings.incomingAudience()).equals(jwt.getAudience())||!(target?settings.domainClientId():settings.incomingClientId()).equals(jwt.getClaims().get("client_id"))||jwt.getExpiresAt()==null||jwt.getNotBefore()==null||!now.isBefore(jwt.getExpiresAt())||now.isBefore(jwt.getNotBefore())||!scopes.contains(ACTION)
            ||target&&(!scopes.equals(Set.of(ACTION))||original==null||!Objects.equals(original.getSubject(),jwt.getSubject())||!Objects.equals(original.getClaims().get("party_id"),jwt.getClaims().get("party_id"))||jwt.getExpiresAt().isAfter(original.getExpiresAt())))throw denied();
        try{UUID value=UUID.fromString((String)rawParty);if(!id(value)||!value.toString().equals(rawParty))throw denied();}catch(IllegalArgumentException malformed){throw denied();}
    }
    private Mono<ApplicationEvidenceReceipt> failure(OperationImpl operation,Throwable error){
        var complete=operation.known.get();var observed=error instanceof WebClientResponseException http?ownerError(operation.action,http):null;
        if(complete==null&&observed==null){
            if(!operation.dispatched.get()&&error instanceof ResponseStatusException status&&Set.of(400,401,403).contains(status.getStatusCode().value()))return Mono.error(error);
            if(error instanceof WebClientResponseException http&&Set.of(400,401,403).contains(http.getStatusCode().value()))return Mono.error(new ResponseStatusException(http.getStatusCode(),"Current application evidence authority or input refused"));
        }
        return Mono.defer(()->same(operation).timeout(remaining(operation))).thenReturn(true).onErrorReturn(false).flatMap(current->{
            if(complete==null&&observed!=null&&observed.outcome()==ApplicationEvidenceError.Outcome.CONFLICT&&current)return Mono.error(new ApplicationEvidenceConflict(operation.action));
            var outcome=complete!=null?ApplicationEvidenceError.Outcome.ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE:observed!=null&&observed.outcome()!=ApplicationEvidenceError.Outcome.CONFLICT?observed.outcome():operation.dispatched.get()?ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN:ApplicationEvidenceError.Outcome.OBSERVATION_UNAVAILABLE;
            Map<String,Object> details=Map.of();if(current){if(complete!=null)details=Map.of("storedDocumentId",complete.documentId().toString(),"associationId",complete.associationId().toString(),"contentDigest",complete.contentDigest(),"documentIntentDigest",complete.documentIntentDigest());else if(observed!=null)details=observed.details();}
            return Mono.error(new ApplicationEvidenceFailure(operation.action,outcome,details));
        });
    }
    static ApplicationEvidenceReceipt receipt(UUID application,UUID action,UUID party,ApplicationEvidenceUpload request,ManagedEvidenceReceipt value){
        if(value==null||!action.equals(value.getActionId())||!application.equals(value.getApplicationId())||!party.equals(value.getPartyId())||!id(value.getDocumentId())||!id(value.getAssociationId())||value.getContentLength()==null||value.getContentLength()<1||value.getContentLength()>2097152||!digest(value.getContentDigest())||!digest(value.getDocumentIntentDigest())||!digest(value.getAssociationIntentDigest())||value.getStoredAt()==null||value.getAttachedAt()==null||value.getReplayed()==null||!"ATTACHED".equals(value.getAssociationState())||!"PENDING".equals(value.getVerificationStatus())||!text(value.getFileName(),200)||value.getDocumentTypeCode()==null||!value.getDocumentTypeCode().matches("[A-Z][A-Z0-9_]{0,49}")||value.getMediaType()==null||!value.getMediaType().matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))throw unavailable();
        if(request!=null&&(!request.actionId().equals(action)||!request.fileName().equals(value.getFileName())||!request.documentTypeCode().equals(value.getDocumentTypeCode())||!request.mediaType().equals(value.getMediaType())||request.content().length!=value.getContentLength()||!hash(request.content()).equals(value.getContentDigest())))throw unavailable();
        return new ApplicationEvidenceReceipt(action,application,party,value.getDocumentId(),value.getAssociationId(),value.getFileName(),value.getDocumentTypeCode(),value.getMediaType(),value.getContentLength(),value.getContentDigest(),value.getDocumentIntentDigest(),value.getAssociationIntentDigest(),value.getStoredAt(),value.getAttachedAt(),value.getAssociationState(),value.getVerificationStatus(),value.getReplayed());
    }
    record OwnerErrorImpl(ApplicationEvidenceError.Outcome outcome,Map<String,Object> details){@Override public String toString(){return "OwnerEvidenceError[redacted]";}}
    static OwnerErrorImpl ownerError(UUID action,WebClientResponseException error){
        if(!Set.of(409,503).contains(error.getStatusCode().value()))return null;byte[] bytes=error.getResponseBodyAsByteArray();if(bytes.length<1||bytes.length>16384)return null;
        try{var content=error.getHeaders().getContentType();if(content==null||!content.getType().equals("application")||!(content.getSubtype().equals("json")||content.getSubtype().equals("problem+json")))return null;
            var mapper=new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);var body=mapper.readValue(bytes,ManagedEvidenceError.class);if(!action.equals(body.getActionId())||body.getOutcome()==null||!"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY".equals(body.getRecovery()))return null;String outcome=body.getOutcome().getValue();if(error.getStatusCode().value()==409){if(!"CONFLICT".equals(outcome)||!"MANAGED_EVIDENCE_ACTION_CONFLICT".equals(body.getCode()))return null;}else if("CONFLICT".equals(outcome)||!("MANAGED_EVIDENCE_"+outcome).equals(body.getCode()))return null;
            var details=new LinkedHashMap<String,Object>();for(var entry:Map.of("storedDocumentId",Optional.ofNullable(body.getDocumentId()),"associationId",Optional.ofNullable(body.getAssociationId())).entrySet())if(entry.getValue().isPresent()){UUID id=entry.getValue().orElseThrow();if(!id(id))return null;details.put(entry.getKey(),id.toString());}
            if(body.getContentDigest()!=null){if(!digest(body.getContentDigest()))return null;details.put("contentDigest",body.getContentDigest());}if(body.getDocumentIntentDigest()!=null){if(!digest(body.getDocumentIntentDigest()))return null;details.put("documentIntentDigest",body.getDocumentIntentDigest());}
            return new OwnerErrorImpl(ApplicationEvidenceError.Outcome.valueOf(outcome),Map.copyOf(details));
        }catch(Exception malformed){return null;}
    }
    private Duration remaining(OperationImpl operation){long elapsed=nanos.getAsLong()-operation.observedNanos;Instant until=operation.observed.plusSeconds(5);var original=operation.original.get();if(original!=null&&original.getToken().getExpiresAt().isBefore(until))until=original.getToken().getExpiresAt();Duration wall=Duration.between(clock.instant(),until),monotonic=Duration.between(operation.observed,until).minusNanos(elapsed);if(elapsed<0||wall.isNegative()||wall.isZero()||monotonic.isNegative()||monotonic.isZero())throw unavailable();return wall.compareTo(monotonic)<0?wall:monotonic;}
    private static UUID party(JwtAuthenticationToken original){return UUID.fromString((String)original.getToken().getClaims().get("party_id"));}
    private static boolean id(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static boolean text(String value,int maximum){return value!=null&&!value.isBlank()&&value.length()<=maximum&&value.equals(value.strip())&&!value.codePoints().anyMatch(Character::isISOControl);}
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw unavailable();}}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current original application and signed party authority required");}
    private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bounded original application evidence required");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Original Domain evidence observation unavailable");}
    private static final class OperationImpl {final UUID application,action;final Instant observed;final long observedNanos;final AtomicReference<JwtAuthenticationToken> original=new AtomicReference<>();final AtomicReference<ApplicationEvidenceReceipt> known=new AtomicReference<>();final AtomicBoolean dispatched=new AtomicBoolean();OperationImpl(UUID application,UUID action,Instant observed,long observedNanos){this.application=application;this.action=action;this.observed=observed;this.observedNanos=observedNanos;}}
    @Override public String toString(){return "TokenExchangedDomainApplicationEvidencePortImpl[redacted]";}
}

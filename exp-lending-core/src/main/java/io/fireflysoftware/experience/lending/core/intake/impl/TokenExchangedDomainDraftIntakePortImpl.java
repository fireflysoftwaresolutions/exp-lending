/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake.impl;
import io.fireflysoftware.experience.lending.core.intake.*;
import io.fireflysoftware.experience.lending.core.intake.ApplicationIntakeExchangeSettings;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.DraftLoanIntakesApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.DraftLoanIntakeRequest;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.DraftLoanIntakeReceipt;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Per-operation native token exchange preserves the verified HUMAN subject and signed party projection. */
public final class TokenExchangedDomainDraftIntakePortImpl implements DomainDraftIntakePort {
    private static final String SCOPE="lending:applications:draft:create";
    private final ApplicationIntakeExchangeSettings settings;
    private final ClientRegistration registration;
    private final ReactiveJwtDecoder targetDecoder;
    private final WebClientReactiveTokenExchangeTokenResponseClient exchange;
    private final WebClient transport;
    private final Clock clock;
    private final DraftApplicationSubjectVerifier subjects;
    public TokenExchangedDomainDraftIntakePortImpl(ApplicationIntakeExchangeSettings settings,ClientRegistration registration,ReactiveJwtDecoder targetDecoder,Clock clock){
        this.settings=Objects.requireNonNull(settings);this.registration=Objects.requireNonNull(registration);this.targetDecoder=Objects.requireNonNull(targetDecoder);this.clock=Objects.requireNonNull(clock);
        this.subjects=new DraftApplicationSubjectVerifierImpl(settings,clock);
        if(!AuthorizationGrantType.TOKEN_EXCHANGE.equals(registration.getAuthorizationGrantType())
            || !ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(registration.getClientAuthenticationMethod())
            || !Set.of(SCOPE).equals(registration.getScopes()) || !settings.domainClientId().equals(registration.getClientId())
            || registration.getClientSecret()==null || registration.getClientSecret().isBlank() || registration.getClientSecret().length()>4096)throw unavailable();
        ApplicationIntakeExchangeSettings.endpoint(java.net.URI.create(registration.getProviderDetails().getTokenUri()),settings.ownedLoopbackSandbox());
        var connector=new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build());
        transport=ApiClient.buildWebClientBuilder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        var oauth=WebClient.builder().clientConnector(connector).codecs(c->{c.defaultCodecs().maxInMemorySize(16384);c.defaultCodecs().enableLoggingRequestDetails(false);}).build();
        exchange=new WebClientReactiveTokenExchangeTokenResponseClient();exchange.setWebClient(oauth);
        exchange.setBodyExtractor((message,context)->org.springframework.security.oauth2.core.web.reactive.function.OAuth2BodyExtractors.oauth2AccessTokenResponse().extract(message,
            new org.springframework.web.reactive.function.BodyExtractor.Context(){
                public java.util.List<org.springframework.http.codec.HttpMessageReader<?>> messageReaders(){return context.messageReaders();}
                public java.util.Optional<org.springframework.http.server.reactive.ServerHttpResponse> serverResponse(){return context.serverResponse();}
                public java.util.Map<String,Object> hints(){var hints=new java.util.HashMap<String,Object>(context.hints());hints.put(org.springframework.core.codec.Hints.SUPPRESS_LOGGING_HINT,true);return java.util.Map.copyOf(hints);}
            }));
        exchange.addParametersConverter(grant->{var values=new LinkedMultiValueMap<String,String>();values.set("audience",settings.domainAudience());return values;});
    }
    @Override public Mono<DraftApplicationReceipt> create(DraftApplicationRequest supplied){
        final DraftApplicationRequest request;
        try{request=capture(supplied);}catch(RuntimeException invalid){return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact draft intake request required"));}
        return Mono.defer(()->{Instant started=clock.instant();long before=System.nanoTime();
            return subjects.current(request).flatMap(original->{Jwt subject=original.getToken();
                return exchange.getTokenResponse(new TokenExchangeGrantRequest(registration,subject,null))
                    .timeout(remaining(started,before)).switchIfEmpty(Mono.error(unavailable())).flatMap(response->{
                        var token=response.getAccessToken();
                        if(token==null || !OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType()) || !Set.of(SCOPE).equals(token.getScopes()))return Mono.error(unavailable());
                        return targetDecoder.decode(token.getTokenValue()).timeout(remaining(started,before)).switchIfEmpty(Mono.error(unavailable())).flatMap(target->{
                            if(!token.getTokenValue().equals(target.getTokenValue()))return Mono.error(unavailable());subjects.requireTarget(target,request,subject);
                            return subjects.revalidate(original,request).then(Mono.defer(()->{
                                var client=new ApiClient(transport);client.setBasePath(settings.domainEndpoint().toASCIIString());client.setBearerToken(token.getTokenValue());
                                var body=new DraftLoanIntakeRequest().requestId(request.requestId()).simulationId(request.simulationId()).productId(request.productId())
                                    .partyId(request.partyId()).requestedAmount(request.requestedAmount()).term(request.term()).currency(request.currency()).purpose(request.purpose());
                                return new DraftLoanIntakesApi(client).createDraftLoanIntake(body).timeout(remaining(started,before))
                                    .switchIfEmpty(Mono.error(unavailable())).map(value->receipt(request,value))
                                    .flatMap(value->subjects.revalidate(original,request).then(Mono.fromCallable(()->{remaining(started,before);return value;})));
                            }));
                        });
                    });
            }).timeout(remaining(started,before));
        }).onErrorMap(TokenExchangedDomainDraftIntakePortImpl::safe);
    }
    private static DraftApplicationRequest capture(DraftApplicationRequest r){
        if(r==null)throw new IllegalArgumentException();for(UUID id:new UUID[]{r.requestId(),r.simulationId(),r.productId(),r.partyId()})
            if(id==null || id.equals(new UUID(0,0)))throw new IllegalArgumentException();
        var amount=Objects.requireNonNull(r.requestedAmount()).setScale(2,RoundingMode.UNNECESSARY);
        if(amount.signum()<=0 || amount.precision()>19 || r.term()==null || r.term()<1 || r.currency()==null || !r.currency().matches("[A-Z]{3}"))throw new IllegalArgumentException();
        if(r.purpose()!=null){
            if(r.purpose().indexOf(0)>=0 || r.purpose().codePointCount(0,r.purpose().length())>50)throw new IllegalArgumentException();
            try{java.nio.charset.StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(r.purpose()));}
            catch(java.nio.charset.CharacterCodingException invalid){throw new IllegalArgumentException();}
        }
        return new DraftApplicationRequest(r.requestId(),r.simulationId(),r.productId(),r.partyId(),amount,r.term(),r.currency(),r.purpose());
    }
    static DraftApplicationReceipt receipt(DraftApplicationRequest r,DraftLoanIntakeReceipt v){
        if(v.getApplicationId()==null || v.getApplicationId().equals(new UUID(0,0)) || !r.requestId().equals(v.getRequestId()) || !r.simulationId().equals(v.getSimulationId())
            || !r.productId().equals(v.getProductId()) || !r.partyId().equals(v.getPartyId()) || v.getRequestedAmount()==null || r.requestedAmount().compareTo(v.getRequestedAmount())!=0
            || !r.term().equals(v.getTerm()) || !r.currency().equals(v.getCurrency()) || !Objects.equals(r.purpose(),v.getPurpose())
            || !"DRAFT".equals(v.getInitialStatusCode()) || !"PENDING_DOCUMENTS".equals(v.getInitialSubStatusCode()) || v.getCreatedAt()==null || v.getReplayed()==null
            || v.getIntentDigest()==null || !v.getIntentDigest().matches("[0-9a-f]{64}"))throw unavailable();
        return new DraftApplicationReceipt(v.getRequestId(),v.getApplicationId(),v.getSimulationId(),v.getProductId(),v.getPartyId(),v.getRequestedAmount(),
            v.getTerm(),v.getCurrency(),v.getPurpose(),v.getInitialStatusCode(),v.getInitialSubStatusCode(),v.getCreatedAt().toInstant(ZoneOffset.UTC),v.getReplayed(),v.getIntentDigest());
    }
    private Duration remaining(Instant started,long before){long elapsed=System.nanoTime()-before;var wall=Duration.between(clock.instant(),started.plusSeconds(5));var mono=Duration.ofSeconds(5).minusNanos(elapsed);
        if(elapsed<0 || wall.isZero() || wall.isNegative() || mono.isZero() || mono.isNegative())throw unavailable();return wall.compareTo(mono)<0?wall:mono;}
    private static Throwable safe(Throwable error){
        if(error instanceof ResponseStatusException status)return new ResponseStatusException(status.getStatusCode(),"Lending intake owner is unavailable");
        if(error instanceof WebClientResponseException http){int code=http.getStatusCode().value();return new ResponseStatusException(Set.of(400,401,403,404,409,422,503).contains(code)?HttpStatus.valueOf(code):HttpStatus.SERVICE_UNAVAILABLE,"Lending intake owner is unavailable");}
        return unavailable();
    }
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Authenticated lending intake owner unavailable");}
    @Override public String toString(){return "TokenExchangedDomainDraftIntakePortImpl[redacted]";}
}

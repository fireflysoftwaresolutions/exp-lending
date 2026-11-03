/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.evidence.impl;
import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
public final class ApplicationEvidenceVerifiedSubjectFilterImpl implements WebFilter {
    private final WebFilter delegate;
    public ApplicationEvidenceVerifiedSubjectFilterImpl(WebFilter delegate){this.delegate=Objects.requireNonNull(delegate);}
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){return exchange.getRequest().getMethod()==HttpMethod.POST?delegate.filter(exchange,chain):chain.filter(exchange);}
}

/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.evidence.impl;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.http.server.reactive.*;
import org.springframework.security.web.server.util.matcher.*;
import org.springframework.web.server.*;
import reactor.core.publisher.*;
public final class ApplicationEvidenceEnvelopeWebFilterImpl implements WebFilter {
    private final ServerWebExchangeMatcher route=ServerWebExchangeMatchers.pathMatchers("/api/v1/experience/lending/applications/*/documents","/api/v1/experience/lending/applications/*/documents/**");
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        if(exchange.getRequest().getMethod()!=HttpMethod.POST)return chain.filter(exchange);
        return route.matches(exchange).flatMap(match->{if(!match.isMatch())return chain.filter(exchange);if(exchange.getRequest().getHeaders().getContentLength()>3145728)return Mono.error(tooLarge());
            return DataBufferUtils.join(exchange.getRequest().getBody(),3145728).map(buffer->{try{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);return bytes;}finally{DataBufferUtils.release(buffer);}}).defaultIfEmpty(new byte[0]).onErrorMap(DataBufferLimitException.class,error->tooLarge()).flatMap(bytes->{var request=new ServerHttpRequestDecorator(exchange.getRequest()){@Override public Flux<DataBuffer> getBody(){return bytes.length==0?Flux.empty():Flux.defer(()->Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));}};return chain.filter(exchange.mutate().request(request).build());});
        });
    }
    private static ResponseStatusException tooLarge(){return new ResponseStatusException(HttpStatus.valueOf(413),"Application evidence JSON envelope exceeds 3145728 bytes");}
}

/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/** Checks the current native resource-server token and an independently decoded exchanged token. */
public interface DraftApplicationSubjectVerifier {
    Mono<JwtAuthenticationToken> current(DraftApplicationRequest request);
    Mono<Void> revalidate(JwtAuthenticationToken original,DraftApplicationRequest request);
    void requireTarget(Jwt target,DraftApplicationRequest request,Jwt original);
}

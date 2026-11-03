/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
/** Created only before the Domain command is dispatched. */
public final class ApplicationInitialContractPreworkRefusal extends ResponseStatusException {
    public ApplicationInitialContractPreworkRefusal(HttpStatusCode status){super(status,"Original application contract authority or input refused before dispatch");if(!java.util.Set.of(400,401,403).contains(status.value()))throw new IllegalArgumentException("Exact prework refusal status required");}
}

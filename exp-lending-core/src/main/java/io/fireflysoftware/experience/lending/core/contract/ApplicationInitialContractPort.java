/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import java.util.*;
import reactor.core.publisher.Mono;
public interface ApplicationInitialContractPort {
    Mono<ApplicationInitialContractReceipt> link(UUID applicationId,ApplicationInitialContractRequest request);
    Mono<Optional<ApplicationInitialContractReceipt>> observe(UUID applicationId,UUID originalActionId);
}

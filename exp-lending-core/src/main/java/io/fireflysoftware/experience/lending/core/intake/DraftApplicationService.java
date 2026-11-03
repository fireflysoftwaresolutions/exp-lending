/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake;
import io.fireflysoftware.experience.lending.core.intake.*;
import reactor.core.publisher.Mono;
public interface DraftApplicationService {
    Mono<DraftApplicationReceipt> create(DraftApplicationRequest request);
}

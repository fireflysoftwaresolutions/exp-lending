/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import java.util.UUID;
import reactor.core.publisher.Mono;
public interface ApplicationEvidencePort {
    Mono<ApplicationEvidenceReceipt> upload(UUID applicationId,ApplicationEvidenceUpload request);
    Mono<ApplicationEvidenceReceipt> recover(UUID applicationId,UUID originalActionId);
}

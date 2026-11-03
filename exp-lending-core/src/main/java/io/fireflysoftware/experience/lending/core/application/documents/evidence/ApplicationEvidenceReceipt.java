/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import java.time.Instant;
import java.util.UUID;
public record ApplicationEvidenceReceipt(UUID actionId,UUID applicationId,UUID partyId,UUID documentId,UUID associationId,
        String fileName,String documentTypeCode,String mediaType,long contentLength,String contentDigest,String documentIntentDigest,
        String associationIntentDigest,Instant storedAt,Instant attachedAt,String associationState,String verificationStatus,boolean replayed){
    @Override public String toString(){return "ApplicationEvidenceReceipt[redacted]";}
}

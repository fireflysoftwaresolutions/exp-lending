/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationEvidenceError(UUID actionId,String code,Outcome outcome,String recovery,UUID storedDocumentId,UUID associationId,String contentDigest,String documentIntentDigest){
    public enum Outcome { CONFLICT,OBSERVATION_UNAVAILABLE,OUTCOME_UNKNOWN,DOCUMENT_OUTCOME_UNKNOWN,DOCUMENT_STORED_ASSOCIATION_UNAVAILABLE,DOCUMENT_STORED_ASSOCIATION_UNKNOWN,ASSOCIATION_COMMITTED_RESPONSE_UNAVAILABLE }
    @Override public String toString(){return "ApplicationEvidenceError[redacted]";}
}

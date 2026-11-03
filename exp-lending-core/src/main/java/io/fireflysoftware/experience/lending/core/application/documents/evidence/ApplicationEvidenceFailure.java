/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class ApplicationEvidenceFailure extends BusinessException {
    private final UUID actionId;private final ApplicationEvidenceError.Outcome outcome;
    public ApplicationEvidenceFailure(UUID actionId,ApplicationEvidenceError.Outcome outcome,Map<String,Object> details){super(HttpStatus.SERVICE_UNAVAILABLE,"APPLICATION_EVIDENCE_"+Objects.requireNonNull(outcome).name(),"Original application evidence response unavailable",metadata(actionId,outcome,details));this.actionId=Objects.requireNonNull(actionId);this.outcome=outcome;}
    public UUID actionId(){return actionId;}public ApplicationEvidenceError.Outcome outcome(){return outcome;}
    private static Map<String,Object> metadata(UUID action,ApplicationEvidenceError.Outcome outcome,Map<String,Object> details){var result=new LinkedHashMap<String,Object>();result.put("actionId",Objects.requireNonNull(action).toString());result.put("outcome",outcome.name());result.put("recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY");for(String key:List.of("storedDocumentId","associationId","contentDigest","documentIntentDigest"))if(details.containsKey(key))result.put(key,details.get(key));return result;}
}

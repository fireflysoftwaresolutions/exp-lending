/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class ApplicationEvidenceConflict extends BusinessException {
    private final UUID actionId;
    public ApplicationEvidenceConflict(UUID actionId){super(HttpStatus.CONFLICT,"APPLICATION_EVIDENCE_CONFLICT","The original action is bound to different evidence");this.actionId=Objects.requireNonNull(actionId);}
    public UUID actionId(){return actionId;}
}

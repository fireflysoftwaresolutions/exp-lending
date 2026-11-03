/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
public final class ApplicationInitialContractFailure extends BusinessException {
    private final UUID action;private final ApplicationInitialContractError.Outcome outcome;
    public ApplicationInitialContractFailure(UUID action,ApplicationInitialContractError.Outcome outcome){super(outcome==ApplicationInitialContractError.Outcome.CONFLICT?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE,"APPLICATION_INITIAL_CONTRACT_"+outcome,"Original initial contract response unavailable",Map.of("actionId",ApplicationInitialContractRequest.requireId(action).toString(),"outcome",outcome.name(),"recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));this.action=action;this.outcome=Objects.requireNonNull(outcome);}
    public UUID actionId(){return action;}public ApplicationInitialContractError.Outcome outcome(){return outcome;}
}

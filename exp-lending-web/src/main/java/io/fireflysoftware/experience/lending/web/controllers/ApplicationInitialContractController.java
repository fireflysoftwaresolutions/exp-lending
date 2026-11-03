/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.controllers;
import io.fireflysoftware.experience.lending.core.contract.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
@RestController
@RequestMapping("/api/v1/experience/lending/applications/{applicationId}/initial-primary-contracts/actions/{actionId}")
@SecurityRequirement(name="lendingExperienceIntakeBearer")
public class ApplicationInitialContractController {
    private final ObjectProvider<ApplicationInitialContractPort> owners;
    public ApplicationInitialContractController(ObjectProvider<ApplicationInitialContractPort> owners){this.owners=owners;}
    @DisableIdempotency
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="linkApplicationInitialPrimaryContract",summary="Link the actual managed application through Domain to its retained initial Core Contract using the original action")
    @ApiResponses({@ApiResponse(responseCode="201",description="Initial Core Contract link created; no signing, activation or Loan transition implied",content=@Content(schema=@Schema(implementation=ApplicationInitialContractReceipt.class))),@ApiResponse(responseCode="200",description="Exact original initial Core Contract link replayed",content=@Content(schema=@Schema(implementation=ApplicationInitialContractReceipt.class))),
        @ApiResponse(responseCode="400",description="Original path/action or request invalid",content=@Content),@ApiResponse(responseCode="401",description="Bearer authentication refused",content=@Content),@ApiResponse(responseCode="403",description="Current original party/actor authority refused before owner dispatch",content=@Content),
        @ApiResponse(responseCode="409",description="Original action or initial purpose conflicts",content=@Content(schema=@Schema(implementation=ApplicationInitialContractError.class))),@ApiResponse(responseCode="503",description="REFUSED before dispatch/proven rollback, OUTCOME_UNKNOWN, or COMMITTED_RESPONSE_UNAVAILABLE; retain same-action recovery",content=@Content(schema=@Schema(implementation=ApplicationInitialContractError.class)))})
    public Mono<ResponseEntity<ApplicationInitialContractReceipt>> link(@PathVariable UUID applicationId,@PathVariable UUID actionId,@Valid @RequestBody ApplicationInitialContractRequest request){return Mono.defer(()->{
        var invoked=new AtomicBoolean();return Mono.defer(()->{
            if(request==null||!actionId.equals(request.actionId()))return Mono.error(new ApplicationInitialContractPreworkRefusal(HttpStatus.BAD_REQUEST));
            var owner=owners.getIfUnique();if(owner==null)return Mono.error(new ApplicationInitialContractFailure(actionId,ApplicationInitialContractError.Outcome.REFUSED));
            invoked.set(true);return owner.link(applicationId,request).switchIfEmpty(Mono.error(new ApplicationInitialContractFailure(actionId,ApplicationInitialContractError.Outcome.OUTCOME_UNKNOWN))).map(value->ResponseEntity.status(value.replayed()?200:201).cacheControl(CacheControl.noStore()).body(value));
        }).onErrorMap(error->{if(error instanceof ApplicationInitialContractFailure||error instanceof ApplicationInitialContractPreworkRefusal)return error;return new ApplicationInitialContractFailure(actionId,invoked.get()?ApplicationInitialContractError.Outcome.OUTCOME_UNKNOWN:ApplicationInitialContractError.Outcome.REFUSED);});
    });}
    @GetMapping(produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="observeApplicationInitialPrimaryContract",summary="Read the original retained Core Contract link through Domain under current original actor/party authority; never writes")
    @ApiResponses({@ApiResponse(responseCode="200",description="Original immutable Core Contract link observed",content=@Content(schema=@Schema(implementation=ApplicationInitialContractReceipt.class))),@ApiResponse(responseCode="204",description="No retained action observed; not proof that an in-flight action cannot commit",content=@Content),@ApiResponse(responseCode="401",description="Bearer authentication refused",content=@Content),@ApiResponse(responseCode="403",description="Current original actor/party authority refused",content=@Content),@ApiResponse(responseCode="503",description="OBSERVATION_UNAVAILABLE; no write issued",content=@Content(schema=@Schema(implementation=ApplicationInitialContractError.class)))})
    public Mono<ResponseEntity<ApplicationInitialContractReceipt>> observe(@PathVariable UUID applicationId,@PathVariable UUID actionId){return Mono.defer(()->{
        var owner=owners.getIfUnique();if(owner==null)return Mono.error(new ApplicationInitialContractFailure(actionId,ApplicationInitialContractError.Outcome.OBSERVATION_UNAVAILABLE));
        return owner.observe(applicationId,actionId).switchIfEmpty(Mono.error(new ApplicationInitialContractFailure(actionId,ApplicationInitialContractError.Outcome.OBSERVATION_UNAVAILABLE))).map(value->value.map(receipt->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt)).orElseGet(()->ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()));
    }).onErrorMap(error->{if(error instanceof ApplicationInitialContractFailure||error instanceof ApplicationInitialContractPreworkRefusal)return error;return new ApplicationInitialContractFailure(actionId,ApplicationInitialContractError.Outcome.OBSERVATION_UNAVAILABLE);});}
    @ExceptionHandler(ApplicationInitialContractFailure.class) public ResponseEntity<ApplicationInitialContractError> unavailable(ApplicationInitialContractFailure error){return ResponseEntity.status(error.outcome()==ApplicationInitialContractError.Outcome.CONFLICT?409:503).cacheControl(CacheControl.noStore()).body(new ApplicationInitialContractError(error.actionId(),error.getCode(),error.outcome(),"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
}

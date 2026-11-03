/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.controllers;
import io.fireflysoftware.experience.lending.core.intake.DraftApplicationService;
import io.fireflysoftware.experience.lending.core.intake.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/experience/lending/application-intakes")
@Tag(name="DraftLoanIntakes")
@SecurityScheme(name="lendingExperienceIntakeBearer",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
@SecurityRequirement(name="lendingExperienceIntakeBearer")
public final class DraftApplicationController {
    private final DraftApplicationService service;
    public DraftApplicationController(DraftApplicationService service){this.service=service;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @DisableIdempotency
    @Operation(operationId="createDraftApplicationIntake",summary="Create or replay an authorized DRAFT loan intake",
        description="Preserves exact retained simulation terms. The receipt contains the initial DRAFT/PENDING_DOCUMENTS status, not an offer, contract, approval, or disbursement.")
    @ApiResponses({@ApiResponse(responseCode="201",description="Draft intake created",
        content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=DraftApplicationReceipt.class))),
        @ApiResponse(responseCode="200",description="Exact draft intake replay",
        content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=DraftApplicationReceipt.class)))})
    public Mono<ResponseEntity<DraftApplicationReceipt>> create(@Valid @RequestBody DraftApplicationRequest request){
        return service.create(request).map(value->ResponseEntity.status(value.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(value));
    }
}

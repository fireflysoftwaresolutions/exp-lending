/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.experience.lending.web.controllers;

import io.fireflysoftware.experience.lending.core.application.documents.commands.UploadDocumentCommand;
import io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO;
import io.fireflysoftware.experience.lending.core.application.documents.services.ApplicationDocumentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import jakarta.validation.Valid;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import org.springframework.http.CacheControl;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller exposing document management endpoints for a loan application
 * (list, upload, delete).
 */
@RestController
@RequestMapping("/api/v1/experience/lending/applications/{id}/documents")
@RequiredArgsConstructor
@Tag(name = "Lending - Application Documents")
public class ApplicationDocumentsController {

    private final ApplicationDocumentsService applicationDocumentsService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List Documents",
            description = "Returns all documents attached to a loan application.")
    public Flux<ApplicationDocumentDTO> listDocuments(@PathVariable UUID id) {
        return applicationDocumentsService.listDocuments(id);
    }

    @DisableIdempotency
    @SecurityRequirement(name="lendingExperienceIntakeBearer")
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="uploadDocument",summary="Upload actual application evidence and attach its retained owner reference",
        description="Requires the caller's original action and actual base64 content. Party comes only from the signed current HUMAN identity. documentId remains the legacy association identifier; storedDocumentId is the distinct Document owner identity. Evidence verification remains PENDING.")
    @ApiResponses({@ApiResponse(responseCode="201",description="Actual custody and original association observed",content=@Content(schema=@Schema(implementation=ApplicationDocumentDTO.class))),@ApiResponse(responseCode="200",description="Same original action/content replayed",content=@Content(schema=@Schema(implementation=ApplicationDocumentDTO.class))),
        @ApiResponse(responseCode="400",description="Missing action/content or invalid route/filename/type",content={}),@ApiResponse(responseCode="413",description="JSON envelope exceeds3145728bytes",content={}),@ApiResponse(responseCode="401",description="Bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current original signed-party authority refused",content={}),
        @ApiResponse(responseCode="409",description="Original action has different evidence",content=@Content(schema=@Schema(implementation=ApplicationEvidenceError.class))),@ApiResponse(responseCode="503",description="Known owner progress retained or completion uncertain; recover only the same original action",content=@Content(schema=@Schema(implementation=ApplicationEvidenceError.class)))})
    public Mono<ResponseEntity<ApplicationDocumentDTO>> uploadDocument(@PathVariable UUID id,@Valid @RequestBody UploadDocumentCommand command){return applicationDocumentsService.uploadDocument(id,command).map(result->response(id,command.getActionId(),result));}

    @DisableIdempotency
    @SecurityRequirement(name="lendingExperienceIntakeBearer")
    @PostMapping(path="/actions/{actionId}/recovery",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="recoverApplicationDocumentEvidence",summary="Recover original evidence custody and its application association",
        description="May complete a missing association under current authority. Never uploads replacement bytes, generates another action or deletes retained Document custody.")
    @ApiResponses({@ApiResponse(responseCode="201",description="Original missing association completed",content=@Content(schema=@Schema(implementation=ApplicationDocumentDTO.class))),@ApiResponse(responseCode="200",description="Existing original association/content observed",content=@Content(schema=@Schema(implementation=ApplicationDocumentDTO.class))),
        @ApiResponse(responseCode="400",description="Invalid original application/action",content={}),@ApiResponse(responseCode="401",description="Bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current original signed-party authority refused",content={}),@ApiResponse(responseCode="409",description="Original action conflict",content=@Content(schema=@Schema(implementation=ApplicationEvidenceError.class))),@ApiResponse(responseCode="503",description="Known partial progress or uncertain completion preserved",content=@Content(schema=@Schema(implementation=ApplicationEvidenceError.class)))})
    public Mono<ResponseEntity<ApplicationDocumentDTO>> recoverDocument(@PathVariable UUID id,@PathVariable UUID actionId){return applicationDocumentsService.recoverDocument(id,actionId).map(result->response(id,actionId,result));}
    private static ResponseEntity<ApplicationDocumentDTO> response(UUID application,UUID action,ApplicationDocumentDTO result){if(result==null||!application.equals(result.getApplicationId())||!action.equals(result.getActionId())||result.getReplayed()==null||result.getAssociationId()==null||!result.getAssociationId().equals(result.getDocumentId())||result.getStoredDocumentId()==null)throw new ApplicationEvidenceFailure(action,ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN,Map.of());return ResponseEntity.status(result.getReplayed()?HttpStatus.OK:HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(result);}
    @ExceptionHandler(ApplicationEvidenceFailure.class) public ResponseEntity<ApplicationEvidenceError> unavailable(ApplicationEvidenceFailure error){var details=error.getMetadata();return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new ApplicationEvidenceError(error.actionId(),error.getCode(),error.outcome(),"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY",id(details,"storedDocumentId"),id(details,"associationId"),value(details,"contentDigest"),value(details,"documentIntentDigest")));}
    @ExceptionHandler(ApplicationEvidenceConflict.class) public ResponseEntity<ApplicationEvidenceError> conflict(ApplicationEvidenceConflict error){return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(new ApplicationEvidenceError(error.actionId(),error.getCode(),ApplicationEvidenceError.Outcome.CONFLICT,"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY",null,null,null,null));}
    private static UUID id(Map<String,Object> details,String key){return details.get(key) instanceof String value?UUID.fromString(value):null;}
    private static String value(Map<String,Object> details,String key){return details.get(key) instanceof String value?value:null;}

    @DeleteMapping("/{docId}")
    @Operation(summary = "Delete Document",
            description = "Removes a document from a loan application.")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @PathVariable UUID id,
            @PathVariable UUID docId) {
        return applicationDocumentsService.deleteDocument(id, docId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}

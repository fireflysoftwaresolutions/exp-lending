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


package io.fireflysoftware.experience.lending.core.application.documents.services.impl;

import io.fireflysoftware.domain.lending.loan.origination.sdk.api.LoanOriginationApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.ApplicationDocumentDTO;
import io.fireflysoftware.experience.lending.core.application.documents.commands.UploadDocumentCommand;
import io.fireflysoftware.experience.lending.core.application.documents.services.ApplicationDocumentsService;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.*;
import java.nio.*;
import java.nio.charset.*;

/**
 * Default implementation of {@link ApplicationDocumentsService}, delegating to the
 * domain Loan Origination SDK's {@code LoanOriginationApi} for document operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationDocumentsServiceImpl implements ApplicationDocumentsService {

    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private final LoanOriginationApi loanOriginationApi;
    private final ObjectProvider<ApplicationEvidencePort> evidencePorts;

    @Override
    public Flux<io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO> listDocuments(UUID applicationId) {
        log.debug("Listing documents for applicationId={}", applicationId);
        return loanOriginationApi
                .getApplicationDocuments(applicationId, null)
                .flatMapIterable(page -> page.getContent() != null ? page.getContent() : List.of())
                .map(this::mapToDTO);
    }

    @Override
    public Mono<io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO> uploadDocument(UUID applicationId,UploadDocumentCommand command){
        final ApplicationEvidenceUpload request;
        try{request=capture(applicationId,command);}catch(RuntimeException invalid){return Mono.error(invalid());}
        return Mono.defer(()->{var port=port(request.actionId());return port.upload(applicationId,request).switchIfEmpty(Mono.error(new ApplicationEvidenceFailure(request.actionId(),ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN,java.util.Map.of())));})
            .map(receipt->evidenceView(applicationId,request.actionId(),request,receipt));
    }
    @Override
    public Mono<io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO> recoverDocument(UUID applicationId,UUID originalActionId){
        if(!id(applicationId)||!id(originalActionId))return Mono.error(invalid());
        return Mono.defer(()->port(originalActionId).recover(applicationId,originalActionId).switchIfEmpty(Mono.error(new ApplicationEvidenceFailure(originalActionId,ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN,java.util.Map.of()))))
            .map(receipt->evidenceView(applicationId,originalActionId,null,receipt));
    }
    private ApplicationEvidencePort port(UUID action){
        try{var port=evidencePorts.getIfUnique();if(port!=null)return port;}catch(RuntimeException unavailable){throw new ApplicationEvidenceFailure(action,ApplicationEvidenceError.Outcome.OBSERVATION_UNAVAILABLE,java.util.Map.of());}
        throw new ApplicationEvidenceFailure(action,ApplicationEvidenceError.Outcome.OBSERVATION_UNAVAILABLE,java.util.Map.of());
    }
    private static ApplicationEvidenceUpload capture(UUID application,UploadDocumentCommand command){
        if(!id(application)||command==null||!id(command.getActionId())||command.getApplicationId()!=null&&!application.equals(command.getApplicationId()))throw invalid();
        byte[] bytes=command.getContent();String name=command.getFileName(),type=command.getDocumentType();
        if(bytes==null||bytes.length<1||bytes.length>2097152||name==null||name.isBlank()||name.length()>200||!name.equals(name.strip())||name.contains("/")||name.contains("\\")||java.util.Set.of(".","..").contains(name)||name.codePoints().anyMatch(Character::isISOControl)||type==null||!type.matches("[A-Z][A-Z0-9_]{0,49}"))throw invalid();
        try{StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(name));}catch(CharacterCodingException malformed){throw invalid();}
        return new ApplicationEvidenceUpload(command.getActionId(),name,type,inferMimeType(name),bytes);
    }
    private static io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO evidenceView(UUID application,UUID action,ApplicationEvidenceUpload request,ApplicationEvidenceReceipt receipt){
        if(receipt==null||!application.equals(receipt.applicationId())||!action.equals(receipt.actionId())||!id(receipt.partyId())||!id(receipt.documentId())||!id(receipt.associationId())||receipt.storedAt()==null||receipt.attachedAt()==null||receipt.contentLength()<1||receipt.contentLength()>2097152||!digest(receipt.contentDigest())||!digest(receipt.documentIntentDigest())||!digest(receipt.associationIntentDigest())||!"ATTACHED".equals(receipt.associationState())||!"PENDING".equals(receipt.verificationStatus())||receipt.fileName()==null||receipt.fileName().isBlank()||receipt.fileName().length()>200||!receipt.fileName().equals(receipt.fileName().strip())||receipt.fileName().contains("/")||receipt.fileName().contains("\\")||receipt.fileName().codePoints().anyMatch(Character::isISOControl)||receipt.documentTypeCode()==null||!receipt.documentTypeCode().matches("[A-Z][A-Z0-9_]{0,49}")||receipt.mediaType()==null||receipt.mediaType().length()>100||!receipt.mediaType().matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))throw protocol(action);
        if(request!=null&&(!request.fileName().equals(receipt.fileName())||!request.documentTypeCode().equals(receipt.documentTypeCode())||!request.mediaType().equals(receipt.mediaType())||request.content().length!=receipt.contentLength()||!hash(request.content()).equals(receipt.contentDigest())))throw protocol(action);
        return io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO.builder()
            .documentId(receipt.associationId()).associationId(receipt.associationId()).storedDocumentId(receipt.documentId()).applicationId(application).actionId(action).partyId(receipt.partyId())
            .fileName(receipt.fileName()).documentType(receipt.mediaType()).documentTypeCode(receipt.documentTypeCode()).mediaType(receipt.mediaType()).size(receipt.contentLength())
            .uploadedAt(LocalDateTime.ofInstant(receipt.attachedAt(),ZoneOffset.UTC)).storedAt(receipt.storedAt()).attachedAt(receipt.attachedAt()).contentDigest(receipt.contentDigest()).documentIntentDigest(receipt.documentIntentDigest()).associationIntentDigest(receipt.associationIntentDigest())
            .associationState(receipt.associationState()).verificationStatus(receipt.verificationStatus()).replayed(receipt.replayed()).build();
    }
    private static boolean id(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static boolean digest(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
    private static String hash(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}}
    private static ApplicationEvidenceFailure protocol(UUID action){return new ApplicationEvidenceFailure(action,ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN,java.util.Map.of());}
    private static BusinessException invalid(){return new BusinessException(HttpStatus.BAD_REQUEST,"APPLICATION_EVIDENCE_INPUT_INVALID","Original action and bounded actual content required");}

    /**
     * Best-effort mime-type inference from a filename's extension. Falls back to
     * application/octet-stream when the extension is missing or unknown — the
     * core service stores whatever we send and does not derive its own value, so
     * sending an opaque-but-honest fallback is safer than guessing.
     */
    public static String inferMimeType(String fileName) {
        if (fileName == null) {
            return DEFAULT_MIME_TYPE;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return DEFAULT_MIME_TYPE;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf"  -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"  -> "image/png";
            case "gif"  -> "image/gif";
            case "tif", "tiff" -> "image/tiff";
            case "webp" -> "image/webp";
            case "txt"  -> "text/plain";
            case "csv"  -> "text/csv";
            case "html", "htm" -> "text/html";
            case "json" -> "application/json";
            case "xml"  -> "application/xml";
            case "doc"  -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls"  -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default     -> DEFAULT_MIME_TYPE;
        };
    }

    @Override
    public Mono<Void> deleteDocument(UUID applicationId, UUID documentId) {
        log.debug("Deleting document documentId={} for applicationId={}", documentId, applicationId);
        return loanOriginationApi.deleteApplicationDocument(applicationId, documentId, UUID.randomUUID().toString());
    }

    private io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO mapToDTO(ApplicationDocumentDTO src) {
        return io.fireflysoftware.experience.lending.core.application.documents.queries.ApplicationDocumentDTO.builder()
                .documentId(src.getApplicationDocumentId())
                .associationId(src.getApplicationDocumentId())
                .applicationId(src.getLoanApplicationId())
                .fileName(src.getDocumentName())
                .documentType(src.getMimeType())
                .uploadedAt(src.getReceivedAt() != null ? src.getReceivedAt() : src.getCreatedAt())
                .size(src.getFileSizeBytes())
                .build();
    }
}

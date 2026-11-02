/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.services;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.LoanOriginationApi;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.ApplicationDocumentDTO;
import io.fireflysoftware.domain.lending.loan.origination.sdk.model.PaginationResponseApplicationDocumentDTO;
import io.fireflysoftware.experience.lending.core.application.documents.commands.UploadDocumentCommand;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import io.fireflysoftware.experience.lending.core.application.documents.services.impl.ApplicationDocumentsServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Maintained service-boundary cases. Mocked ports do not prove custody or owner HTTP/native acceptance. */
@ExtendWith(MockitoExtension.class)
class ApplicationDocumentsServiceImplTest {
    @Mock LoanOriginationApi loanOriginationApi;
    @Mock ObjectProvider<ApplicationEvidencePort> evidencePorts;
    @Mock ApplicationEvidencePort evidence;
    @InjectMocks ApplicationDocumentsServiceImpl service;
    static final UUID APPLICATION_ID=UUID.randomUUID(),DOCUMENT_ID=UUID.randomUUID(),STORED_DOCUMENT_ID=UUID.randomUUID(),ACTION=UUID.randomUUID(),PARTY=UUID.randomUUID();
    @Test void listDocuments_returnsDocumentsMappedFromPage(){
        var sdk=new ApplicationDocumentDTO().applicationDocumentId(DOCUMENT_ID).loanApplicationId(APPLICATION_ID).documentName("passport.pdf").mimeType("application/pdf").receivedAt(LocalDateTime.now()).fileSizeBytes(1024L);
        when(loanOriginationApi.getApplicationDocuments(eq(APPLICATION_ID),isNull())).thenReturn(Mono.just(new PaginationResponseApplicationDocumentDTO().content(List.of(sdk))));
        StepVerifier.create(service.listDocuments(APPLICATION_ID)).assertNext(dto->{assertThat(dto.getDocumentId()).isEqualTo(DOCUMENT_ID);assertThat(dto.getAssociationId()).isEqualTo(DOCUMENT_ID);assertThat(dto.getStoredDocumentId()).isNull();assertThat(dto.getApplicationId()).isEqualTo(APPLICATION_ID);assertThat(dto.getFileName()).isEqualTo("passport.pdf");assertThat(dto.getDocumentType()).isEqualTo("application/pdf");assertThat(dto.getSize()).isEqualTo(1024L);}).verifyComplete();
    }
    @Test void listDocuments_returnsEmptyWhenPageContentIsNull(){when(loanOriginationApi.getApplicationDocuments(eq(APPLICATION_ID),isNull())).thenReturn(Mono.just(new PaginationResponseApplicationDocumentDTO().content(null)));StepVerifier.create(service.listDocuments(APPLICATION_ID)).verifyComplete();}
    @Test void listDocuments_propagatesUpstreamError(){when(loanOriginationApi.getApplicationDocuments(eq(APPLICATION_ID),isNull())).thenReturn(Mono.error(new RuntimeException("upstream error")));StepVerifier.create(service.listDocuments(APPLICATION_ID)).expectError(RuntimeException.class).verify();}
    @Test void uploadDocument_returnsMappedDTO(){
        var command=command("id_card.jpg","ID_DOCUMENT",new byte[]{1,2,3});when(evidencePorts.getIfUnique()).thenReturn(evidence);when(evidence.upload(eq(APPLICATION_ID),any())).thenReturn(Mono.just(receipt("id_card.jpg","ID_DOCUMENT","image/jpeg",3)));
        StepVerifier.create(service.uploadDocument(APPLICATION_ID,command)).assertNext(dto->{assertThat(dto.getDocumentId()).isEqualTo(DOCUMENT_ID);assertThat(dto.getAssociationId()).isEqualTo(DOCUMENT_ID);assertThat(dto.getStoredDocumentId()).isEqualTo(STORED_DOCUMENT_ID).isNotEqualTo(dto.getDocumentId());assertThat(dto.getActionId()).isEqualTo(ACTION);assertThat(dto.getFileName()).isEqualTo("id_card.jpg");assertThat(dto.getSize()).isEqualTo(3L);assertThat(dto.getVerificationStatus()).isEqualTo("PENDING");assertThat(dto.getAssociationState()).isEqualTo("ATTACHED");}).verifyComplete();verifyNoInteractions(loanOriginationApi);
    }
    @Test void uploadDocument_passesDocumentTypeAsCodeAndDerivesMimeTypeFromFileName(){
        var command=command("nomina_enero_2025.pdf","PAYSLIP",new byte[]{1,2,3,4});when(evidencePorts.getIfUnique()).thenReturn(evidence);when(evidence.upload(eq(APPLICATION_ID),any())).thenReturn(Mono.just(receipt(command.getFileName(),"PAYSLIP","application/pdf",4)));StepVerifier.create(service.uploadDocument(APPLICATION_ID,command)).expectNextCount(1).verifyComplete();var sent=ArgumentCaptor.forClass(ApplicationEvidenceUpload.class);verify(evidence).upload(eq(APPLICATION_ID),sent.capture());assertThat(sent.getValue().documentTypeCode()).isEqualTo("PAYSLIP");assertThat(sent.getValue().mediaType()).isEqualTo("application/pdf");assertThat(sent.getValue().fileName()).isEqualTo(command.getFileName());assertThat(sent.getValue().content()).isEqualTo(new byte[]{1,2,3,4});assertThat(sent.getValue().actionId()).isEqualTo(ACTION);verifyNoInteractions(loanOriginationApi);
    }
    @Test void inferMimeType_returnsOctetStreamForUnknownOrMissingExtension(){for(String name:Arrays.asList(null,"noextension","trailing.dot.","weird.unknownext"))assertThat(ApplicationDocumentsServiceImpl.inferMimeType(name)).isEqualTo("application/octet-stream");}
    @Test void inferMimeType_recognisesCommonExtensions(){assertThat(ApplicationDocumentsServiceImpl.inferMimeType("payslip.pdf")).isEqualTo("application/pdf");assertThat(ApplicationDocumentsServiceImpl.inferMimeType("id.JPG")).isEqualTo("image/jpeg");assertThat(ApplicationDocumentsServiceImpl.inferMimeType("scan.jpeg")).isEqualTo("image/jpeg");assertThat(ApplicationDocumentsServiceImpl.inferMimeType("logo.png")).isEqualTo("image/png");assertThat(ApplicationDocumentsServiceImpl.inferMimeType("data.csv")).isEqualTo("text/csv");}
    @Test void uploadDocument_propagatesUpstreamError(){var error=new ApplicationEvidenceFailure(ACTION,ApplicationEvidenceError.Outcome.DOCUMENT_OUTCOME_UNKNOWN,Map.of());when(evidencePorts.getIfUnique()).thenReturn(evidence);when(evidence.upload(eq(APPLICATION_ID),any())).thenReturn(Mono.error(error));StepVerifier.create(service.uploadDocument(APPLICATION_ID,command("fail.pdf","ID_DOCUMENT",new byte[]{1}))).expectErrorSatisfies(actual->assertThat(actual).isSameAs(error)).verify();verifyNoInteractions(loanOriginationApi);}
    @Test void uploadDocument_failsWithBusinessException_whenAttachResponseMissingDocumentId(){var valid=receipt("anonymous.pdf","ID_DOCUMENT","application/pdf",1);var metadataOnly=new ApplicationEvidenceReceipt(ACTION,APPLICATION_ID,PARTY,null,DOCUMENT_ID,valid.fileName(),valid.documentTypeCode(),valid.mediaType(),1,valid.contentDigest(),valid.documentIntentDigest(),valid.associationIntentDigest(),valid.storedAt(),valid.attachedAt(),"ATTACHED","PENDING",false);when(evidencePorts.getIfUnique()).thenReturn(evidence);when(evidence.upload(eq(APPLICATION_ID),any())).thenReturn(Mono.just(metadataOnly));StepVerifier.create(service.uploadDocument(APPLICATION_ID,command("anonymous.pdf","ID_DOCUMENT",new byte[]{9}))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationEvidenceFailure.class);assertThat(((ApplicationEvidenceFailure)error).outcome()).isEqualTo(ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN);}).verify();}
    @Test void deleteDocument_deletesTheStoredRecord(){when(loanOriginationApi.deleteApplicationDocument(eq(APPLICATION_ID),eq(DOCUMENT_ID),any())).thenReturn(Mono.empty());StepVerifier.create(service.deleteDocument(APPLICATION_ID,DOCUMENT_ID)).verifyComplete();}
    static UploadDocumentCommand command(String name,String type,byte[] bytes){return new UploadDocumentCommand(APPLICATION_ID,name,type,bytes,ACTION);}
    static String hash(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static ApplicationEvidenceReceipt receipt(String name,String type,String mime,long length){Instant time=Instant.parse("2026-11-02T00:00:00.123456Z");return new ApplicationEvidenceReceipt(ACTION,APPLICATION_ID,PARTY,STORED_DOCUMENT_ID,DOCUMENT_ID,name,type,mime,length,hash(length==1?new byte[]{9}:length==4?new byte[]{1,2,3,4}:new byte[]{1,2,3}),"b".repeat(64),"c".repeat(64),time,time,"ATTACHED","PENDING",false);}
}

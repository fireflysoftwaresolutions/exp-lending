/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.services;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.LoanOriginationApi;
import io.fireflysoftware.experience.lending.core.application.documents.commands.UploadDocumentCommand;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import io.fireflysoftware.experience.lending.core.application.documents.services.impl.ApplicationDocumentsServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Boundary validation and same-action delegation only, never owner/native proof. */
class ApplicationEvidenceInputRecoveryTest {
    final LoanOriginationApi legacy=mock(LoanOriginationApi.class);final ObjectProvider<ApplicationEvidencePort> ports=mock(ObjectProvider.class);final ApplicationEvidencePort domain=mock(ApplicationEvidencePort.class);final ApplicationDocumentsServiceImpl service=new ApplicationDocumentsServiceImpl(legacy,ports);
    @Test void missingActionCannotBecomeAnAutomaticallyGeneratedOrFingerprintAction(){var request=ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",new byte[]{1});request.setActionId(null);invalid(request);}
    @Test void oversizedFilenameAndForeignRouteApplicationRefuseBeforeDispatch(){var request=ApplicationDocumentsServiceImplTest.command("a".repeat(201),"PAYSLIP",new byte[]{1});invalid(request);request=ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",new byte[]{1});request.setApplicationId(UUID.randomUUID());invalid(request);}
    @Test void callerByteArrayAndGetterMutationsCannotChangeCapturedContent(){byte[] bytes={1,2,3};var request=new UploadDocumentCommand(ApplicationDocumentsServiceImplTest.APPLICATION_ID,"evidence.pdf","PAYSLIP",bytes,ApplicationDocumentsServiceImplTest.ACTION);bytes[0]=9;request.getContent()[1]=9;assertThat(request.getContent()).isEqualTo(new byte[]{1,2,3});}
    @Test void recoveryPassesOnlyTheSameOriginalActionWithoutAnotherUpload(){when(ports.getIfUnique()).thenReturn(domain);when(domain.recover(ApplicationDocumentsServiceImplTest.APPLICATION_ID,ApplicationDocumentsServiceImplTest.ACTION)).thenReturn(Mono.just(ApplicationDocumentsServiceImplTest.receipt("evidence.pdf","PAYSLIP","application/pdf",3)));StepVerifier.create(service.recoverDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,ApplicationDocumentsServiceImplTest.ACTION)).assertNext(value->assertThat(value.getActionId()).isEqualTo(ApplicationDocumentsServiceImplTest.ACTION)).verifyComplete();verify(domain).recover(ApplicationDocumentsServiceImplTest.APPLICATION_ID,ApplicationDocumentsServiceImplTest.ACTION);verify(domain,never()).upload(any(),any());verifyNoInteractions(legacy);}
    @Test void absentProviderIsTypedUnavailableAndNeverUsesLegacyMetadataFallback(){when(ports.getIfUnique()).thenReturn(null);StepVerifier.create(service.uploadDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",new byte[]{1}))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationEvidenceFailure.class);assertThat(((ApplicationEvidenceFailure)error).outcome()).isEqualTo(ApplicationEvidenceError.Outcome.OBSERVATION_UNAVAILABLE);}).verify();verifyNoInteractions(legacy,domain);}
    @Test void completeServiceReceiptCannotForgePartyDigestStateLengthOrUploadEcho(){
        var original=ApplicationDocumentsServiceImplTest.receipt("evidence.pdf","PAYSLIP","application/pdf",3);when(ports.getIfUnique()).thenReturn(domain);
        for(var bad:List.of(changed(original,new UUID(0,0),3,original.contentDigest(),"ATTACHED","evidence.pdf"),changed(original,original.partyId(),3,"not-a-digest","ATTACHED","evidence.pdf"),changed(original,original.partyId(),4,original.contentDigest(),"ATTACHED","evidence.pdf"),changed(original,original.partyId(),3,original.contentDigest(),"VERIFIED","evidence.pdf"),changed(original,original.partyId(),3,original.contentDigest(),"ATTACHED","another.pdf"),changed(original,original.partyId(),3,ApplicationDocumentsServiceImplTest.hash(new byte[]{4,5,6}),"ATTACHED","evidence.pdf"))){when(domain.upload(any(),any())).thenReturn(Mono.just(bad));StepVerifier.create(service.uploadDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",new byte[]{1,2,3}))).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ApplicationEvidenceFailure.class);assertThat(((ApplicationEvidenceFailure)error).outcome()).isEqualTo(ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN);}).verify();}verifyNoInteractions(legacy);
    }
    private static ApplicationEvidenceReceipt changed(ApplicationEvidenceReceipt value,UUID party,long length,String digest,String state,String name){return new ApplicationEvidenceReceipt(value.actionId(),value.applicationId(),party,value.documentId(),value.associationId(),name,value.documentTypeCode(),value.mediaType(),length,digest,value.documentIntentDigest(),value.associationIntentDigest(),value.storedAt(),value.attachedAt(),state,value.verificationStatus(),value.replayed());}
    private void invalid(UploadDocumentCommand request){StepVerifier.create(service.uploadDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,request)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(org.fireflyframework.web.error.exceptions.BusinessException.class);assertThat(((org.fireflyframework.web.error.exceptions.BusinessException)error).getCode()).isEqualTo("APPLICATION_EVIDENCE_INPUT_INVALID");}).verify();verifyNoInteractions(legacy,ports,domain);}
}

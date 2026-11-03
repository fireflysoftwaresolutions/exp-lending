/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.services;
import io.fireflysoftware.domain.lending.loan.origination.sdk.api.LoanOriginationApi;
import io.fireflysoftware.experience.lending.core.application.documents.commands.UploadDocumentCommand;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.*;
import io.fireflysoftware.experience.lending.core.application.documents.services.impl.ApplicationDocumentsServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Maintains the three actually witnessed RED causes under the new explicit-action contract. Boundary only. */
class ApplicationEvidenceCustodyContractTest {
    final LoanOriginationApi legacy=mock(LoanOriginationApi.class);final ObjectProvider<ApplicationEvidencePort> ports=mock(ObjectProvider.class);final ApplicationEvidencePort domain=mock(ApplicationEvidencePort.class);final ApplicationDocumentsServiceImpl service=new ApplicationDocumentsServiceImpl(legacy,ports);
    @Test void completeContentMustReachTheOwningDomainInsteadOfOnlyItsLength(){
        when(ports.getIfUnique()).thenReturn(domain);when(domain.upload(any(),any())).thenReturn(Mono.error(new ApplicationEvidenceFailure(ApplicationDocumentsServiceImplTest.ACTION,ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN,Map.of())));var command=ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",new byte[]{1,2,3});service.uploadDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,command).onErrorResume(error->Mono.empty()).block();var request=ArgumentCaptor.forClass(ApplicationEvidenceUpload.class);verify(domain).upload(eq(ApplicationDocumentsServiceImplTest.APPLICATION_ID),request.capture());assertThat(request.getValue().content()).isEqualTo(new byte[]{1,2,3});assertThat(Base64.getEncoder().encodeToString(request.getValue().content())).isEqualTo("AQID");verifyNoInteractions(legacy);
    }
    @Test void differentContentWithEqualLengthCannotShareTheSameOwnerAction(){
        when(ports.getIfUnique()).thenReturn(domain);when(domain.upload(any(),any())).thenAnswer(invocation->Mono.error(new ApplicationEvidenceFailure(((ApplicationEvidenceUpload)invocation.getArgument(1)).actionId(),ApplicationEvidenceError.Outcome.OUTCOME_UNKNOWN,Map.of())));var first=ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",new byte[]{1,2,3});var second=ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",new byte[]{4,5,6});second.setActionId(UUID.randomUUID());service.uploadDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,first).onErrorResume(error->Mono.empty()).block();service.uploadDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,second).onErrorResume(error->Mono.empty()).block();var sent=ArgumentCaptor.forClass(ApplicationEvidenceUpload.class);verify(domain,times(2)).upload(any(),sent.capture());assertThat(sent.getAllValues().get(0).actionId()).isEqualTo(first.getActionId()).isNotEqualTo(sent.getAllValues().get(1).actionId());assertThat(sent.getAllValues().get(1).actionId()).isEqualTo(second.getActionId());assertThat(sent.getAllValues().get(0).content()).isNotEqualTo(sent.getAllValues().get(1).content());verifyNoInteractions(legacy);
    }
    @Test void metadataWithoutStoredDocumentCustodyCannotBecomeAnUploadedReceipt(){var command=ApplicationDocumentsServiceImplTest.command("evidence.pdf","PAYSLIP",null);StepVerifier.create(service.uploadDocument(ApplicationDocumentsServiceImplTest.APPLICATION_ID,command)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(org.fireflyframework.web.error.exceptions.BusinessException.class);assertThat(((org.fireflyframework.web.error.exceptions.BusinessException)error).getCode()).isEqualTo("APPLICATION_EVIDENCE_INPUT_INVALID");}).verify();verifyNoInteractions(legacy,ports,domain);}
}

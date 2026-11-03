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


package io.fireflysoftware.experience.lending.core.application.documents.queries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Metadata record for a document attached to a loan application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDocumentDTO {

    @Schema(description="Legacy application-document association identifier; equals associationId, not storedDocumentId")
    private UUID documentId;
    private UUID applicationId;
    private String fileName;
    private String documentType;
    @Schema(description="Legacy UTC display projection of attachedAt; authoritative owner timestamps are storedAt and attachedAt")
    private LocalDateTime uploadedAt;
    private Long size;

    private UUID actionId;
    private UUID partyId;
    private UUID associationId;
    private UUID storedDocumentId;
    private String documentTypeCode;
    private String mediaType;
    private String contentDigest;
    private String documentIntentDigest;
    private String associationIntentDigest;
    @Schema(ref="#/components/schemas/FireflyInstant")
    private Instant storedAt;
    @Schema(ref="#/components/schemas/FireflyInstant")
    private Instant attachedAt;
    private String associationState;
    private String verificationStatus;
    private Boolean replayed;

    public ApplicationDocumentDTO(UUID documentId,UUID applicationId,String fileName,String documentType,LocalDateTime uploadedAt,Long size){this.documentId=documentId;this.applicationId=applicationId;this.fileName=fileName;this.documentType=documentType;this.uploadedAt=uploadedAt;this.size=size;}
}

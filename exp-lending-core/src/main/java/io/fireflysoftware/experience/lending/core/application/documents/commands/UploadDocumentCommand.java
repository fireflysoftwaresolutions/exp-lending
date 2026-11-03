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


package io.fireflysoftware.experience.lending.core.application.documents.commands;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.validation.BoundedApplicationEvidenceContent;

/**
 * Command to upload and attach a document to a loan application.
 */
@Data
@NoArgsConstructor
public class UploadDocumentCommand {

    private UUID applicationId;
    @NotBlank @Size(max=200)
    private String fileName;
    @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,49}")
    private String documentType;
    @NotNull @BoundedApplicationEvidenceContent
    @Schema(type="string",format="byte",minLength=4,maxLength=2796204,description="Base64 of 1 through 2097152 decoded content bytes; JSON envelope bounded to 3145728 bytes")
    private byte[] content;

    @NotNull
    private UUID actionId;

    public UploadDocumentCommand(UUID applicationId,String fileName,String documentType,byte[] content,UUID actionId){this(applicationId,fileName,documentType,content);this.actionId=actionId;}
    public UploadDocumentCommand(UUID applicationId,String fileName,String documentType,byte[] content){this.applicationId=applicationId;this.fileName=fileName;this.documentType=documentType;setContent(content);}
    public byte[] getContent(){return content==null?null:content.clone();}
    public void setContent(byte[] value){content=value==null?null:value.clone();}
    @Override public String toString(){return "UploadDocumentCommand[redacted]";}
}

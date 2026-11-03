/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import java.util.UUID;
public record ApplicationEvidenceUpload(UUID actionId,String fileName,String documentTypeCode,String mediaType,byte[] content){
    public ApplicationEvidenceUpload{content=content==null?null:content.clone();}
    @Override public byte[] content(){return content==null?null:content.clone();}
    @Override public String toString(){return "ApplicationEvidenceUpload[redacted]";}
}

/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence.validation.impl;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.validation.BoundedApplicationEvidenceContent;
import jakarta.validation.*;
public final class BoundedApplicationEvidenceContentValidatorImpl implements ConstraintValidator<BoundedApplicationEvidenceContent,byte[]> {
    @Override public boolean isValid(byte[] value,ConstraintValidatorContext context){return value!=null&&value.length>=1&&value.length<=2097152;}
}

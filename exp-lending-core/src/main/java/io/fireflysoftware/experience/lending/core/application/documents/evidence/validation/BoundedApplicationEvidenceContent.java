/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence.validation;
import io.fireflysoftware.experience.lending.core.application.documents.evidence.validation.impl.BoundedApplicationEvidenceContentValidatorImpl;
import jakarta.validation.*;
import java.lang.annotation.*;
@Documented @Constraint(validatedBy=BoundedApplicationEvidenceContentValidatorImpl.class)
@Target({ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.ANNOTATION_TYPE}) @Retention(RetentionPolicy.RUNTIME)
public @interface BoundedApplicationEvidenceContent {String message() default "Content must contain 1 through 2097152 decoded bytes";Class<?>[] groups() default {};Class<? extends Payload>[] payload() default {};}

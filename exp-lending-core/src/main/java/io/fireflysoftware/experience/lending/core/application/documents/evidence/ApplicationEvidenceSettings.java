/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.application.documents.evidence;
import java.net.URI;
import io.fireflysoftware.experience.lending.core.intake.ApplicationIntakeExchangeSettings;
public record ApplicationEvidenceSettings(String issuer,String incomingAudience,String incomingClientId,String platformTenant,String domainAudience,String domainClientId,URI domainEndpoint,boolean ownedLoopbackSandbox){
    public ApplicationEvidenceSettings{for(String value:new String[]{issuer,incomingAudience,incomingClientId,platformTenant,domainAudience,domainClientId})if(value==null||value.isBlank()||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Explicit application evidence exchange profile required");ApplicationIntakeExchangeSettings.endpoint(domainEndpoint,ownedLoopbackSandbox);}
    @Override public String toString(){return "ApplicationEvidenceSettings[redacted]";}
}

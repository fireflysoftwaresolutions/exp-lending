/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.contract;
import java.net.URI;
import io.fireflysoftware.experience.lending.core.intake.ApplicationIntakeExchangeSettings;
public record ApplicationInitialContractSettings(String issuer,String incomingAudience,String incomingClientId,String platformTenant,String domainAudience,String domainClientId,URI domainEndpoint,boolean ownedLoopbackSandbox){
    public ApplicationInitialContractSettings{for(String value:new String[]{issuer,incomingAudience,incomingClientId,platformTenant,domainAudience,domainClientId})if(value==null||value.isBlank()||!value.equals(value.strip())||value.length()>512||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Explicit original application contract exchange profile required");ApplicationIntakeExchangeSettings.endpoint(domainEndpoint,ownedLoopbackSandbox);}
    @Override public String toString(){return "ApplicationInitialContractSettings[redacted]";}
}

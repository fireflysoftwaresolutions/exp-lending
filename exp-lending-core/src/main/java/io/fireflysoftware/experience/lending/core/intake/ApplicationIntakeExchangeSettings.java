/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake;
import java.net.URI;
public record ApplicationIntakeExchangeSettings(String issuer,String incomingAudience,String incomingClientId,
        String platformTenant,String domainAudience,String domainClientId,URI domainEndpoint,boolean ownedLoopbackSandbox){
    public ApplicationIntakeExchangeSettings{
        for(String value:new String[]{issuer,incomingAudience,incomingClientId,platformTenant,domainAudience,domainClientId})
            if(value==null || value.isBlank() || value.length()>512 || value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Exact lending exchange profile required");
        endpoint(domainEndpoint,ownedLoopbackSandbox);
    }
    public static URI endpoint(URI value,boolean sandbox){
        if(value==null || value.getHost()==null || value.getUserInfo()!=null || value.getQuery()!=null || value.getFragment()!=null
            || !("https".equals(value.getScheme()) || sandbox && "http".equals(value.getScheme()) && "127.0.0.1".equals(value.getHost()) && value.getPort()>0))
            throw new IllegalArgumentException("HTTPS or explicitly owned sandbox loopback endpoint required");
        return value;
    }
    @Override public String toString(){return "ApplicationIntakeExchangeSettings[redacted]";}
}

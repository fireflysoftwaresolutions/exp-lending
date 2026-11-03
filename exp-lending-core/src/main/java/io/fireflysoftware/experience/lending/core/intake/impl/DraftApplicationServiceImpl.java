/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.core.intake.impl;
import io.fireflysoftware.experience.lending.core.intake.DraftApplicationService;
import io.fireflysoftware.experience.lending.core.intake.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
@Service
public final class DraftApplicationServiceImpl implements DraftApplicationService {
    private final ObjectProvider<DomainDraftIntakePort> owner;
    public DraftApplicationServiceImpl(ObjectProvider<DomainDraftIntakePort> owner){this.owner=owner;}
    @Override public Mono<DraftApplicationReceipt> create(DraftApplicationRequest request){return Mono.defer(()->{
        var adapter=owner.getIfUnique();if(adapter==null)return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Authenticated lending intake owner unavailable"));
        if(request==null)return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact intake request required"));
        return adapter.create(request).switchIfEmpty(Mono.error(unavailable())).map(value->verified(request,value));
    });}
    private static DraftApplicationReceipt verified(DraftApplicationRequest request,DraftApplicationReceipt value){
        if(value.applicationId()==null || value.applicationId().equals(new UUID(0,0))
            || request.requestId()==null || !request.requestId().equals(value.requestId())
            || request.simulationId()==null || !request.simulationId().equals(value.simulationId())
            || request.productId()==null || !request.productId().equals(value.productId())
            || request.partyId()==null || !request.partyId().equals(value.partyId())
            || request.requestedAmount()==null || value.requestedAmount()==null || request.requestedAmount().compareTo(value.requestedAmount())!=0
            || request.term()==null || request.term()!=value.term() || request.currency()==null || !request.currency().equals(value.currency())
            || !Objects.equals(request.purpose(),value.purpose()) || !"DRAFT".equals(value.initialStatusCode())
            || !"PENDING_DOCUMENTS".equals(value.initialSubStatusCode()) || value.createdAt()==null
            || value.intentDigest()==null || !value.intentDigest().matches("[0-9a-f]{64}"))throw unavailable();
        return value;
    }
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Lending intake owner returned an invalid receipt");}
}

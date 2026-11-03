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


package io.fireflysoftware.experience.lending.core.adapter;

import io.fireflysoftware.domain.configuration.sdk.api.BusinessParametersApi;
import io.fireflysoftware.domain.configuration.sdk.model.BusinessParameter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpBusinessParameterAdapterTest {

    @Test
    void findByKeyReadsTheConfigurationDomainSdk() {
        BusinessParametersApi api = mock(BusinessParametersApi.class);
        when(api.getBusinessParameterByKey("journey.lending.min-credit-score"))
                .thenReturn(Mono.just(new BusinessParameter().value("640")));
        HttpBusinessParameterAdapterImpl adapter = new HttpBusinessParameterAdapterImpl(api);

        String value = adapter.findByKey("journey.lending.min-credit-score").block(Duration.ofSeconds(2));

        verify(api).getBusinessParameterByKey("journey.lending.min-credit-score");
        assertEquals("640", value);
    }
}

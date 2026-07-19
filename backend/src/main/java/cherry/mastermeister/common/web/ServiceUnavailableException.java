/*
 * Copyright 2026 agwlvssainokuni
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
package cherry.mastermeister.common.web;

import org.springframework.http.HttpStatus;

/**
 * 一時的な混雑(プール枯渇等)— 503 + Retry-After(NFR Design Q2=B)。
 * 上流 DB 障害(502 系)と区別し、待って再試行すれば成功し得ることを示す。
 */
public class ServiceUnavailableException extends ApiException {

    private final long retryAfterSeconds;

    public ServiceUnavailableException(String code, long retryAfterSeconds) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

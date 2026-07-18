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
package cherry.mastermeister.audit;

import java.util.Map;

/**
 * 監査イベント。全機能が {@link AuditEventPublisher} 経由で発行する(D-20)。
 * detail にはコード・数値・ID のみを入れる(表示文字列禁止 — H-07)。
 * connectionId / resource は④以降のデータアクセス記録で使用する。
 */
public record AuditEvent(
        String eventType,
        String actor,
        Long connectionId,
        String resource,
        AuditOutcome outcome,
        Map<String, Object> detail) {

    public static AuditEvent success(String eventType, String actor) {
        return new AuditEvent(eventType, actor, null, null, AuditOutcome.SUCCESS, Map.of());
    }

    public static AuditEvent success(String eventType, String actor, Map<String, Object> detail) {
        return new AuditEvent(eventType, actor, null, null, AuditOutcome.SUCCESS, detail);
    }

    public static AuditEvent failure(String eventType, String actor, Map<String, Object> detail) {
        return new AuditEvent(eventType, actor, null, null, AuditOutcome.FAILURE, detail);
    }
}

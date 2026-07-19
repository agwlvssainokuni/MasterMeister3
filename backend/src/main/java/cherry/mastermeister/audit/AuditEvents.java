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

/**
 * 監査イベント種別カタログ(Phase 1 — business-rules.md §5)。
 * 拡張規約(④〜⑥): 「名詞_過去分詞」の大文字スネークで追加し、
 * detail にはコード・数値・ID のみを入れる(表示文字列禁止 — H-07)。
 */
public final class AuditEvents {

    public static final String REGISTRATION_REQUESTED = "REGISTRATION_REQUESTED";
    public static final String REGISTRATION_COMPLETED = "REGISTRATION_COMPLETED";
    public static final String USER_APPROVED = "USER_APPROVED";
    public static final String USER_REJECTED = "USER_REJECTED";
    public static final String ADMIN_BOOTSTRAPPED = "ADMIN_BOOTSTRAPPED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_UNLOCKED = "ACCOUNT_UNLOCKED";
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    public static final String LOGOUT = "LOGOUT";
    public static final String MAIL_SEND_FAILED = "MAIL_SEND_FAILED";
    public static final String SECURITY_ALERT_SENT = "SECURITY_ALERT_SENT";

    // ユニット④: 管理操作(US-043 — business-rules.md §7)
    public static final String CONNECTION_CREATED = "CONNECTION_CREATED";
    public static final String CONNECTION_UPDATED = "CONNECTION_UPDATED";
    public static final String CONNECTION_DELETED = "CONNECTION_DELETED";
    public static final String CONNECTION_TESTED = "CONNECTION_TESTED";
    public static final String SCHEMA_IMPORTED = "SCHEMA_IMPORTED";
    public static final String PERMISSION_SET = "PERMISSION_SET";
    public static final String PERMISSION_REMOVED = "PERMISSION_REMOVED";
    public static final String GROUP_CREATED = "GROUP_CREATED";
    public static final String GROUP_RENAMED = "GROUP_RENAMED";
    public static final String GROUP_DELETED = "GROUP_DELETED";
    public static final String GROUP_MEMBER_ADDED = "GROUP_MEMBER_ADDED";
    public static final String GROUP_MEMBER_REMOVED = "GROUP_MEMBER_REMOVED";
    public static final String PERMISSION_EXPORTED = "PERMISSION_EXPORTED";
    public static final String PERMISSION_IMPORTED = "PERMISSION_IMPORTED";

    private AuditEvents() {
    }
}

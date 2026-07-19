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
package cherry.mastermeister.user;

/**
 * ユーザ向けメール通知の出口。実装はメール基盤(common.mail)側で提供する。
 * 送信失敗は例外にせず false を返す(失敗時の監査記録 MAIL_SEND_FAILED は実装側の責務)。
 * 業務処理はメール失敗で失敗しない(Q4=A)。
 */
public interface UserNotificationGateway {

    /** 登録確認メール(US-001)。リンク URL は実装側が base-url + トークンで組み立てる。 */
    boolean sendRegistrationConfirm(String email, String language, String token);

    /** 承認通知メール(US-003)。受信者言語は user.language。 */
    boolean sendUserApproved(AppUser user);

    /** 却下通知メール(US-003)。受信者言語は user.language。 */
    boolean sendUserRejected(AppUser user);
}

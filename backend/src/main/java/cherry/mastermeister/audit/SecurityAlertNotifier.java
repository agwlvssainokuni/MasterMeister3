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
 * セキュリティアラートの通知手段。実装(管理者全員へのメール送信)は
 * メール基盤(common.mail)側で提供する — audit パッケージはメールに依存しない。
 */
public interface SecurityAlertNotifier {

    /**
     * 管理者へアラートを通知する。
     *
     * @param alertType アラート種別コード(例: LOGIN_FAILURE_BURST)
     * @param count     時間窓内の対象イベント件数(即時通知は 1)
     */
    void sendAlert(String alertType, long count);
}

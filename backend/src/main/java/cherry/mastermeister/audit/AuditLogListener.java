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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 監査イベントの受け口(NFR-U3-03)。
 * {@code @EventListener}(@TransactionalEventListener ではない)— 主処理の成否にかかわらず
 * 同一スレッドで即時実行し、永続化は REQUIRES_NEW で独立コミットする。
 * リスナー内の例外は主処理へ伝播させない(ERROR ログに退避)。
 */
@Component
public class AuditLogListener {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogListener.class);

    private final AuditLogService auditLogService;
    private final SecurityAlertService securityAlertService;

    public AuditLogListener(AuditLogService auditLogService, SecurityAlertService securityAlertService) {
        this.auditLogService = auditLogService;
        this.securityAlertService = securityAlertService;
    }

    @EventListener
    public void on(AuditEvent event) {
        try {
            auditLogService.record(event);
        } catch (RuntimeException e) {
            logger.error("Failed to record audit event: {}", event.eventType(), e);
        }
        try {
            securityAlertService.evaluate(event);
        } catch (RuntimeException e) {
            logger.error("Failed to evaluate security alert for event: {}", event.eventType(), e);
        }
    }
}

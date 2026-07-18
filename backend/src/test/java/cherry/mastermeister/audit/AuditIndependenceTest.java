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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 監査記録の独立性(NFR-U3-03)の例外注入テスト。
 * (1) 主処理がロールバックしても監査記録は残る(REQUIRES_NEW)。
 * (2) 監査 INSERT 自体の失敗は主処理へ伝播しない。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditIndependenceTest {

    @Autowired
    private TxProbe txProbe;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAll();
    }

    @Test
    void 主処理がロールバックしても監査記録は残る() {
        assertThatThrownBy(() -> txProbe.publishThenFail())
                .isInstanceOf(IllegalStateException.class);

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getEventType()).isEqualTo(AuditEvents.LOGIN_FAILED);
                    assertThat(entry.getActor()).isEqualTo("probe@example.com");
                    assertThat(entry.getOutcome()).isEqualTo("FAILURE");
                    assertThat(entry.getDetailJson()).contains("BAD_CREDENTIALS");
                });
    }

    @Test
    void 監査INSERT失敗は主処理へ伝播しない() {
        // event_type は VARCHAR(50) — 超過させて INSERT を失敗させる(例外注入)
        String oversizedType = "X".repeat(60);

        txProbe.publishOversized(oversizedType);

        assertThat(auditLogRepository.findAll())
                .noneSatisfy(entry -> assertThat(entry.getEventType()).isEqualTo(oversizedType));
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        TxProbe txProbe(AuditEventPublisher publisher) {
            return new TxProbe(publisher);
        }
    }

    /** 主処理を模したプローブ。publishThenFail はトランザクションをロールバックさせる。 */
    static class TxProbe {

        private final AuditEventPublisher publisher;

        TxProbe(AuditEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishThenFail() {
            publisher.publish(AuditEvent.failure(
                    AuditEvents.LOGIN_FAILED, "probe@example.com",
                    Map.of("reason", "BAD_CREDENTIALS")));
            throw new IllegalStateException("main transaction rollback");
        }

        @Transactional
        public void publishOversized(String oversizedType) {
            publisher.publish(AuditEvent.success(oversizedType, "probe@example.com"));
        }
    }
}

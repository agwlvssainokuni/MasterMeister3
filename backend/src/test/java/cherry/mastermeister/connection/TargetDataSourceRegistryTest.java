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
package cherry.mastermeister.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.dialect.DbType;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 接続別プールの遅延生成・キャッシュ・破棄(NFR-U4-02)。
 * H2 インメモリをターゲットに、同一インスタンスの再利用と evict 後の再生成を検証する。
 */
class TargetDataSourceRegistryTest {

    private final DbConnectionRepository repository = mock(DbConnectionRepository.class);
    private final CredentialEncryptor encryptor = encryptor();
    private final TargetDataSourceRegistry registry =
            new TargetDataSourceRegistry(repository, encryptor);

    private static CredentialEncryptor encryptor() {
        String key = Base64.getEncoder().encodeToString(
                "A".repeat(32).getBytes(StandardCharsets.UTF_8));
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef",
                        Duration.ofMinutes(10), Duration.ofHours(24)),
                new AppProperties.Credential(Map.of("k1", key), "k1"),
                new AppProperties.UserRegistration(Duration.ofHours(3)),
                new AppProperties.Auth(new AppProperties.Auth.Lockout(5, Duration.ofMinutes(15))),
                new AppProperties.SecurityAlert(Duration.ofMinutes(10), 10),
                new AppProperties.Admin(new AppProperties.Admin.Bootstrap(null, null)),
                new AppProperties.Mail("noreply@mastermeister.local", "http://localhost:8080"),
                new AppProperties.Permission(Duration.ofMinutes(10), 1000));
        return new CredentialEncryptor(properties);
    }

    private DbConnection h2Connection(long id) {
        DbConnection connection = new DbConnection();
        ReflectionTestUtils.setField(connection, "id", id);
        connection.setName("registry-test-" + id);
        connection.setDbType(DbType.H2);
        connection.setHost("mem:registry-test-" + id);
        connection.setDatabaseName("unused");
        connection.setUsername("sa");
        connection.setPasswordEnc(encryptor.encrypt("sa-password"));
        connection.setOptions("DB_CLOSE_DELAY=-1");
        connection.setPoolMaxSize(2);
        connection.setPoolTimeoutMs(2000);
        return connection;
    }

    @AfterEach
    void cleanup() {
        registry.shutdown();
    }

    @Test
    void 遅延生成しキャッシュされたインスタンスを再利用する() throws Exception {
        when(repository.findById(eq(1L))).thenReturn(Optional.of(h2Connection(1L)));

        DataSource first = registry.get(1L);
        DataSource second = registry.get(1L);
        assertThat(second).isSameAs(first);

        try (Connection connection = first.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }

    @Test
    void evictでクローズされ次回取得時に再生成される() throws Exception {
        when(repository.findById(eq(2L))).thenReturn(Optional.of(h2Connection(2L)));

        HikariDataSource first = (HikariDataSource) registry.get(2L);
        registry.evict(2L);
        assertThat(first.isClosed()).isTrue();

        HikariDataSource second = (HikariDataSource) registry.get(2L);
        assertThat(second).isNotSameAs(first);
        try (Connection connection = second.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }
}

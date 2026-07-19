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

import cherry.mastermeister.audit.AuditEvent;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditEvents;
import cherry.mastermeister.audit.AuditOutcome;
import cherry.mastermeister.common.dialect.DbDialect;
import cherry.mastermeister.common.dialect.DbDialects;
import cherry.mastermeister.common.dialect.DbType;
import cherry.mastermeister.common.event.PermissionCacheInvalidated;
import cherry.mastermeister.common.web.BadRequestException;
import cherry.mastermeister.common.web.ConflictException;
import cherry.mastermeister.common.web.NotFoundException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接続管理(US-010/011 — business-rules.md §1)。
 * パスワードは暗号文のみ保存し、応答・ログ・監査 detail のいずれにも出さない。
 * dbType は作成後変更不可。変更系はすべて監査記録する。
 */
@Service
public class ConnectionService {

    /** 接続テストの疎通確認タイムアウト(秒)— 実装既定値。 */
    private static final int TEST_VALID_TIMEOUT_SECONDS = 5;

    public record ConnectionData(
            String name, DbType dbType, String host, Integer port, String databaseName,
            String username, String password, String options,
            Integer poolMaxSize, Integer poolTimeoutMs) {
    }

    public record TestParams(
            Long id, DbType dbType, String host, Integer port, String databaseName,
            String username, String password, String options) {
    }

    public record TestResult(boolean success, String reason) {
    }

    private final DbConnectionRepository connectionRepository;
    private final CredentialEncryptor credentialEncryptor;
    private final TargetDataSourceRegistry dataSourceRegistry;
    private final AuditEventPublisher auditEventPublisher;
    private final ApplicationEventPublisher eventPublisher;

    public ConnectionService(
            DbConnectionRepository connectionRepository,
            CredentialEncryptor credentialEncryptor,
            TargetDataSourceRegistry dataSourceRegistry,
            AuditEventPublisher auditEventPublisher,
            ApplicationEventPublisher eventPublisher) {
        this.connectionRepository = connectionRepository;
        this.credentialEncryptor = credentialEncryptor;
        this.dataSourceRegistry = dataSourceRegistry;
        this.auditEventPublisher = auditEventPublisher;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<DbConnection> list() {
        return connectionRepository.findAll(
                org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public DbConnection get(Long id) {
        return require(id);
    }

    @Transactional
    public DbConnection create(ConnectionData data, String actor) {
        requireNameAvailable(data.name(), null);
        DbConnection connection = new DbConnection();
        connection.setName(data.name());
        connection.setDbType(data.dbType());
        applyMutableFields(connection, data);
        connection.setPasswordEnc(credentialEncryptor.encrypt(data.password()));
        connectionRepository.save(connection);
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.CONNECTION_CREATED, actor, connection.getId(), null,
                AuditOutcome.SUCCESS, Map.of("name", connection.getName())));
        return connection;
    }

    @Transactional
    public DbConnection update(Long id, ConnectionData data, String actor) {
        DbConnection connection = require(id);
        if (data.dbType() != null && data.dbType() != connection.getDbType()) {
            throw new BadRequestException("CONNECTION_TYPE_IMMUTABLE");
        }
        if (!connection.getName().equals(data.name())) {
            requireNameAvailable(data.name(), id);
            connection.setName(data.name());
        }
        applyMutableFields(connection, data);
        if (data.password() != null && !data.password().isBlank()) {
            // 変更時のみ再暗号化(常に active 鍵 — 段階的ローテーション)
            connection.setPasswordEnc(credentialEncryptor.encrypt(data.password()));
        }
        dataSourceRegistry.evict(id);
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.CONNECTION_UPDATED, actor, id, null,
                AuditOutcome.SUCCESS, Map.of("name", connection.getName())));
        return connection;
    }

    @Transactional
    public void delete(Long id, String actor) {
        DbConnection connection = require(id);
        String name = connection.getName();
        connectionRepository.delete(connection);
        dataSourceRegistry.evict(id);
        eventPublisher.publishEvent(new PermissionCacheInvalidated());
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.CONNECTION_DELETED, actor, id, null,
                AuditOutcome.SUCCESS, Map.of("name", name)));
    }

    /**
     * 接続テスト(Q4=A + レビュー確認 2)。未保存のフォーム値で実行できる。
     * password 省略時は id 必須(保存済み資格情報で補完)。プールは経由しない単発接続。
     */
    public TestResult test(TestParams params, String actor) {
        Long connectionId = params.id();
        String password = params.password();
        if (password == null || password.isBlank()) {
            if (connectionId == null) {
                throw new BadRequestException("CONNECTION_PASSWORD_REQUIRED");
            }
            password = credentialEncryptor.decrypt(require(connectionId).getPasswordEnc());
        }
        TestResult result = probe(params, password);
        Map<String, Object> detail = result.success()
                ? Map.of("dbType", params.dbType().name(), "host", params.host())
                : Map.of("dbType", params.dbType().name(), "host", params.host(),
                        "reason", result.reason());
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.CONNECTION_TESTED, actor, connectionId, null,
                result.success() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE, detail));
        return result;
    }

    private TestResult probe(TestParams params, String password) {
        DbDialect dialect = DbDialects.of(params.dbType());
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        try {
            dataSource.setDriverClass(Class.forName(dialect.driverClassName())
                    .asSubclass(Driver.class));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver not found: "
                    + dialect.driverClassName(), e);
        }
        dataSource.setUrl(dialect.buildUrl(params.host(), params.port(),
                params.databaseName(), params.options()));
        dataSource.setUsername(params.username());
        dataSource.setPassword(password);
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(TEST_VALID_TIMEOUT_SECONDS)
                    ? new TestResult(true, null)
                    : new TestResult(false, "CONNECT_FAILED");
        } catch (SQLTimeoutException e) {
            return new TestResult(false, "TIMEOUT");
        } catch (SQLException e) {
            return new TestResult(false, classify(e));
        }
    }

    private String classify(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.startsWith("28")) {
            return "AUTH_FAILED";
        }
        return "CONNECT_FAILED";
    }

    private void applyMutableFields(DbConnection connection, ConnectionData data) {
        connection.setHost(data.host());
        connection.setPort(data.port());
        connection.setDatabaseName(data.databaseName());
        connection.setUsername(data.username());
        connection.setOptions(data.options());
        connection.setPoolMaxSize(data.poolMaxSize() != null ? data.poolMaxSize() : 5);
        connection.setPoolTimeoutMs(data.poolTimeoutMs() != null ? data.poolTimeoutMs() : 5000);
    }

    private void requireNameAvailable(String name, Long selfId) {
        connectionRepository.findByName(name).ifPresent(existing -> {
            if (selfId == null || !existing.getId().equals(selfId)) {
                throw new ConflictException("CONNECTION_NAME_DUPLICATE");
            }
        });
    }

    private DbConnection require(Long id) {
        return connectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CONNECTION_NOT_FOUND"));
    }
}

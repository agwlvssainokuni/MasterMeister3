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
package cherry.mastermeister.testinfra;

import static org.assertj.core.api.Assertions.assertThat;

import cherry.mastermeister.audit.AuditLogRepository;
import cherry.mastermeister.common.dialect.DbType;
import cherry.mastermeister.connection.ConnectionService;
import cherry.mastermeister.connection.CredentialEncryptor;
import cherry.mastermeister.connection.DbConnection;
import cherry.mastermeister.connection.DbConnectionRepository;
import cherry.mastermeister.connection.TargetDataSourceRegistry;
import cherry.mastermeister.metadata.MetaSchemaRepository;
import cherry.mastermeister.metadata.MetadataQueryService;
import cherry.mastermeister.metadata.SchemaImportService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * 実エンジンでの接続テスト・スキーマ取込の結合テスト基底(NFR-U4-07、Q5=A)。
 * ①の接続スモークテスト基盤を拡張し、各エンジン 2 系統:
 * (a) 接続テストの成功と誤資格情報の AUTH_FAILED 分類、
 * (b) サンプルスキーマ(テーブル・ビュー・PK)の取込結果検証。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class TargetRdbmsConnectionImportTestBase {

    private static final String ACTOR = "it@example.com";

    @Autowired
    private DbConnectionRepository connectionRepository;

    @Autowired
    private MetaSchemaRepository metaSchemaRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CredentialEncryptor credentialEncryptor;

    @Autowired
    private TargetDataSourceRegistry dataSourceRegistry;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private SchemaImportService schemaImportService;

    @Autowired
    private MetadataQueryService metadataQueryService;

    protected abstract JdbcDatabaseContainer<?> container();

    protected abstract DbType dbType();

    @BeforeEach
    void setUpSampleSchema() throws Exception {
        JdbcDatabaseContainer<?> target = container();
        try (Connection connection = DriverManager.getConnection(
                        target.getJdbcUrl(), target.getUsername(), target.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("DROP VIEW IF EXISTS v_sample_names");
            statement.execute("DROP TABLE IF EXISTS sample_products");
            statement.execute("""
                    CREATE TABLE sample_products (
                        id INT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        price INT,
                        PRIMARY KEY (id)
                    )
                    """);
            statement.execute("CREATE VIEW v_sample_names AS SELECT name FROM sample_products");
        }
    }

    @AfterEach
    void cleanup() {
        metaSchemaRepository.deleteAll();
        connectionRepository.findAll()
                .forEach(connection -> dataSourceRegistry.evict(connection.getId()));
        connectionRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    private ConnectionService.TestParams params(String password) {
        JdbcDatabaseContainer<?> target = container();
        return new ConnectionService.TestParams(
                null, dbType(), target.getHost(), target.getFirstMappedPort(),
                target.getDatabaseName(), target.getUsername(), password, null);
    }

    @Test
    void 接続テストが成功し誤パスワードはAUTH_FAILEDに分類される() {
        ConnectionService.TestResult ok = connectionService.test(
                params(container().getPassword()), ACTOR);
        assertThat(ok.success()).isTrue();

        ConnectionService.TestResult bad = connectionService.test(
                params("wrong-password-xxxxx"), ACTOR);
        assertThat(bad.success()).isFalse();
        assertThat(bad.reason()).isEqualTo("AUTH_FAILED");
    }

    @Test
    void スキーマ取込でテーブルとビューと主キーが取り込まれる() {
        JdbcDatabaseContainer<?> target = container();
        DbConnection connection = new DbConnection();
        connection.setName("real-" + dbType().name().toLowerCase());
        connection.setDbType(dbType());
        connection.setHost(target.getHost());
        connection.setPort(target.getFirstMappedPort());
        connection.setDatabaseName(target.getDatabaseName());
        connection.setUsername(target.getUsername());
        connection.setPasswordEnc(credentialEncryptor.encrypt(target.getPassword()));
        connection.setPoolMaxSize(2);
        connection.setPoolTimeoutMs(5000);
        connectionRepository.save(connection);

        SchemaImportService.ImportResult result =
                schemaImportService.importSchema(connection.getId(), ACTOR);
        assertThat(result.tables()).isGreaterThanOrEqualTo(2);

        MetadataQueryService.SchemaTree tree = metadataQueryService.getTree(connection.getId());
        MetadataQueryService.TableNode table = findTable(tree, "sample_products");
        assertThat(table.tableType().name()).isEqualTo("TABLE");
        MetadataQueryService.ColumnNode idColumn = table.columns().stream()
                .filter(column -> column.name().equalsIgnoreCase("id"))
                .findFirst().orElseThrow();
        assertThat(idColumn.primaryKeySeq()).isEqualTo(1);
        assertThat(idColumn.nullable()).isFalse();
        MetadataQueryService.ColumnNode priceColumn = table.columns().stream()
                .filter(column -> column.name().equalsIgnoreCase("price"))
                .findFirst().orElseThrow();
        assertThat(priceColumn.primaryKeySeq()).isNull();

        MetadataQueryService.TableNode view = findTable(tree, "v_sample_names");
        assertThat(view.tableType().name()).isEqualTo("VIEW");
        assertThat(view.columns()).anyMatch(
                column -> column.name().equalsIgnoreCase("name"));
    }

    private static MetadataQueryService.TableNode findTable(
            MetadataQueryService.SchemaTree tree, String name) {
        return tree.schemas().stream()
                .flatMap(schema -> schema.tables().stream())
                .filter(table -> table.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("table not found: " + name));
    }
}

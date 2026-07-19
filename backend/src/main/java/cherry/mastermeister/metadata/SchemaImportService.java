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
package cherry.mastermeister.metadata;

import cherry.mastermeister.audit.AuditEvent;
import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.AuditEvents;
import cherry.mastermeister.audit.AuditOutcome;
import cherry.mastermeister.common.dialect.DbDialect;
import cherry.mastermeister.common.dialect.DbDialects;
import cherry.mastermeister.common.event.PermissionCacheInvalidated;
import cherry.mastermeister.common.web.ApiException;
import cherry.mastermeister.common.web.NotFoundException;
import cherry.mastermeister.common.web.ServiceUnavailableException;
import cherry.mastermeister.connection.DbConnection;
import cherry.mastermeister.connection.DbConnectionRepository;
import cherry.mastermeister.connection.TargetDataSourceRegistry;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * スキーマ取込(US-012 — business-rules.md §2)。
 * 接続単位の全置換・単一トランザクション(失敗時は全ロールバックで取込前状態を維持)。
 * 完了時に実効権限キャッシュを無効化し、成功/失敗を監査記録する。
 * リトライはしない(NFR Design Q1=A)。プール枯渇は 503 TARGET_DB_BUSY(Q2=B)。
 */
@Service
public class SchemaImportService {

    private static final long RETRY_AFTER_SECONDS = 5;

    public record ImportResult(int schemas, int tables, int columns) {
    }

    private record TableData(String name, MetaTable.TableType type, String remarks,
            List<ColumnData> columns) {
    }

    private record ColumnData(String name, int ordinal, String dataType, Integer columnSize,
            Integer decimalDigits, boolean nullable, String defaultValue, String remarks,
            Integer primaryKeySeq) {
    }

    private final DbConnectionRepository connectionRepository;
    private final TargetDataSourceRegistry dataSourceRegistry;
    private final MetaSchemaRepository schemaRepository;
    private final MetaTableRepository tableRepository;
    private final MetaColumnRepository columnRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SchemaImportService(
            DbConnectionRepository connectionRepository,
            TargetDataSourceRegistry dataSourceRegistry,
            MetaSchemaRepository schemaRepository,
            MetaTableRepository tableRepository,
            MetaColumnRepository columnRepository,
            AuditEventPublisher auditEventPublisher,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.connectionRepository = connectionRepository;
        this.dataSourceRegistry = dataSourceRegistry;
        this.schemaRepository = schemaRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importSchema(Long connectionId, String actor) {
        DbConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("CONNECTION_NOT_FOUND"));
        DbDialect dialect = DbDialects.of(connection.getDbType());

        DataSource dataSource;
        try {
            dataSource = dataSourceRegistry.get(connectionId);
        } catch (ApiException e) {
            // CREDENTIAL_DECRYPT_FAILED 等はそのまま返す
            throw e;
        } catch (RuntimeException e) {
            // プール初期生成の失敗(接続不能等)は上流 DB 起因として 502
            auditFailure(connectionId, actor, "CONNECT_FAILED");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SCHEMA_IMPORT_FAILED");
        }

        Map<String, List<TableData>> schemas;
        try (Connection targetConnection = dataSource.getConnection()) {
            schemas = read(targetConnection, dialect, connection.getDatabaseName());
        } catch (SQLTransientConnectionException e) {
            auditFailure(connectionId, actor, "TARGET_DB_BUSY");
            throw new ServiceUnavailableException("TARGET_DB_BUSY", RETRY_AFTER_SECONDS);
        } catch (SQLException e) {
            auditFailure(connectionId, actor, "CONNECT_FAILED");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SCHEMA_IMPORT_FAILED");
        }

        // 全置換(単一トランザクション — 子テーブルは FK ON DELETE CASCADE)
        schemaRepository.deleteByConnectionId(connectionId);
        schemaRepository.flush();

        LocalDateTime importedAt = LocalDateTime.now(clock);
        int tableCount = 0;
        int columnCount = 0;
        for (Map.Entry<String, List<TableData>> schemaEntry : schemas.entrySet()) {
            MetaSchema schema = new MetaSchema();
            schema.setConnectionId(connectionId);
            schema.setName(schemaEntry.getKey());
            schema.setImportedAt(importedAt);
            schemaRepository.save(schema);
            for (TableData tableData : schemaEntry.getValue()) {
                MetaTable table = new MetaTable();
                table.setSchemaId(schema.getId());
                table.setName(tableData.name());
                table.setTableType(tableData.type());
                table.setRemarks(tableData.remarks());
                tableRepository.save(table);
                tableCount++;
                List<MetaColumn> columns = new ArrayList<>();
                for (ColumnData columnData : tableData.columns()) {
                    MetaColumn column = new MetaColumn();
                    column.setTableId(table.getId());
                    column.setName(columnData.name());
                    column.setOrdinal(columnData.ordinal());
                    column.setDataType(columnData.dataType());
                    column.setColumnSize(columnData.columnSize());
                    column.setDecimalDigits(columnData.decimalDigits());
                    column.setNullable(columnData.nullable());
                    column.setDefaultValue(columnData.defaultValue());
                    column.setRemarks(columnData.remarks());
                    column.setPrimaryKeySeq(columnData.primaryKeySeq());
                    columns.add(column);
                }
                columnRepository.saveAll(columns);
                columnCount += columns.size();
            }
        }

        eventPublisher.publishEvent(new PermissionCacheInvalidated());
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.SCHEMA_IMPORTED, actor, connectionId, null, AuditOutcome.SUCCESS,
                Map.of("schemas", schemas.size(), "tables", tableCount, "columns", columnCount)));
        return new ImportResult(schemas.size(), tableCount, columnCount);
    }

    private void auditFailure(Long connectionId, String actor, String reason) {
        auditEventPublisher.publish(new AuditEvent(
                AuditEvents.SCHEMA_IMPORTED, actor, connectionId, null, AuditOutcome.FAILURE,
                Map.of("reason", reason)));
    }

    private Map<String, List<TableData>> read(
            Connection targetConnection, DbDialect dialect, String databaseName)
            throws SQLException {
        DatabaseMetaData meta = targetConnection.getMetaData();
        Map<String, List<TableData>> result = new HashMap<>();
        for (String schemaName : dialect.listSchemaNames(meta, databaseName)) {
            String catalog = dialect.catalogFor(schemaName);
            String schemaPattern = dialect.schemaPatternFor(schemaName);
            List<TableData> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(catalog, schemaPattern, "%",
                    new String[] {"TABLE", "VIEW", "BASE TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableType = rs.getString("TABLE_TYPE");
                    MetaTable.TableType type = "VIEW".equalsIgnoreCase(tableType)
                            ? MetaTable.TableType.VIEW
                            : MetaTable.TableType.TABLE;
                    tables.add(new TableData(tableName, type, rs.getString("REMARKS"),
                            readColumns(meta, catalog, schemaPattern, tableName)));
                }
            }
            result.put(schemaName, tables);
        }
        return result;
    }

    private List<ColumnData> readColumns(
            DatabaseMetaData meta, String catalog, String schemaPattern, String tableName)
            throws SQLException {
        Map<String, Integer> primaryKeys = new HashMap<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schemaPattern, tableName)) {
            while (rs.next()) {
                primaryKeys.put(rs.getString("COLUMN_NAME"), rs.getInt("KEY_SEQ"));
            }
        }
        List<ColumnData> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schemaPattern, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                Integer columnSize = rs.getObject("COLUMN_SIZE") == null
                        ? null : rs.getInt("COLUMN_SIZE");
                Integer decimalDigits = rs.getObject("DECIMAL_DIGITS") == null
                        ? null : rs.getInt("DECIMAL_DIGITS");
                columns.add(new ColumnData(
                        columnName,
                        rs.getInt("ORDINAL_POSITION"),
                        rs.getString("TYPE_NAME"),
                        columnSize,
                        decimalDigits,
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        rs.getString("COLUMN_DEF"),
                        rs.getString("REMARKS"),
                        primaryKeys.get(columnName)));
            }
        }
        return columns;
    }
}

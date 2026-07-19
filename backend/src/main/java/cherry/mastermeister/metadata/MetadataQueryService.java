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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取込済み構造の参照(④の画面・⑤⑥の権限フィルタ適用側が使用)。
 * 権限フィルタは呼び出し側で適用する(Application Design)。
 */
@Service
public class MetadataQueryService {

    public record ColumnNode(
            String name, int ordinal, String dataType, Integer columnSize,
            Integer decimalDigits, boolean nullable, String defaultValue, String remarks,
            Integer primaryKeySeq) {
    }

    public record TableNode(
            String name, MetaTable.TableType tableType, String remarks,
            List<ColumnNode> columns) {
    }

    public record SchemaNode(String name, String remarks, List<TableNode> tables) {
    }

    public record SchemaTree(LocalDateTime importedAt, List<SchemaNode> schemas) {
    }

    private final MetaSchemaRepository schemaRepository;
    private final MetaTableRepository tableRepository;
    private final MetaColumnRepository columnRepository;

    public MetadataQueryService(
            MetaSchemaRepository schemaRepository,
            MetaTableRepository tableRepository,
            MetaColumnRepository columnRepository) {
        this.schemaRepository = schemaRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
    }

    /** 接続の取込済みツリー(未取込は importedAt = null・空リスト)。3 クエリで組み立てる。 */
    @Transactional(readOnly = true)
    public SchemaTree getTree(Long connectionId) {
        List<MetaSchema> schemas = schemaRepository.findByConnectionIdOrderByName(connectionId);
        if (schemas.isEmpty()) {
            return new SchemaTree(null, List.of());
        }
        List<Long> schemaIds = schemas.stream().map(MetaSchema::getId).toList();
        List<MetaTable> tables = tableRepository.findBySchemaIdInOrderByName(schemaIds);
        List<Long> tableIds = tables.stream().map(MetaTable::getId).toList();
        Map<Long, List<MetaColumn>> columnsByTable = tableIds.isEmpty()
                ? Map.of()
                : columnRepository.findByTableIdInOrderByOrdinal(tableIds).stream()
                        .collect(Collectors.groupingBy(MetaColumn::getTableId));
        Map<Long, List<MetaTable>> tablesBySchema = tables.stream()
                .collect(Collectors.groupingBy(MetaTable::getSchemaId));

        List<SchemaNode> schemaNodes = schemas.stream()
                .map(schema -> new SchemaNode(
                        schema.getName(), schema.getRemarks(),
                        tablesBySchema.getOrDefault(schema.getId(), List.of()).stream()
                                .map(table -> new TableNode(
                                        table.getName(), table.getTableType(), table.getRemarks(),
                                        columnsByTable.getOrDefault(table.getId(), List.of())
                                                .stream()
                                                .map(MetadataQueryService::toColumnNode)
                                                .toList()))
                                .toList()))
                .toList();
        return new SchemaTree(schemas.getFirst().getImportedAt(), schemaNodes);
    }

    /** 接続 ID → 取込日時(一覧画面の取込状況表示用)。 */
    @Transactional(readOnly = true)
    public Map<Long, LocalDateTime> importStatus() {
        return schemaRepository.findImportStatus().stream()
                .collect(Collectors.toMap(
                        MetaSchemaRepository.ImportStatusRow::getConnectionId,
                        MetaSchemaRepository.ImportStatusRow::getImportedAt));
    }

    private static ColumnNode toColumnNode(MetaColumn column) {
        return new ColumnNode(
                column.getName(), column.getOrdinal(), column.getDataType(),
                column.getColumnSize(), column.getDecimalDigits(), column.isNullable(),
                column.getDefaultValue(), column.getRemarks(), column.getPrimaryKeySeq());
    }
}

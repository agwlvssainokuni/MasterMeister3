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
package cherry.mastermeister.common.dialect;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * 対象 RDBMS の方言差の吸収(④: JDBC URL 組み立て・スキーマ概念。⑤⑥: SQL 組み立てに拡張予定)。
 * スキーマ概念の差: MySQL/MariaDB はカタログ = データベースがスキーマに相当し、
 * PostgreSQL/H2 はデータベース内のスキーマがそのまま対応する。
 */
public interface DbDialect {

    DbType dbType();

    String driverClassName();

    /** 接続パラメータから JDBC URL を組み立てる(URL 文字列の直接入力は受け付けない)。 */
    String buildUrl(String host, Integer port, String databaseName, String options);

    /** true: スキーマを JDBC メタデータのカタログとして扱う(MySQL / MariaDB)。 */
    boolean schemaIsCatalog();

    /** 取込対象のスキーマ名一覧(システムスキーマは除外)。 */
    List<String> listSchemaNames(DatabaseMetaData meta, String databaseName) throws SQLException;

    default String catalogFor(String schemaName) {
        return schemaIsCatalog() ? schemaName : null;
    }

    default String schemaPatternFor(String schemaName) {
        return schemaIsCatalog() ? null : schemaName;
    }
}

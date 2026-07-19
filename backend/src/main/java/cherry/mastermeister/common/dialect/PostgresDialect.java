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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** PostgreSQL。データベース内のスキーマを取込対象とする(システムスキーマ除外)。 */
public class PostgresDialect implements DbDialect {

    @Override
    public DbType dbType() {
        return DbType.POSTGRESQL;
    }

    @Override
    public String driverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String buildUrl(String host, Integer port, String databaseName, String options) {
        StringBuilder url = new StringBuilder("jdbc:postgresql://").append(host);
        if (port != null) {
            url.append(':').append(port);
        }
        url.append('/').append(databaseName);
        if (options != null && !options.isBlank()) {
            url.append('?').append(options);
        }
        return url.toString();
    }

    @Override
    public boolean schemaIsCatalog() {
        return false;
    }

    @Override
    public List<String> listSchemaNames(DatabaseMetaData meta, String databaseName)
            throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = meta.getSchemas()) {
            while (rs.next()) {
                String name = rs.getString("TABLE_SCHEM");
                if (name.startsWith("pg_") || name.equalsIgnoreCase("information_schema")) {
                    continue;
                }
                names.add(name);
            }
        }
        return names;
    }
}

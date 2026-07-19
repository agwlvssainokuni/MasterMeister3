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
import java.util.List;

/** MariaDB。スキーマ = カタログ(接続先データベースのみを取込対象とする)。 */
public class MariaDbDialect implements DbDialect {

    @Override
    public DbType dbType() {
        return DbType.MARIADB;
    }

    @Override
    public String driverClassName() {
        return "org.mariadb.jdbc.Driver";
    }

    @Override
    public String buildUrl(String host, Integer port, String databaseName, String options) {
        StringBuilder url = new StringBuilder("jdbc:mariadb://").append(host);
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
        return true;
    }

    @Override
    public List<String> listSchemaNames(DatabaseMetaData meta, String databaseName) {
        return List.of(databaseName);
    }
}

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

/**
 * H2。port 指定あり = TCP サーバモード(host:port/databaseName)、
 * port なし = host をそのまま H2 の接続指定として扱う(file:/path・mem:name 等)。
 * 追加オプションは H2 の形式(;KEY=VALUE)で連結する。
 */
public class H2Dialect implements DbDialect {

    @Override
    public DbType dbType() {
        return DbType.H2;
    }

    @Override
    public String driverClassName() {
        return "org.h2.Driver";
    }

    @Override
    public String buildUrl(String host, Integer port, String databaseName, String options) {
        StringBuilder url = new StringBuilder("jdbc:h2:");
        if (port != null) {
            url.append("tcp://").append(host).append(':').append(port).append('/')
                    .append(databaseName);
        } else {
            url.append(host);
        }
        if (options != null && !options.isBlank()) {
            url.append(';').append(options.replace('&', ';'));
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
                if (name.equalsIgnoreCase("information_schema")) {
                    continue;
                }
                names.add(name);
            }
        }
        return names;
    }
}

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

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** DbType → DbDialect の解決。 */
public final class DbDialects {

    private static final Map<DbType, DbDialect> DIALECTS = Stream.of(
                    new MySqlDialect(), new MariaDbDialect(), new PostgresDialect(), new H2Dialect())
            .collect(Collectors.toUnmodifiableMap(DbDialect::dbType, Function.identity()));

    public static DbDialect of(DbType dbType) {
        return DIALECTS.get(dbType);
    }

    private DbDialects() {
    }
}

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

import cherry.mastermeister.common.dialect.DbType;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MariaDB 11.8 の接続テスト・スキーマ取込テスト。Docker 不在時はスキップされる。 */
@Testcontainers(disabledWithoutDocker = true)
class MariaDbConnectionImportTest extends TargetRdbmsConnectionImportTestBase {

    @Container
    static final MariaDBContainer<?> CONTAINER = new MariaDBContainer<>("mariadb:11.8");

    @Override
    protected JdbcDatabaseContainer<?> container() {
        return CONTAINER;
    }

    @Override
    protected DbType dbType() {
        return DbType.MARIADB;
    }
}

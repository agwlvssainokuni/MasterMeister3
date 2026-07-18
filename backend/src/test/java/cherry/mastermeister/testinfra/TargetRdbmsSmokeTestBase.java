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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * 対象 RDBMS の方言別結合テストの共通基底。
 * 後続ユニット(④⑤⑥)は本基底の形式に倣い、同一テストを
 * MySQL / MariaDB / PostgreSQL の実エンジン(+ 内蔵 H2)で実行する。
 * ユニット①では接続スモークテストのみを提供する。
 */
public abstract class TargetRdbmsSmokeTestBase {

    /** 各方言のコンテナ(具象クラスが {@code @Container} で管理する)を返す。 */
    protected abstract JdbcDatabaseContainer<?> container();

    @Test
    void 接続してクエリを実行できる() throws Exception {
        JdbcDatabaseContainer<?> target = container();
        try (Connection connection = DriverManager.getConnection(
                        target.getJdbcUrl(), target.getUsername(), target.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }
}

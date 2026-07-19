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
package cherry.mastermeister.connection;

import cherry.mastermeister.common.dialect.DbDialect;
import cherry.mastermeister.common.dialect.DbDialects;
import cherry.mastermeister.common.web.NotFoundException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 接続 ID → プール済み DataSource(NFR-U4-02)。遅延生成 + キャッシュ。
 * 接続設定の更新・削除時は {@link #evict(Long)} でプールを確実にクローズする。
 * 取込(④)・データアクセス(⑤⑥)はここから取得する。プール外の単発接続は接続テストのみ。
 */
@Component
public class TargetDataSourceRegistry {

    private final ConcurrentHashMap<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    private final DbConnectionRepository connectionRepository;
    private final CredentialEncryptor credentialEncryptor;

    public TargetDataSourceRegistry(
            DbConnectionRepository connectionRepository,
            CredentialEncryptor credentialEncryptor) {
        this.connectionRepository = connectionRepository;
        this.credentialEncryptor = credentialEncryptor;
    }

    public DataSource get(Long connectionId) {
        return pools.computeIfAbsent(connectionId, id -> create(load(id)));
    }

    public void evict(Long connectionId) {
        HikariDataSource pool = pools.remove(connectionId);
        if (pool != null) {
            pool.close();
        }
    }

    @PreDestroy
    void shutdown() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private DbConnection load(Long connectionId) {
        return connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("CONNECTION_NOT_FOUND"));
    }

    private HikariDataSource create(DbConnection connection) {
        DbDialect dialect = DbDialects.of(connection.getDbType());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dialect.buildUrl(connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getOptions()));
        config.setDriverClassName(dialect.driverClassName());
        config.setUsername(connection.getUsername());
        config.setPassword(credentialEncryptor.decrypt(connection.getPasswordEnc()));
        config.setMaximumPoolSize(connection.getPoolMaxSize());
        config.setConnectionTimeout(connection.getPoolTimeoutMs());
        config.setPoolName("mm-target-" + connection.getId());
        return new HikariDataSource(config);
    }
}

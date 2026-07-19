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

import cherry.mastermeister.common.dialect.DbType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接続管理 API(ADMIN — US-010/011)。応答にパスワードは一切含めない。
 */
@RestController
@RequestMapping("/api/admin/connections")
public class ConnectionController {

    public record ConnectionCreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull DbType dbType,
            @NotBlank @Size(max = 255) String host,
            @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 255) String databaseName,
            @NotBlank @Size(max = 255) String username,
            @NotBlank @Size(max = 255) String password,
            @Size(max = 1000) String options,
            @Min(1) @Max(50) Integer poolMaxSize,
            @Min(100) @Max(60000) Integer poolTimeoutMs) {
    }

    public record ConnectionUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            DbType dbType,
            @NotBlank @Size(max = 255) String host,
            @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 255) String databaseName,
            @NotBlank @Size(max = 255) String username,
            @Size(max = 255) String password,
            @Size(max = 1000) String options,
            @Min(1) @Max(50) Integer poolMaxSize,
            @Min(100) @Max(60000) Integer poolTimeoutMs) {
    }

    public record ConnectionResponse(
            Long id, String name, DbType dbType, String host, Integer port,
            String databaseName, String username, String options,
            int poolMaxSize, int poolTimeoutMs) {

        static ConnectionResponse of(DbConnection connection) {
            return new ConnectionResponse(
                    connection.getId(), connection.getName(), connection.getDbType(),
                    connection.getHost(), connection.getPort(), connection.getDatabaseName(),
                    connection.getUsername(), connection.getOptions(),
                    connection.getPoolMaxSize(), connection.getPoolTimeoutMs());
        }
    }

    public record ConnectionTestRequest(
            Long id,
            @NotNull DbType dbType,
            @NotBlank @Size(max = 255) String host,
            @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 255) String databaseName,
            @NotBlank @Size(max = 255) String username,
            @Size(max = 255) String password,
            @Size(max = 1000) String options) {
    }

    public record ConnectionTestResponse(boolean success, String reason) {
    }

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<ConnectionResponse> list() {
        return connectionService.list().stream().map(ConnectionResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ConnectionResponse get(@PathVariable Long id) {
        return ConnectionResponse.of(connectionService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionResponse create(
            @Valid @RequestBody ConnectionCreateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        return ConnectionResponse.of(connectionService.create(
                new ConnectionService.ConnectionData(
                        body.name(), body.dbType(), body.host(), body.port(),
                        body.databaseName(), body.username(), body.password(),
                        body.options(), body.poolMaxSize(), body.poolTimeoutMs()),
                actor(jwt)));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @PathVariable Long id,
            @Valid @RequestBody ConnectionUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        connectionService.update(id,
                new ConnectionService.ConnectionData(
                        body.name(), body.dbType(), body.host(), body.port(),
                        body.databaseName(), body.username(), body.password(),
                        body.options(), body.poolMaxSize(), body.poolTimeoutMs()),
                actor(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        connectionService.delete(id, actor(jwt));
    }

    @PostMapping("/test")
    public ConnectionTestResponse test(
            @Valid @RequestBody ConnectionTestRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        ConnectionService.TestResult result = connectionService.test(
                new ConnectionService.TestParams(
                        body.id(), body.dbType(), body.host(), body.port(),
                        body.databaseName(), body.username(), body.password(), body.options()),
                actor(jwt));
        return new ConnectionTestResponse(result.success(), result.reason());
    }

    private static String actor(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}

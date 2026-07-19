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
package cherry.mastermeister.permission;

import cherry.mastermeister.common.web.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 権限設定 API(ADMIN — US-013/014/016/017)。
 * エントリの設定(明示 NONE 含む)と削除(未設定に戻す)は別操作(D-18)。
 * YAML は 1 MB 上限をアプリケーション内で検証する(NFR-U4-04)。
 */
@RestController
@RequestMapping("/api/admin/connections/{connectionId}/permissions")
public class PermissionController {

    private static final int YAML_MAX_BYTES = 1024 * 1024;

    /** 主権限は permission を、補助権限は auxType + granted を指定する(排他)。 */
    public record PermissionEntryRequest(
            @NotNull PrincipalType principalType,
            @NotNull Long principalId,
            @NotBlank @Size(max = 255) String schema,
            @Size(max = 255) String table,
            @Size(max = 255) String column,
            PermissionLevel permission,
            AuxType auxType,
            Boolean granted) {
    }

    public record PermissionEntryRemoveRequest(
            @NotNull PrincipalType principalType,
            @NotNull Long principalId,
            @NotBlank @Size(max = 255) String schema,
            @Size(max = 255) String table,
            @Size(max = 255) String column,
            AuxType auxType) {
    }

    public record MainEntryResponse(
            String schema, String table, String column, PermissionLevel permission,
            boolean orphan) {
    }

    public record AuxEntryResponse(
            String schema, String table, AuxType auxType, boolean granted, boolean orphan) {
    }

    public record PrincipalEntriesResponse(
            List<MainEntryResponse> main, List<AuxEntryResponse> aux) {
    }

    public record YamlImportResponse(int entries) {
    }

    private final PermissionService permissionService;
    private final PermissionYamlService yamlService;

    public PermissionController(
            PermissionService permissionService, PermissionYamlService yamlService) {
        this.permissionService = permissionService;
        this.yamlService = yamlService;
    }

    @GetMapping
    public PrincipalEntriesResponse entries(
            @PathVariable Long connectionId,
            @RequestParam PrincipalType principalType,
            @RequestParam Long principalId) {
        PermissionService.PrincipalEntries entries =
                permissionService.entriesFor(connectionId, principalType, principalId);
        return new PrincipalEntriesResponse(
                entries.main().stream()
                        .map(view -> new MainEntryResponse(view.schema(), view.table(),
                                view.column(), view.permission(), view.orphan()))
                        .toList(),
                entries.aux().stream()
                        .map(view -> new AuxEntryResponse(view.schema(), view.table(),
                                view.auxType(), view.granted(), view.orphan()))
                        .toList());
    }

    @PutMapping("/entry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setEntry(
            @PathVariable Long connectionId,
            @Valid @RequestBody PermissionEntryRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        PermissionService.Scope scope =
                new PermissionService.Scope(body.schema(), body.table(), body.column());
        if (body.auxType() != null) {
            if (body.granted() == null || body.permission() != null) {
                throw new BadRequestException("PERMISSION_INVALID_SCOPE");
            }
            permissionService.setAuxEntry(connectionId, body.principalType(),
                    body.principalId(), scope, body.auxType(), body.granted(), actor(jwt));
        } else {
            if (body.permission() == null || body.granted() != null) {
                throw new BadRequestException("PERMISSION_INVALID_SCOPE");
            }
            permissionService.setMainEntry(connectionId, body.principalType(),
                    body.principalId(), scope, body.permission(), actor(jwt));
        }
    }

    @DeleteMapping("/entry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEntry(
            @PathVariable Long connectionId,
            @Valid @RequestBody PermissionEntryRemoveRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        PermissionService.Scope scope =
                new PermissionService.Scope(body.schema(), body.table(), body.column());
        if (body.auxType() != null) {
            permissionService.removeAuxEntry(connectionId, body.principalType(),
                    body.principalId(), scope, body.auxType(), actor(jwt));
        } else {
            permissionService.removeMainEntry(connectionId, body.principalType(),
                    body.principalId(), scope, actor(jwt));
        }
    }

    @GetMapping("/yaml")
    public ResponseEntity<String> exportYaml(
            @PathVariable Long connectionId, @AuthenticationPrincipal Jwt jwt) {
        String yaml = yamlService.export(connectionId, actor(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/yaml"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"permissions-" + connectionId + ".yaml\"")
                .body(yaml);
    }

    @PutMapping(value = "/yaml", consumes = {"application/yaml", "text/yaml", "text/plain"})
    public YamlImportResponse importYaml(
            @PathVariable Long connectionId,
            @RequestBody String yaml,
            @AuthenticationPrincipal Jwt jwt) {
        if (yaml.getBytes(StandardCharsets.UTF_8).length > YAML_MAX_BYTES) {
            throw new BadRequestException("YAML_TOO_LARGE");
        }
        return new YamlImportResponse(yamlService.importReplace(connectionId, yaml, actor(jwt)));
    }

    /** YAML 検証エラー: 全体拒否 + 行位置・理由コードの一覧(US-017)。 */
    @ExceptionHandler(PermissionYamlService.YamlValidationException.class)
    public ResponseEntity<ProblemDetail> handleYamlValidation(
            PermissionYamlService.YamlValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setProperty("code", e.code());
        problem.setProperty("errors", e.errors().stream()
                .map(error -> Map.of("path", error.path(), "reason", error.reason()))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    private static String actor(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}

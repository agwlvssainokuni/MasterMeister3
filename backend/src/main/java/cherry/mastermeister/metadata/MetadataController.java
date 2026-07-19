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
package cherry.mastermeister.metadata;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * スキーマ取込・メタデータ参照 API(ADMIN — US-012)。
 */
@RestController
@RequestMapping("/api/admin")
public class MetadataController {

    public record SchemaImportResponse(int schemas, int tables, int columns) {
    }

    public record ImportStatusResponse(Long connectionId, LocalDateTime importedAt) {
    }

    private final SchemaImportService schemaImportService;
    private final MetadataQueryService metadataQueryService;

    public MetadataController(
            SchemaImportService schemaImportService,
            MetadataQueryService metadataQueryService) {
        this.schemaImportService = schemaImportService;
        this.metadataQueryService = metadataQueryService;
    }

    @PostMapping("/connections/{id}/schema/import")
    public SchemaImportResponse importSchema(
            @PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        SchemaImportService.ImportResult result =
                schemaImportService.importSchema(id, jwt.getClaimAsString("email"));
        return new SchemaImportResponse(result.schemas(), result.tables(), result.columns());
    }

    @GetMapping("/connections/{id}/schema")
    public MetadataQueryService.SchemaTree getSchema(@PathVariable Long id) {
        return metadataQueryService.getTree(id);
    }

    @GetMapping("/metadata/import-status")
    public List<ImportStatusResponse> importStatus() {
        return metadataQueryService.importStatus().entrySet().stream()
                .map(entry -> new ImportStatusResponse(entry.getKey(), entry.getValue()))
                .toList();
    }
}

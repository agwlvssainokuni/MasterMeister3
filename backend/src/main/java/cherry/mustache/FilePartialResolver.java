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
package cherry.mustache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 指定した基準ディレクトリ配下から、パーシャル名+{@code .mustache}拡張子でファイルを探索する{@link PartialResolver}実装。
 * 基準ディレクトリ外を指すパーシャル名は未解決（{@code null}）として扱う（NFR-SEC-1、パストラバーサル対策）。
 */
public final class FilePartialResolver implements PartialResolver {

    private static final Logger log = LoggerFactory.getLogger(FilePartialResolver.class);

    private final Path baseDir;

    /**
     * @param baseDir パーシャルファイルを探索する基準ディレクトリ
     */
    public FilePartialResolver(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
    }

    @Override
    public String resolve(String partialName) {
        Path candidate = baseDir.resolve(partialName + ".mustache").normalize();
        if (!candidate.startsWith(baseDir)) {
            log.warn("Rejected partial name resolving outside baseDir: {}", partialName);
            return null;
        }
        if (!Files.isRegularFile(candidate)) {
            return null;
        }
        try {
            return Files.readString(candidate, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Failed to read partial file: {}", partialName, e);
            throw new MustacheRenderException("Failed to read partial file: " + partialName, partialName, e);
        }
    }
}

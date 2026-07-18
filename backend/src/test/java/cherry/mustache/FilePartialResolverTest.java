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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FilePartialResolverTest {

    @Test
    void resolvesExistingPartialFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("greeting.mustache"), "Hello, {{name}}!");
        FilePartialResolver resolver = new FilePartialResolver(tempDir);
        assertEquals("Hello, {{name}}!", resolver.resolve("greeting"));
    }

    @Test
    void missingFileResolvesToNull(@TempDir Path tempDir) {
        FilePartialResolver resolver = new FilePartialResolver(tempDir);
        assertNull(resolver.resolve("missing"));
    }

    @Test
    void pathTraversalOutsideBaseDirResolvesToNull(@TempDir Path tempDir) throws IOException {
        Path outside = tempDir.getParent().resolve("outside-" + System.nanoTime() + ".mustache");
        Files.writeString(outside, "secret");
        try {
            FilePartialResolver resolver = new FilePartialResolver(tempDir);
            String traversalName = "../" + outside.getFileName().toString().replace(".mustache", "");
            assertNull(resolver.resolve(traversalName));
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}

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
package cherry.mustache.render;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextTest {

    @Test
    void resolvesFromOwnData() {
        Context context = new Context(Map.of("a", "1"), null);
        assertEquals("1", context.resolve("a"));
    }

    @Test
    void resolvesFromParentWhenNotFoundInOwnScope() {
        Context parent = new Context(Map.of("a", "parent-value"), null);
        Context child = parent.push(Map.of("b", "child-value"));
        assertEquals("child-value", child.resolve("b"));
        assertEquals("parent-value", child.resolve("a"));
    }

    @Test
    void childScopeShadowsParent() {
        Context parent = new Context(Map.of("a", "parent-value"), null);
        Context child = parent.push(Map.of("a", "child-value"));
        assertEquals("child-value", child.resolve("a"));
    }

    @Test
    void dottedKeyDoesNotWalkParentForNonFirstSegment() {
        Context parent = new Context(Map.of("b", "parent-b"), null);
        Context child = parent.push(Map.of("a", Map.of()));
        assertNull(child.resolve("a.b"));
    }

    @Test
    void implicitIteratorReturnsCurrentData() {
        Context context = new Context("current-value", null);
        assertEquals("current-value", context.resolve("."));
    }

    @Test
    void missingKeyResolvesToNull() {
        Context context = new Context(Map.of(), null);
        assertNull(context.resolve("missing"));
    }
}

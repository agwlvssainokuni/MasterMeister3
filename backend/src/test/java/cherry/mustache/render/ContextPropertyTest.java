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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BR-6（ドット表記の解決）に関するProperty-Based Test（PBT-05: Oracle）。
 * 深さ2のドット表記の解決結果が、手動でMap.getを2回辿った結果と常に一致することを検証する。
 */
class ContextPropertyTest {

    @Property
    void dottedKeyResolutionMatchesManualNestedMapLookup(
            @ForAll @AlphaChars @StringLength(min = 1, max = 8) String a,
            @ForAll @AlphaChars @StringLength(min = 1, max = 8) String b,
            @ForAll @StringLength(max = 20) String value) {
        Map<String, Object> inner = Map.of(b, value);
        Map<String, Object> outer = Map.of(a, inner);
        Context context = new Context(outer, null);

        Object manual = inner.get(b);
        assertEquals(manual, context.resolve(a + "." + b));
    }
}

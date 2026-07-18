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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * BR-1（HTMLエスケープ）に関するProperty-Based Test（PBT-03: Invariant）。
 * functional-design/business-logic-model.md 4節「Testable Properties」参照。
 * 本番の{@code HtmlEscaper}とは独立に実装した参照エスケーパーをOracleとして使用する。
 */
class EscapingPropertyTest {

    @Property
    void escapedVariableMatchesReferenceEscaper(@ForAll @StringLength(max = 50) String value) {
        Template t = Mustache.compile("{{value}}");
        assertEquals(referenceEscape(value), t.render(Map.of("value", value)));
    }

    @Property
    void escapedOutputNeverContainsRawReservedCharacters(@ForAll @StringLength(max = 50) String value) {
        Template t = Mustache.compile("{{value}}");
        String rendered = t.render(Map.of("value", value));
        assertFalse(rendered.contains("<"));
        assertFalse(rendered.contains(">"));
        assertFalse(rendered.contains("\""));
    }

    @Property
    void sectionOverListRendersEachElementInOrder(@ForAll List<@StringLength(max = 20) String> items) {
        Template t = Mustache.compile("{{#items}}[{{.}}]{{/items}}");
        StringBuilder expected = new StringBuilder();
        for (String item : items) {
            expected.append('[').append(referenceEscape(item)).append(']');
        }
        assertEquals(expected.toString(), t.render(Map.of("items", items)));
    }

    private static String referenceEscape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}

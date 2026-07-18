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
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BR-9（パーシャル循環参照検出の裏側にある非循環参照の独立性）、
 * BR-11（デリミタ変更のスコープ）に関するProperty-Based Test（PBT-05: Oracle）。
 * functional-design/business-logic-model.md 4節「Testable Properties」参照。
 */
class TemplateEquivalencePropertyTest {

    @Property
    void customDelimiterProducesSameResultAsDefaultDelimiter(@ForAll @StringLength(max = 20) String value) {
        Template defaultTemplate = Mustache.compile("Hello, {{value}}!");
        Template customTemplate = Mustache.compile("{{=<% %>=}}Hello, <%value%>!");
        Map<String, Object> data = Map.of("value", value);
        assertEquals(defaultTemplate.render(data), customTemplate.render(data));
    }

    @Property
    void nonCircularPartialReferencesAreIndependentAndDeterministic(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String name) {
        MapPartialResolver resolver = new MapPartialResolver(Map.of("greet", "Hi, {{name}}!"));
        Template t = Mustache.compile("{{>greet}} {{>greet}}", resolver);
        Map<String, Object> data = Map.of("name", name);

        String result = t.render(data);
        String single = "Hi, " + name + "!";
        assertEquals(single + " " + single, result);
        assertEquals(result, t.render(data));
    }
}

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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit1(core) Functional Design の業務ルール（BR-1〜BR-11）を検証する例示ベーステスト。
 */
class TemplateRenderingTest {

    @Test
    void escapesHtmlSpecialCharacters() {
        Template t = Mustache.compile("{{value}}");
        assertEquals("&amp;&lt;&gt;&quot;", t.render(Map.of("value", "&<>\"")));
    }

    @Test
    void unescapedVariableDoesNotEscape() {
        Template t = Mustache.compile("{{{value}}}|{{&value2}}");
        assertEquals("<b>|<i>", t.render(Map.of("value", "<b>", "value2", "<i>")));
    }

    @Test
    void emptyStringIsTruthy() {
        Template t = Mustache.compile("{{#value}}shown{{/value}}");
        assertEquals("shown", t.render(Map.of("value", "")));
    }

    @Test
    void zeroIsTruthy() {
        Template t = Mustache.compile("{{#value}}shown{{/value}}");
        assertEquals("shown", t.render(Map.of("value", 0)));
    }

    @Test
    void emptyListIsFalsy() {
        Template t = Mustache.compile("{{#value}}shown{{/value}}");
        assertEquals("", t.render(Map.of("value", List.of())));
    }

    @Test
    void nonEmptyListIteratesSection() {
        Template t = Mustache.compile("{{#items}}[{{.}}]{{/items}}");
        assertEquals("[a][b]", t.render(Map.of("items", List.of("a", "b"))));
    }

    @Test
    void falseIsFalsyForInvertedSection() {
        Template t = Mustache.compile("{{^value}}shown{{/value}}");
        assertEquals("shown", t.render(Map.of("value", false)));
    }

    @Test
    void nullIsFalsyForInvertedSection() {
        Template t = Mustache.compile("{{^value}}shown{{/value}}");
        assertEquals("shown", t.render(Collections.singletonMap("value", null)));
    }

    @Test
    void dottedKeyResolvesNestedMaps() {
        Template t = Mustache.compile("{{a.b.c}}");
        assertEquals("deep", t.render(Map.of("a", Map.of("b", Map.of("c", "deep")))));
    }

    @Test
    void brokenChainResolvesToEmpty() {
        Template t = Mustache.compile("[{{a.b.c}}]");
        assertEquals("[]", t.render(Map.of("a", Map.of())));
    }

    @Test
    void pojoGetterTakesPriorityOverField() {
        class Sample {
            public String field = "field-value";

            public String getField() {
                return "getter-value";
            }
        }
        Template t = Mustache.compile("{{field}}");
        assertEquals("getter-value", t.render(new Sample()));
    }

    @Test
    void recordAccessorIsResolved() {
        record Person(String name) {
        }
        Template t = Mustache.compile("{{name}}");
        assertEquals("Alice", t.render(new Person("Alice")));
    }

    @Test
    void pojoAccessorExceptionIsWrappedAsRenderException() {
        class Boom {
            public String getValue() {
                throw new IllegalStateException("boom");
            }
        }
        Template t = Mustache.compile("{{value}}");
        MustacheRenderException ex = assertThrows(MustacheRenderException.class, () -> t.render(new Boom()));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void commentProducesNoOutput() {
        Template t = Mustache.compile("a{{! comment }}b");
        assertEquals("ab", t.render(Map.of()));
    }

    @Test
    void standaloneCommentLineIsFullyRemoved() {
        Template t = Mustache.compile("Begin\n{{! comment }}\nEnd\n");
        assertEquals("Begin\nEnd\n", t.render(Map.of()));
    }

    @Test
    void partialIndentIsReapplied() {
        Template t = Mustache.compile("Begin\n  {{>p}}\nEnd\n",
                new MapPartialResolver(Map.of("p", "line1\nline2\n")));
        assertEquals("Begin\n  line1\n  line2\nEnd\n", t.render(Map.of()));
    }

    @Test
    void missingPartialRendersNothing() {
        Template t = Mustache.compile("[{{>missing}}]", new MapPartialResolver(Map.of()));
        assertEquals("[]", t.render(Map.of()));
    }

    @Test
    void circularPartialReferenceThrowsRenderException() {
        MapPartialResolver resolver = new MapPartialResolver(Map.of("a", "{{>b}}", "b", "{{>a}}"));
        Template t = Mustache.compile("{{>a}}", resolver);
        assertThrows(MustacheRenderException.class, () -> t.render(Map.of()));
    }

    @Test
    void delimiterChangeDoesNotLeakIntoPartial() {
        Template t = Mustache.compile("{{=<% %>=}}<%>p%>",
                new MapPartialResolver(Map.of("p", "{{value}}")));
        assertEquals("OK", t.render(Map.of("value", "OK")));
    }

    @Test
    void variableLambdaOutputIsReparsedAndInterpolated() {
        // 公式spec ~lambdas.yml「Interpolation - Expansion」相当
        Lambda lambda = text -> "{{inner}}";
        Template t = Mustache.compile("{{lambda}}");
        assertEquals("world", t.render(Map.of("lambda", lambda, "inner", "world")));
    }

    @Test
    void variableLambdaEscapingFollowsOuterTagType() {
        // 公式spec ~lambdas.yml「Escaping」相当
        Lambda lambda = text -> ">";
        Template t = Mustache.compile("<{{lambda}}{{{lambda}}}>");
        assertEquals("<&gt;>>", t.render(Map.of("lambda", lambda)));
    }

    @Test
    void sectionLambdaReceivesRawTextAndIsNotEscaped() {
        Lambda wrap = text -> "<b>" + text + "</b>";
        Template t = Mustache.compile("{{#wrap}}hi {{name}}{{/wrap}}");
        assertEquals("<b>hi X</b>", t.render(Map.of("wrap", wrap, "name", "X")));
    }

    @Test
    void invertedSectionLambdaAlwaysRendersNothing() {
        Lambda lambda = text -> "shown";
        Template t = Mustache.compile("{{^value}}not shown{{/value}}");
        assertEquals("", t.render(Map.of("value", lambda)));
    }
}

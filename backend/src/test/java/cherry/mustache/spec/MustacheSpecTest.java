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
package cherry.mustache.spec;

import cherry.mustache.Lambda;
import cherry.mustache.MapPartialResolver;
import cherry.mustache.Mustache;
import cherry.mustache.Template;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 公式Mustache specテストスイート（BR-2のスコープ: comments/delimiters/interpolation/inverted/partials/sections/~lambdas）を
 * JUnit5の動的テスト（{@code @TestFactory}）として実行する。Testable Properties「Oracle」（PBT-05）に相当する。
 * {@code ~lambdas.yml}のRuby等のコード定義（{@code !code}タグ）は実行できないため、テスト名をキーとして
 * 同等の挙動を持つJava {@link Lambda}実装に手動でマッピングする。
 */
class MustacheSpecTest {

    private static final Object CODE_MARKER = new Object();

    @TestFactory
    Collection<DynamicTest> comments() {
        return loadSpecTests("comments.yml", Map.of());
    }

    @TestFactory
    Collection<DynamicTest> delimiters() {
        return loadSpecTests("delimiters.yml", Map.of());
    }

    @TestFactory
    Collection<DynamicTest> interpolation() {
        return loadSpecTests("interpolation.yml", Map.of());
    }

    @TestFactory
    Collection<DynamicTest> inverted() {
        return loadSpecTests("inverted.yml", Map.of());
    }

    @TestFactory
    Collection<DynamicTest> partials() {
        return loadSpecTests("partials.yml", Map.of());
    }

    @TestFactory
    Collection<DynamicTest> sections() {
        return loadSpecTests("sections.yml", Map.of());
    }

    @TestFactory
    Collection<DynamicTest> lambdas() {
        return loadSpecTests("~lambdas.yml", lambdaImplementations());
    }

    /**
     * {@code ~lambdas.yml}の各テストケース名 → Ruby定義と同等のJava {@link Lambda}実装の対応表。
     */
    private static Map<String, Lambda> lambdaImplementations() {
        AtomicInteger interpolationCallCount = new AtomicInteger(0);
        Map<String, Lambda> impls = new LinkedHashMap<>();
        impls.put("Interpolation", text -> "world");
        impls.put("Interpolation - Expansion", text -> "{{planet}}");
        impls.put("Interpolation - Alternate Delimiters", text -> "|planet| => {{planet}}");
        impls.put("Interpolation - Multiple Calls", text -> String.valueOf(interpolationCallCount.incrementAndGet()));
        impls.put("Escaping", text -> ">");
        impls.put("Section", text -> "{{x}}".equals(text) ? "yes" : "no");
        impls.put("Section - Expansion", text -> text + "{{planet}}" + text);
        impls.put("Section - Alternate Delimiters", text -> text + "{{planet}} => |planet|" + text);
        impls.put("Section - Multiple Calls", text -> "__" + text + "__");
        impls.put("Inverted Section", text -> "false");
        return impls;
    }

    @SuppressWarnings("unchecked")
    private static List<DynamicTest> loadSpecTests(String fileName, Map<String, Lambda> lambdaOverrides) {
        Yaml yaml = new Yaml(codeAwareConstructor());
        try (InputStream in = MustacheSpecTest.class.getResourceAsStream("/spec/" + fileName)) {
            assertNotNull(in, "spec resource not found: " + fileName);
            Map<String, Object> root = yaml.load(in);
            List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("tests");
            List<DynamicTest> result = new ArrayList<>();
            for (Map<String, Object> testCase : cases) {
                String name = (String) testCase.get("name");
                result.add(DynamicTest.dynamicTest(fileName + " / " + name, () -> runCase(testCase, lambdaOverrides)));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void runCase(Map<String, Object> testCase, Map<String, Lambda> lambdaOverrides) {
        String name = (String) testCase.get("name");
        String template = (String) testCase.get("template");
        String expected = (String) testCase.get("expected");
        Object rawData = testCase.getOrDefault("data", Map.of());

        // 「Implicit Iterators」系のテストではルートcontextがMapではなくスカラー/リスト直値になる
        Object data;
        if (rawData instanceof Map<?, ?> rawMap) {
            Map<String, Object> mapData = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                mapData.put((String) e.getKey(), e.getValue());
            }
            if (CODE_MARKER.equals(mapData.get("lambda"))) {
                Lambda lambda = lambdaOverrides.get(name);
                assertNotNull(lambda, "No Java Lambda mapping registered for spec test: " + name);
                mapData.put("lambda", lambda);
            }
            data = mapData;
        } else {
            data = rawData;
        }

        Map<String, String> partials = new LinkedHashMap<>();
        Object partialsRaw = testCase.get("partials");
        if (partialsRaw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                partials.put((String) e.getKey(), (String) e.getValue());
            }
        }

        Template t = Mustache.compile(template, new MapPartialResolver(partials));
        String actual = t.render(data);
        assertEquals(expected, actual, "spec case: " + name);
    }

    /**
     * {@code !code}タグ（Ruby等のコード定義）を、実際には構築せずマーカーオブジェクトに置き換えるConstructor。
     */
    private static Constructor codeAwareConstructor() {
        LoaderOptions options = new LoaderOptions();
        return new Constructor(options) {
            {
                this.yamlConstructors.put(new Tag("!code"), new AbstractConstruct() {
                    @Override
                    public Object construct(Node node) {
                        return CODE_MARKER;
                    }
                });
            }
        };
    }
}

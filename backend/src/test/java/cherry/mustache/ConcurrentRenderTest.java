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
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NFR-REL-1（同一{@link Template}インスタンスへの並行{@code render()}呼び出しの一貫性）を検証する。
 * {@link cherry.mustache.render.Context}のイミュータブル設計（BR-10）が、共有された単一の
 * コンパイル済み{@link Template}インスタンスに対する並行アクセス下でも安全であることを確認する。
 */
class ConcurrentRenderTest {

    @Test
    @Timeout(30)
    void sameTemplateInstanceRendersCorrectlyUnderConcurrentAccess() throws Exception {
        Template template = Mustache.compile("Hello, {{name}}! You have {{#items}}[{{.}}]{{/items}} items.");
        int threadCount = 16;
        int iterationsPerThread = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Void>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Void>>mapToObj(threadIndex -> () -> {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String name = "user-" + threadIndex + "-" + i;
                            Map<String, Object> data = Map.of(
                                    "name", name,
                                    "items", List.of("a" + threadIndex, "b" + threadIndex));
                            String expected = "Hello, " + name + "! You have [a" + threadIndex + "][b" + threadIndex + "] items.";
                            assertEquals(expected, template.render(data));
                        }
                        return null;
                    })
                    .collect(Collectors.toList());

            List<Future<Void>> futures = tasks.stream().map(executor::submit).collect(Collectors.toList());
            for (Future<Void> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }
    }
}

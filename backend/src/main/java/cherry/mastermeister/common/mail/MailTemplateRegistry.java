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
package cherry.mastermeister.common.mail;

import cherry.mustache.MapPartialResolver;
import cherry.mustache.Mustache;
import cherry.mustache.Template;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * メールテンプレートのコンパイル済みキャッシュ(NFR-U3-05)。
 * 起動時に classpath:mail/*.mustache.html を全件 cherry.mustache でコンパイルし、
 * 1 件でも parse 失敗なら起動失敗(fail-fast — 壊れた HTML を送らない)。
 * ファイル名規約: {templateId}_{lang}.mustache.html。言語が見つからなければ en へフォールバック。
 */
@Component
public class MailTemplateRegistry {

    private static final String LOCATION_PATTERN = "classpath*:mail/*.mustache.html";
    private static final String SUFFIX = ".mustache.html";
    private static final String FALLBACK_LANGUAGE = "en";

    private static final Logger logger = LoggerFactory.getLogger(MailTemplateRegistry.class);

    private final Map<String, Template> templates;

    public MailTemplateRegistry() {
        this(LOCATION_PATTERN);
    }

    MailTemplateRegistry(String locationPattern) {
        this.templates = compileAll(locationPattern);
    }

    private static Map<String, Template> compileAll(String locationPattern) {
        Map<String, Template> compiled = new HashMap<>();
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver().getResources(locationPattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(SUFFIX)) {
                    continue;
                }
                String key = filename.substring(0, filename.length() - SUFFIX.length());
                try (InputStreamReader reader =
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    // パーシャルはメールテンプレートでは使わない(nfr-design-patterns.md §4)
                    compiled.put(key, Mustache.compile(reader, new MapPartialResolver(Map.of())));
                }
                logger.info("Compiled mail template: {}", key);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load mail templates", e);
        }
        return Map.copyOf(compiled);
    }

    /**
     * templateId と言語からテンプレートを返す。言語不在は en にフォールバック。
     *
     * @throws IllegalArgumentException templateId 自体が存在しない場合(実装バグ)
     */
    public Template get(String templateId, String language) {
        Template template = templates.get(templateId + "_" + language);
        if (template != null) {
            return template;
        }
        Template fallback = templates.get(templateId + "_" + FALLBACK_LANGUAGE);
        if (fallback == null) {
            throw new IllegalArgumentException("Mail template not found: " + templateId);
        }
        return fallback;
    }
}

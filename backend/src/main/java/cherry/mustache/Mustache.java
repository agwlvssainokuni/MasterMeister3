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

import cherry.mustache.ast.Node;
import cherry.mustache.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

/**
 * Mustacheテンプレートをコンパイルするためのstaticファクトリ。
 */
public final class Mustache {

    private static final Logger log = LoggerFactory.getLogger(Mustache.class);

    private Mustache() {
    }

    /**
     * @param template テンプレート文字列
     * @return コンパイル済みの{@link Template}（パーシャルリゾルバーは空のデフォルト実装）
     */
    public static Template compile(String template) {
        return compile(template, new MapPartialResolver(Map.of()));
    }

    /**
     * @param template        テンプレート文字列
     * @param partialResolver パーシャル解決に使う{@link PartialResolver}
     * @return コンパイル済みの{@link Template}
     */
    public static Template compile(String template, PartialResolver partialResolver) {
        Node root = new Parser().parse(template);
        return new Template(root, partialResolver);
    }

    /**
     * @param reader          テンプレート文字列を読み込む{@link Reader}
     * @param partialResolver パーシャル解決に使う{@link PartialResolver}
     * @return コンパイル済みの{@link Template}
     */
    public static Template compile(Reader reader, PartialResolver partialResolver) {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        try {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        } catch (IOException e) {
            log.debug("Failed to read template", e);
            throw new MustacheException("Failed to read template", e);
        }
        return compile(sb.toString(), partialResolver);
    }
}

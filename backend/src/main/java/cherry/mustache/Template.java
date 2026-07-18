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
import cherry.mustache.render.Context;
import cherry.mustache.render.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * コンパイル済みのMustacheテンプレート。内部にコンパイル済みAST（{@link Node}ツリー）を保持し、
 * 複数回の{@code render}呼び出しで再パースを行わず再利用できる（NFR-1）。
 * イミュータブルな{@link Context}に基づくため、同一インスタンスへの並行{@code render()}呼び出しに対して安全（NFR-REL-1）。
 */
public final class Template {

    private static final Logger log = LoggerFactory.getLogger(Template.class);

    private final Node root;
    private final PartialResolver defaultPartialResolver;
    private final Renderer renderer = new Renderer();

    Template(Node root, PartialResolver defaultPartialResolver) {
        this.root = root;
        this.defaultPartialResolver = defaultPartialResolver;
    }

    /**
     * @param data レンダリング対象データ（{@code Map}またはPOJO）
     * @return レンダリング結果の文字列
     */
    public String render(Object data) {
        return render(data, defaultPartialResolver);
    }

    /**
     * @param data レンダリング対象データ（{@code Map}またはPOJO）
     * @param out  レンダリング結果の書き込み先
     */
    public void render(Object data, Writer out) {
        renderTo(data, defaultPartialResolver, out);
    }

    /**
     * @param data            レンダリング対象データ（{@code Map}またはPOJO）
     * @param partialResolver コンパイル時のものを上書きするパーシャルリゾルバー
     * @return レンダリング結果の文字列
     */
    public String render(Object data, PartialResolver partialResolver) {
        StringWriter writer = new StringWriter();
        renderTo(data, partialResolver, writer);
        return writer.toString();
    }

    private void renderTo(Object data, PartialResolver partialResolver, Writer out) {
        try {
            renderer.render(root, new Context(data, null), partialResolver, out);
        } catch (IOException e) {
            log.debug("Failed to write render output", e);
            throw new MustacheRenderException("Failed to write render output", null, e);
        }
    }
}

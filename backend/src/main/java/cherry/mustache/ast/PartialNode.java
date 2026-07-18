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
package cherry.mustache.ast;

import cherry.mustache.MustacheRenderException;
import cherry.mustache.render.Context;
import cherry.mustache.render.RenderSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;

/**
 * パーシャル参照（{@code {{&gt;partialName}}}）を表すノード。
 * パーシャル内容はコンパイル時には解決せず、レンダリング時に都度{@link cherry.mustache.PartialResolver}で解決する（1.4節）。
 */
public final class PartialNode extends Node {

    private static final Logger log = LoggerFactory.getLogger(PartialNode.class);

    private static final String DEFAULT_OPEN = "{{";
    private static final String DEFAULT_CLOSE = "}}";

    private final String partialName;
    private final String indent;

    public PartialNode(String partialName, String indent) {
        this.partialName = partialName;
        this.indent = indent;
    }

    @Override
    public void render(Context context, RenderSession session, Writer out) throws IOException {
        String partialTemplate = session.partialResolver().resolve(partialName);
        if (partialTemplate == null) {
            log.debug("Partial not resolved, skipping: {}", partialName);
            return;
        }
        if (!session.beginPartial(partialName)) {
            log.warn("Partial nesting too deep, possible circular reference: {}", partialName);
            throw new MustacheRenderException("Partial nesting too deep, possible circular reference: " + partialName, partialName);
        }
        try {
            // インデントはレンダリング後の出力ではなく、パーシャルの生テンプレート文字列（パース前）に適用する。
            // こうすることで、埋め込まれたデータ値自体に含まれる改行はインデントの影響を受けない
            // （公式spec partials.yml「Standalone Indentation」準拠）。
            String source = indent.isEmpty() ? partialTemplate : applyIndent(partialTemplate, indent);
            Node partialRoot = session.reparse(source, DEFAULT_OPEN, DEFAULT_CLOSE);
            partialRoot.render(context, session, out);
        } finally {
            session.endPartial(partialName);
        }
    }

    private static String applyIndent(String template, String indent) {
        if (template.isEmpty()) {
            return template;
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = template.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            boolean isLast = i == lines.length - 1;
            if (!(isLast && lines[i].isEmpty())) {
                sb.append(indent).append(lines[i]);
            }
            if (!isLast) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}

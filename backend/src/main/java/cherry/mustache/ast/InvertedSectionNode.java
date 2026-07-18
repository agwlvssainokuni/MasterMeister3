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

import cherry.mustache.Lambda;
import cherry.mustache.render.Context;
import cherry.mustache.render.RenderSession;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * 否定セクション（{@code {{^key}}...{{/key}}}）を表すノード。値がfalsyの場合のみ描画する。
 * Lambdaは常にtruthy扱いとし、否定セクションとしては何も出力しない（BR-2.4節）。
 */
public final class InvertedSectionNode extends Node {

    private final String key;
    private final List<Node> children;

    public InvertedSectionNode(String key, List<Node> children) {
        this.key = key;
        this.children = children;
    }

    @Override
    public void render(Context context, RenderSession session, Writer out) throws IOException {
        Object value = context.resolve(key);
        if (value instanceof Lambda) {
            return;
        }
        if (Truthiness.isTruthy(value)) {
            return;
        }
        for (Node child : children) {
            child.render(context, session, out);
        }
    }
}

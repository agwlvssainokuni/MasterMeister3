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

/**
 * エスケープ有り変数展開（{@code {{key}}}）を行うノード。
 */
public final class VariableNode extends Node {

    private static final String DEFAULT_OPEN = "{{";
    private static final String DEFAULT_CLOSE = "}}";

    private final String key;

    public VariableNode(String key) {
        this.key = key;
    }

    @Override
    public void render(Context context, RenderSession session, Writer out) throws IOException {
        Object value = context.resolve(key);
        String text;
        if (value instanceof Lambda lambda) {
            // 公式spec ~lambdas.yml「Interpolation - Alternate Delimiters」: 変数タグのLambda戻り値は
            // 現在のデリミタではなく常にデフォルトデリミタで再パースする（セクションタグとは異なる）
            text = LambdaSupport.renderLambdaOutput(lambda.execute(""), context, session, DEFAULT_OPEN, DEFAULT_CLOSE);
        } else {
            text = value == null ? "" : String.valueOf(value);
        }
        out.write(HtmlEscaper.escape(text));
    }
}

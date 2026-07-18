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
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * セクション（{@code {{#key}}...{{/key}}}）を表すノード。
 * 真偽判定・リスト展開・Lambda呼び出しに応じて条件付き／繰り返しレンダリングを行う（BR-3）。
 */
public final class SectionNode extends Node {

    private final String key;
    private final List<Node> children;
    private final String rawText;
    private final String openDelimiter;
    private final String closeDelimiter;

    public SectionNode(String key, List<Node> children, String rawText, String openDelimiter, String closeDelimiter) {
        this.key = key;
        this.children = children;
        this.rawText = rawText;
        this.openDelimiter = openDelimiter;
        this.closeDelimiter = closeDelimiter;
    }

    @Override
    public void render(Context context, RenderSession session, Writer out) throws IOException {
        Object value = context.resolve(key);

        if (value instanceof Lambda lambda) {
            String text = LambdaSupport.renderLambdaOutput(lambda.execute(rawText), context, session, openDelimiter, closeDelimiter);
            out.write(text);
            return;
        }

        if (!Truthiness.isTruthy(value)) {
            return;
        }

        if (Truthiness.isListLike(value)) {
            for (Object element : toIterable(value)) {
                renderChildren(context.push(element), session, out);
            }
            return;
        }

        // 公式spec sections.yml「Variable test」: 真かつリスト以外の値は、スカラーであっても
        // 新しいスコープとしてpushする（{{.}}で値自身を、{{key}}で親コンテキストへのフォールバックを両立させる）
        renderChildren(context.push(value), session, out);
    }

    private void renderChildren(Context context, RenderSession session, Writer out) throws IOException {
        for (Node child : children) {
            child.render(context, session, out);
        }
    }

    private static Iterable<?> toIterable(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        int length = Array.getLength(value);
        Object[] elements = new Object[length];
        for (int i = 0; i < length; i++) {
            elements[i] = Array.get(value, i);
        }
        return Arrays.asList(elements);
    }
}

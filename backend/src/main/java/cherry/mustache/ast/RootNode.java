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

import cherry.mustache.render.Context;
import cherry.mustache.render.RenderSession;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * テンプレート全体のASTルート。子ノード群を順にレンダリングする。
 */
public final class RootNode extends Node {

    private final List<Node> children;

    public RootNode(List<Node> children) {
        this.children = children;
    }

    @Override
    public void render(Context context, RenderSession session, Writer out) throws IOException {
        for (Node child : children) {
            child.render(context, session, out);
        }
    }
}

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

/**
 * ASTノードの抽象基底クラス。各サブクラスが自身のレンダリング方法を実装する
 * （ポリモーフィズムによるノード自己レンダリング、Application Design Q7）。
 */
public abstract class Node {

    /**
     * 自身をレンダリングし{@code out}に書き込む。
     *
     * @param context 現在のデータコンテキスト
     * @param session パーシャル解決・ラムダ再パース・循環参照検出を提供するレンダリングセッション
     * @param out     出力先
     */
    public abstract void render(Context context, RenderSession session, Writer out) throws IOException;
}

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
package cherry.mustache.render;

import cherry.mustache.PartialResolver;
import cherry.mustache.ast.Node;
import cherry.mustache.ast.Reparser;

/**
 * 1回の{@code render()}呼び出しに紐づくレンダリング状態。
 * パーシャル解決・Lambda出力の再パース・パーシャル循環参照検出（BR-9）をまとめて提供する。
 * インスタンスは{@code render()}呼び出しごとに生成され、呼び出しスタックローカルに保持される
 * （スレッドローカルではない。nfr-design-patterns.md参照）。
 *
 * <p>循環参照検出は「パーシャル名の再出現」ではなく、パーシャル解決のネスト深さの上限（{@link #MAX_PARTIAL_DEPTH}）で行う。
 * 公式spec {@code partials.yml}「Recursion」テストが示す通り、同名パーシャルがデータ駆動で正当に自己再帰し
 * 終端するケース（木構造の描画等）が存在するため、名前の再出現だけでは循環参照と正当な再帰を区別できない
 * （Code Generation Step12で発見・是正）。
 */
public final class RenderSession {

    private static final int MAX_PARTIAL_DEPTH = 100;

    private final PartialResolver partialResolver;
    private final Reparser reparser;
    private int partialDepth = 0;

    public RenderSession(PartialResolver partialResolver, Reparser reparser) {
        this.partialResolver = partialResolver;
        this.reparser = reparser;
    }

    public PartialResolver partialResolver() {
        return partialResolver;
    }

    public Node reparse(String template, String openDelimiter, String closeDelimiter) {
        return reparser.reparse(template, openDelimiter, closeDelimiter);
    }

    /**
     * @param partialName 解決を開始するパーシャル名
     * @return ネスト深さの上限に達しておらず解決を開始できた場合は{@code true}
     */
    public boolean beginPartial(String partialName) {
        if (partialDepth >= MAX_PARTIAL_DEPTH) {
            return false;
        }
        partialDepth++;
        return true;
    }

    public void endPartial(String partialName) {
        partialDepth--;
    }
}

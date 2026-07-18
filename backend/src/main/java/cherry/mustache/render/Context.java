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

import java.util.Map;

/**
 * レンダリング中のデータスコープを表すイミュータブルなコンテキスト（BR-10）。
 * {@link #push(Object)}は新しい子{@code Context}を返し、既存インスタンスは変更しない。
 * 同一の{@code Template}インスタンスへの並行{@code render()}呼び出しに対して安全。
 */
public final class Context {

    private final Object data;
    private final Context parent;

    public Context(Object data, Context parent) {
        this.data = data;
        this.parent = parent;
    }

    /**
     * @param data 新しいスコープのデータ
     * @return {@code data}を最上位スコープとし、このインスタンスを親とする新しい{@code Context}
     */
    public Context push(Object data) {
        return new Context(data, this);
    }

    /**
     * ドット表記を含むキーから値を解決する（BR-6）。
     * 最初のセグメントのみ現在のスコープから親方向へスタックを遡って探索し、
     * 以降のセグメントは直前のセグメントで解決された値の内部のみを探索する。
     * 途中経路が解決できない場合（Broken Chain）は{@code null}を返す（エラーにしない）。
     *
     * @param key 解決対象のキー（ドット表記可、{@code "."}は現在のスコープ自体を指す）
     * @return 解決結果（未解決の場合は{@code null}）
     */
    public Object resolve(String key) {
        if (".".equals(key)) {
            return data;
        }
        String[] segments = key.split("\\.", -1);
        Object value = resolveFirstSegment(segments[0]);
        for (int i = 1; i < segments.length && value != null; i++) {
            value = lookupIn(value, segments[i]).value();
        }
        return value;
    }

    private Object resolveFirstSegment(String segment) {
        for (Context c = this; c != null; c = c.parent) {
            Lookup lookup = lookupIn(c.data, segment);
            if (lookup.found()) {
                return lookup.value();
            }
        }
        return null;
    }

    private static Lookup lookupIn(Object target, String segment) {
        if (target == null) {
            return Lookup.NOT_FOUND;
        }
        if (target instanceof Map<?, ?> map) {
            return map.containsKey(segment) ? Lookup.of(map.get(segment)) : Lookup.NOT_FOUND;
        }
        return PojoResolver.resolve(target, segment);
    }
}

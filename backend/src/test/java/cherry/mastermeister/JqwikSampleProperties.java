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
package cherry.mastermeister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * jqwik の動作確認用サンプルプロパティ(PBT-09 で選定したフレームワークの導入確認)。
 * 実プロパティ(PBT-02/03)はユニット④⑥の対象コンポーネントで実装する。
 */
class JqwikSampleProperties {

    @Property
    boolean reverseを2回適用すると元に戻る(@ForAll List<Integer> values) {
        List<Integer> roundTripped = new ArrayList<>(values);
        Collections.reverse(roundTripped);
        Collections.reverse(roundTripped);
        return roundTripped.equals(values);
    }
}

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
package cherry.mastermeister.common.event;

/**
 * 実効権限キャッシュの無効化要求イベント(粗粒度 — 全無効化)。
 * 発行元: 接続削除(connection)・スキーマ取込(metadata)。
 * permission パッケージ内の変更は EffectivePermissionResolver を直接呼ぶため
 * このイベントを使わない(パッケージ間の循環参照を避けるための片方向イベント)。
 */
public record PermissionCacheInvalidated() {
}

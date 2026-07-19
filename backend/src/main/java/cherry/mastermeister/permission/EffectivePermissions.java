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
package cherry.mastermeister.permission;

import java.util.Map;

/**
 * 解決済み実効権限(D-21)。取込済みメタデータに存在するカラムのみを対象とする
 * (孤児エントリは結果に現れない — Q2=A / PBT P4)。イミュータブル。
 * 操作可否(US-014 + D-15)はテーブル単位に合成済み。
 */
public record EffectivePermissions(
        Long userId,
        Long connectionId,
        Map<TableKey, TablePermissions> tables) {

    public record TableKey(String schema, String table) {
    }

    /**
     * @param columns カラム名 → 実効主権限(未設定カラムは NONE = アクセス権なし)
     * @param canCreate レコード作成可(CREATE 付与 かつ [主キーなし または 全主キー列 UPDATE])
     * @param canDelete レコード削除可(DELETE 付与 かつ 主キーあり かつ 全主キー列 READ 以上)
     * @param updatable レコード更新可の前提(主キーあり かつ 全主キー列 READ 以上 — ⑤でカラム単位の UPDATE と併用)
     */
    public record TablePermissions(
            Map<String, PermissionLevel> columns,
            boolean canCreate,
            boolean canDelete,
            boolean updatable) {
    }

    /** カラムの実効主権限(未設定・対象外は NONE)。 */
    public PermissionLevel columnLevel(String schema, String table, String column) {
        TablePermissions tablePermissions = tables.get(new TableKey(schema, table));
        if (tablePermissions == null) {
            return PermissionLevel.NONE;
        }
        return tablePermissions.columns().getOrDefault(column, PermissionLevel.NONE);
    }

    public boolean canRead(String schema, String table, String column) {
        return columnLevel(schema, table, column).ordinal() >= PermissionLevel.READ.ordinal();
    }
}

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
package cherry.mustache;

/**
 * テンプレートのレンダリング中に検出されたエラーを表す例外。
 * パーシャルの循環参照、POJOプロパティアクセス時の例外、パーシャルファイルの
 * I/Oエラー等、正常系（未解決キー・未解決パーシャル）とは区別すべき異常系で送出される。
 */
public class MustacheRenderException extends MustacheException {

    private final String key;

    /**
     * @param message エラーメッセージ
     * @param key     関連するタグ名・パーシャル名（無ければ{@code null}）
     */
    public MustacheRenderException(String message, String key) {
        super(message);
        this.key = key;
    }

    /**
     * @param message エラーメッセージ
     * @param key     関連するタグ名・パーシャル名（無ければ{@code null}）
     * @param cause   根本原因
     */
    public MustacheRenderException(String message, String key, Throwable cause) {
        super(message, cause);
        this.key = key;
    }

    /**
     * @return 関連するタグ名・パーシャル名（無ければ{@code null}）
     */
    public String getKey() {
        return key;
    }
}

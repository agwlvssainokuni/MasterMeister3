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
 * テンプレートの構文解析（パース）中に検出されたエラーを表す例外。
 * タグの対応不整合、未終了タグ、不正なデリミタ指定等で送出される。
 */
public class MustacheParseException extends MustacheException {

    private final int line;
    private final int column;

    /**
     * @param message エラーメッセージ
     * @param line    エラー発生位置の行番号（1始まり）
     * @param column  エラー発生位置の列番号（1始まり）
     */
    public MustacheParseException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    /**
     * @param message エラーメッセージ
     * @param line    エラー発生位置の行番号（1始まり）
     * @param column  エラー発生位置の列番号（1始まり）
     * @param cause   根本原因
     */
    public MustacheParseException(String message, int line, int column, Throwable cause) {
        super(message, cause);
        this.line = line;
        this.column = column;
    }

    /**
     * @return エラー発生位置の行番号（1始まり）
     */
    public int getLine() {
        return line;
    }

    /**
     * @return エラー発生位置の列番号（1始まり）
     */
    public int getColumn() {
        return column;
    }
}

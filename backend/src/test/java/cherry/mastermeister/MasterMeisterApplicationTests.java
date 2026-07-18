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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * コンテキスト起動テスト。
 * Flyway マイグレーション適用を含めてアプリケーションコンテキストが起動することを確認する。
 * test プロファイル(application-test.yaml)でインメモリ H2 + mm.app.* 全項目を設定する。
 */
@SpringBootTest
@ActiveProfiles("test")
class MasterMeisterApplicationTests {

    @Test
    void contextLoads() {
        // コンテキスト起動(Flyway 適用含む)が成功すればよい
    }
}

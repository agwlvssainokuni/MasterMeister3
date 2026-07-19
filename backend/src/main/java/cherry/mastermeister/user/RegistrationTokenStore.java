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
package cherry.mastermeister.user;

import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.util.SecureTokens;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登録トークンの発行・照合・使用済み化(US-001/002)。
 * 同一メールへの再申請は新トークンを発行し旧トークンと併存できる(最初に使われた 1 つで完了)。
 */
@Service
public class RegistrationTokenStore {

    private final RegistrationTokenRepository repository;
    private final AppProperties properties;
    private final Clock clock;

    public RegistrationTokenStore(
            RegistrationTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** トークンを発行し平文を返す(平文はメール記載のみ・保存しない)。 */
    @Transactional
    public String issue(String email, String language) {
        String plain = SecureTokens.generate();
        RegistrationToken token = new RegistrationToken();
        token.setTokenHash(SecureTokens.sha256Hex(plain));
        token.setEmail(email);
        token.setLanguage(language);
        token.setExpiresAt(LocalDateTime.now(clock)
                .plus(properties.userRegistration().tokenExpiry()));
        repository.save(token);
        return plain;
    }

    public Optional<RegistrationToken> findByPlainToken(String plain) {
        return repository.findByTokenHash(SecureTokens.sha256Hex(plain));
    }

    /** 検証順序: 存在 → 未使用 → 期限内(business-rules.md §1)。 */
    public boolean isValid(RegistrationToken token) {
        return token.getUsedAt() == null
                && token.getExpiresAt().isAfter(LocalDateTime.now(clock));
    }

    /**
     * 使用済み化。REQUIRES_NEW — 競合検知(既にユーザ行が存在)で主処理が 400 で
     * 終わる場合にも使用済み化は必ずコミットする(business-rules.md §1 登録完了 4)。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUsed(Long tokenId) {
        repository.findById(tokenId).ifPresent(token ->
                token.setUsedAt(LocalDateTime.now(clock)));
    }
}

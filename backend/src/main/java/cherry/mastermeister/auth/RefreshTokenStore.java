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
package cherry.mastermeister.auth;

import cherry.mastermeister.common.config.AppProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・ローテーション・失効(US-007/008/009)。
 * ローテーションは UPDATE の更新行数で原子的に確保する(nfr-design-patterns.md §5)。
 */
@Service
public class RefreshTokenStore {

    /** ローテーション結果。REUSE_DETECTED の場合はファミリ失効済み。 */
    public enum RotationStatus {
        ROTATED,
        REUSE_DETECTED,
        INVALID
    }

    public record RotationResult(RotationStatus status, RefreshToken token) {

        static RotationResult invalid() {
            return new RotationResult(RotationStatus.INVALID, null);
        }
    }

    private final RefreshTokenRepository repository;
    private final AppProperties properties;
    private final Clock clock;

    public RefreshTokenStore(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RefreshToken issue(Long userId, String familyId, String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(tokenHash);
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setExpiresAt(LocalDateTime.now(clock).plus(properties.jwt().refreshTokenExpiry()));
        return repository.save(token);
    }

    /**
     * 提示トークン(ハッシュ)のローテーション。
     * 現役行を UPDATE で確保できたときのみ ROTATED。rotated 済みの再提示は
     * 再利用検知としてファミリを一括失効し REUSE_DETECTED を返す(US-008)。
     * 不在・期限切れ・revoked は INVALID。
     */
    @Transactional
    public RotationResult rotate(String tokenHash) {
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = repository.markRotated(tokenHash, now);
        if (updated == 1) {
            RefreshToken token = repository.findByTokenHash(tokenHash).orElseThrow();
            return new RotationResult(RotationStatus.ROTATED, token);
        }
        RefreshToken token = repository.findByTokenHash(tokenHash).orElse(null);
        if (token == null || token.getRevokedAt() != null) {
            return RotationResult.invalid();
        }
        if (token.getRotatedAt() != null) {
            repository.revokeFamily(token.getFamilyId(), now);
            return new RotationResult(RotationStatus.REUSE_DETECTED, token);
        }
        return RotationResult.invalid(); // 期限切れ
    }

    /** 提示トークンのファミリ全体を明示失効する(US-009 ログアウト)。 */
    @Transactional
    public Optional<RefreshToken> revokeFamilyOf(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(token -> {
            repository.revokeFamily(token.getFamilyId(), LocalDateTime.now(clock));
            return token;
        });
    }

    /** ユーザ状態異常時などの保険的ファミリ失効。 */
    @Transactional
    public void revokeFamily(String familyId) {
        repository.revokeFamily(familyId, LocalDateTime.now(clock));
    }
}

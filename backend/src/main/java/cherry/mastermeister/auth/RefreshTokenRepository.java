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

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * ローテーションの原子的確保(NFR-U3-01)。現役トークンの行を UPDATE で確保できた
     * (更新行数 1)場合のみローテーション成功 — 同時リフレッシュの二重発行を防ぐ。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.rotatedAt = :now
            where t.tokenHash = :tokenHash
              and t.rotatedAt is null and t.revokedAt is null and t.expiresAt > :now
            """)
    int markRotated(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /** ファミリ一括失効(US-008 再利用検知・US-009 ログアウト)。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);
}

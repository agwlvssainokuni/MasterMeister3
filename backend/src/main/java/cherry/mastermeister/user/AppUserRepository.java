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

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findByRole(UserRole role);

    /** 管理者向け一覧(status フィルタ + email/表示名キーワード。keyword は小文字前提)。 */
    @Query("""
            select u from AppUser u
            where (:status is null or u.status = :status)
              and (:keyword is null
                   or lower(u.email) like concat('%', :keyword, '%')
                   or lower(coalesce(u.displayName, '')) like concat('%', :keyword, '%'))
            """)
    Page<AppUser> search(
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}

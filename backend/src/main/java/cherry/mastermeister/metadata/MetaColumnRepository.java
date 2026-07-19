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
package cherry.mastermeister.metadata;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetaColumnRepository extends JpaRepository<MetaColumn, Long> {

    List<MetaColumn> findByTableIdInOrderByOrdinal(List<Long> tableIds);

    @Query("SELECT c FROM MetaColumn c WHERE c.tableId IN "
            + "(SELECT t.id FROM MetaTable t WHERE t.schemaId IN "
            + "(SELECT s.id FROM MetaSchema s WHERE s.connectionId = :connectionId))")
    List<MetaColumn> findByConnectionId(@Param("connectionId") Long connectionId);
}

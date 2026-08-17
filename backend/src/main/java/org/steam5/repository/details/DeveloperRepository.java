package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Developer;

import java.util.Optional;

@Repository
public interface DeveloperRepository extends JpaRepository<Developer, Long> {
    Optional<Developer> findByNameIgnoreCase(String name);

    /** Atomic insert-or-ignore used by the lookup-or-create path to avoid check-then-insert races. */
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "INSERT INTO developer(name) VALUES (:name) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("name") String name);
}



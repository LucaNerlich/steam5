package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Genre;

import java.util.Optional;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByDescriptionIgnoreCase(String description);

    /** Atomic insert-or-ignore used by the lookup-or-create path to avoid check-then-insert races. */
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "INSERT INTO genre(description) VALUES (:description) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("description") String description);
}



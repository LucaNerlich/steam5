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

    /** Serializes lookup-or-create for the same description across case variants. */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext('genre-desc'), hashtext(lower(:description)))", nativeQuery = true)
    void lockByDescriptionIgnoreCase(@Param("description") String description);

    /** Atomic insert-or-ignore used by the lookup-or-create path to avoid check-then-insert races. */
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = """
            INSERT INTO genre(description)
            SELECT :description
            WHERE NOT EXISTS (SELECT 1 FROM genre g WHERE lower(g.description) = lower(:description))
            """, nativeQuery = true)
    int insertIfAbsent(@Param("description") String description);
}



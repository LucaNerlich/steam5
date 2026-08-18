package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Publisher;

import java.util.Optional;

@Repository
public interface PublisherRepository extends JpaRepository<Publisher, Long> {
    Optional<Publisher> findByNameIgnoreCase(String name);

    /** Serializes lookup-or-create for the same name across case variants. */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext('publisher-name'), hashtext(lower(:name)))", nativeQuery = true)
    void lockByNameIgnoreCase(@Param("name") String name);

    /** Atomic insert-or-ignore used by the lookup-or-create path to avoid check-then-insert races. */
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = """
            INSERT INTO publisher(name)
            SELECT :name
            WHERE NOT EXISTS (SELECT 1 FROM publisher p WHERE lower(p.name) = lower(:name))
            """, nativeQuery = true)
    int insertIfAbsent(@Param("name") String name);
}



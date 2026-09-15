package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.steam5.domain.User;

import java.time.OffsetDateTime;
import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    /** Atomically advances the JWT cutoff without allowing a concurrent older logout to move it backwards. */
    @Modifying
    @Query("""
            update User u
               set u.tokenNotValidBefore = case
                   when u.tokenNotValidBefore is null or u.tokenNotValidBefore < :instant then :instant
                   else u.tokenNotValidBefore
               end
             where u.steamId = :steamId
            """)
    int advanceTokenNotValidBefore(@Param("steamId") String steamId,
                                   @Param("instant") OffsetDateTime instant);

    /**
     * Finds up to 10 users whose persona name contains the given substring (case-insensitive),
     * ordered by persona name for deterministic results. Backs the mention-autocomplete search.
     */
    List<User> findTop10ByPersonaNameContainingIgnoreCaseAndPersonaNameNotNullOrderByPersonaNameAsc(String personaName);
}


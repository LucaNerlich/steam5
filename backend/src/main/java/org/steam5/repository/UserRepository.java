package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Finds up to 10 users whose persona name contains the given substring (case-insensitive),
     * ordered by persona name for deterministic results. Backs the mention-autocomplete search.
     */
    List<User> findTop10ByPersonaNameContainingIgnoreCaseAndPersonaNameNotNullOrderByPersonaNameAsc(String personaName);
}



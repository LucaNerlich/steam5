package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Genre;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
}



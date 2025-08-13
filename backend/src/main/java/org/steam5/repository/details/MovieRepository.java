package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Movie;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {
}



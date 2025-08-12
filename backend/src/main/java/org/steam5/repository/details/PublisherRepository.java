package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Movie;
import org.steam5.domain.details.Publisher;

@Repository
public interface PublisherRepository extends JpaRepository<Publisher, Long> {
}



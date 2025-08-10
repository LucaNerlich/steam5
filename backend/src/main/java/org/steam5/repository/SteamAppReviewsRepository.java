package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.steam5.domain.SteamAppReviews;

@Repository
public interface SteamAppReviewsRepository extends JpaRepository<SteamAppReviews, Long> {
}



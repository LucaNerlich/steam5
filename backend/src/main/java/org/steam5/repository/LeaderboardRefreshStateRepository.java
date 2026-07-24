package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;

public interface LeaderboardRefreshStateRepository extends JpaRepository<LeaderboardRefreshState, LeaderboardType> {
}

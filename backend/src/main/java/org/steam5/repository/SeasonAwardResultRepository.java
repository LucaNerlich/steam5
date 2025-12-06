package org.steam5.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.SeasonAwardCategory;
import org.steam5.domain.SeasonAwardResult;

import java.util.List;

public interface SeasonAwardResultRepository extends JpaRepository<SeasonAwardResult, Long> {

    List<SeasonAwardResult> findAllBySeasonIdOrderByCategoryAscPlacementLevelAsc(Long seasonId);

    List<SeasonAwardResult> findAllBySeasonIdAndCategoryOrderByPlacementLevelAsc(Long seasonId, SeasonAwardCategory category);

    @EntityGraph(attributePaths = "season")
    List<SeasonAwardResult> findAllBySteamIdOrderBySeasonSeasonNumberDescPlacementLevelAsc(String steamId);

    void deleteAllBySeasonId(Long seasonId);
}



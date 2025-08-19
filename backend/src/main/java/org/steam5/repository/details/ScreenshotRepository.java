package org.steam5.repository.details;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Screenshot;

import java.util.List;

@Repository
public interface ScreenshotRepository extends JpaRepository<Screenshot, Long> {
    @Query("select s from Screenshot s where (s.blurhashThumb is null or s.blurhashThumb = '' or s.blurhashFull is null or s.blurhashFull = '') order by s.id asc")
    Page<Screenshot> findPageWithoutBlurhash(Pageable pageable);

    @Query("select s from Screenshot s where s.appId.appId = :appId and (s.blurhashThumb is null or s.blurhashThumb = '' or s.blurhashFull is null or s.blurhashFull = '') order by s.id asc")
    List<Screenshot> findMissingForApp(@Param("appId") Long appId);
}



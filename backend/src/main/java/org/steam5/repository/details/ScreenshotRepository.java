package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Screenshot;

@Repository
public interface ScreenshotRepository extends JpaRepository<Screenshot, Long> {
}



package org.steam5.repository.details;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.steam5.domain.details.Price;

@Repository
public interface PriceRepository extends JpaRepository<Price, Long> {
}



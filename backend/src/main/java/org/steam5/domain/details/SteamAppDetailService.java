package org.steam5.domain.details;

import org.springframework.stereotype.Service;
import org.steam5.repository.details.SteamAppDetailRepository;

@Service
public class SteamAppDetailService {

    private final SteamAppDetailRepository repository;

    public SteamAppDetailService(final SteamAppDetailRepository repository) {
        this.repository = repository;
    }
}

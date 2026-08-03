package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.User;
import org.steam5.repository.UserRepository;
import org.steam5.security.CurrentUser;

import java.util.Collections;
import java.util.List;

/**
 * Read-only user search for the comment composer's @mention autocomplete.
 * Public (permitAll) and rate-limited per IP by {@link org.steam5.security.UserSearchRateLimitFilter},
 * since a search-by-name endpoint enables user enumeration.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserSearchController {

    private static final int MIN_QUERY_LENGTH = 2;

    private final UserRepository userRepository;

    @GetMapping("/search")
    public List<UserSearchDto> search(@RequestParam(name = "q", required = false) final String q,
                                      @CurrentUser final String steamId) {
        final String query = q == null ? "" : q.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return Collections.emptyList();
        }
        final List<User> matches =
                userRepository.findTop10ByPersonaNameContainingIgnoreCaseAndPersonaNameNotNullOrderByPersonaNameAsc(query);
        return matches.stream()
                .filter(user -> steamId == null || !steamId.equals(user.getSteamId()))
                .map(user -> new UserSearchDto(user.getSteamId(), user.getPersonaName(), user.getAvatar()))
                .toList();
    }

    public record UserSearchDto(String steamId, String personaName, String avatar) {
    }
}

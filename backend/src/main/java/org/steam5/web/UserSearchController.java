package org.steam5.web;

import jakarta.validation.constraints.Size;
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
    // Bounds the LIKE pattern sent to the DB; well above any realistic persona name length.
    // Enforced via @Size so an oversized q is rejected (400) before it reaches the query,
    // instead of silently swallowed like the min-length case below.
    private static final int MAX_QUERY_LENGTH = 64;

    private final UserRepository userRepository;

    @GetMapping("/search")
    public List<UserSearchDto> search(
            @RequestParam(name = "q", required = false)
            @Size(max = MAX_QUERY_LENGTH, message = "q exceeds maximum length")
            final String q,
            @CurrentUser final String steamId) {
        final String query = q == null ? "" : q.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return Collections.emptyList();
        }
        final List<User> matches =
                userRepository.findTop10ByPersonaNameContainingIgnoreCaseAndPersonaNameNotNullOrderByPersonaNameAsc(
                        escapeLikeWildcards(query));
        return matches.stream()
                .filter(user -> steamId == null || !steamId.equals(user.getSteamId()))
                .map(user -> new UserSearchDto(user.getSteamId(), user.getPersonaName(), user.getAvatar()))
                .toList();
    }

    /**
     * Escapes the LIKE wildcard characters ({@code %}, {@code _}) and the escape character
     * ({@code \}) so a raw search term is matched literally. Without this, {@code q=%%}
     * matches every user and {@code q=a_b} matches any three-character name, defeating the
     * enumeration-guard intent of the endpoint. PostgreSQL's default LIKE escape is backslash.
     */
    private static String escapeLikeWildcards(final String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record UserSearchDto(String steamId, String personaName, String avatar) {
    }
}

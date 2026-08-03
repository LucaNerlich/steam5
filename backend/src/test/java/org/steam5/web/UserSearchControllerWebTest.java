package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.steam5.repository.UserRepository;
import org.steam5.security.CurrentUserArgumentResolver;
import org.steam5.service.AuthTokenService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real MVC dispatch pipeline (unlike a plain unit test calling the controller
 * method directly) to confirm {@code @Size} on {@code q} actually produces a 400. Uses
 * standaloneSetup with an explicit Validator rather than @WebMvcTest, which isn't on this
 * project's test classpath (Spring Boot 4 moved it out of spring-boot-test-autoconfigure).
 */
class UserSearchControllerWebTest {

    private MockMvc mockMvc;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        final UserSearchController controller = new UserSearchController(userRepository);
        final AuthTokenService authTokenService = mock(AuthTokenService.class);
        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(authTokenService))
                .build();
    }

    @Test
    void search_rejectsOversizedQueryWith400() throws Exception {
        final String tooLong = "a".repeat(65);

        mockMvc.perform(get("/api/users/search").param("q", tooLong))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_acceptsQueryAtMaxLength() throws Exception {
        final String atMax = "a".repeat(64);
        when(userRepository.findTop10ByPersonaNameContainingIgnoreCaseAndPersonaNameNotNullOrderByPersonaNameAsc(atMax))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/users/search").param("q", atMax))
                .andExpect(status().isOk());
    }

    @Test
    void search_returnsEmptyListForShortQueryWithout400() throws Exception {
        mockMvc.perform(get("/api/users/search").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}

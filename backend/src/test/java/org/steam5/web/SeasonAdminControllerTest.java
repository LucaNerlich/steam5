package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.steam5.domain.GameDate;
import org.steam5.service.SeasonService;
import org.steam5.web.admin.SeasonAdminController;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonAdminControllerTest {

    private SeasonService seasonService;
    private SeasonAdminController controller;

    @BeforeEach
    void setUp() {
        seasonService = mock(SeasonService.class);
        controller = new SeasonAdminController(seasonService);
    }

    @Test
    void backfill_rejectsFutureStartDateWith400() {
        // The real service rejects a future start with IllegalArgumentException
        // (SeasonService.backfillRange); the controller must own the 400 before delegating.
        final LocalDate startDate = GameDate.todayUtc().plusDays(1);
        final LocalDate endDate = startDate.plusDays(1);
        when(seasonService.backfillRange(startDate, endDate))
                .thenThrow(new IllegalArgumentException("startDate must not be in the future"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.backfill(new SeasonAdminController.BackfillRequest(startDate, endDate, false)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(seasonService, never()).backfillRange(any(), any());
    }

    @Test
    void backfill_allowsStartDateToday() {
        final LocalDate today = GameDate.todayUtc();
        when(seasonService.backfillRange(today, today.plusDays(1))).thenReturn(List.of());

        ResponseEntity<SeasonAdminController.BackfillResponse> response =
                controller.backfill(new SeasonAdminController.BackfillRequest(today, today.plusDays(1), false));

        assertEquals(200, response.getStatusCode().value());
        verify(seasonService).backfillRange(today, today.plusDays(1));
    }
}

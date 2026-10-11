package dev.linkpulse.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Resolução e validação do período das estatísticas (D-01, D-02), sem Docker.
 */
class StatsPeriodTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-10");

    private static void assertInvalidPeriod(LocalDate from, LocalDate to) {
        assertThatThrownBy(() -> StatsPeriod.resolve(from, to, TODAY))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getBody().getType().toString())
                            .isEqualTo("/problems/invalid-period");
                    assertThat(e.getBody().getProperties())
                            .containsEntry("maxDays", StatsPeriod.MAX_DAYS)
                            .containsEntry("from", from.toString())
                            .containsEntry("to", to.toString());
                });
    }

    @Test
    void withoutDatesIsLastThirtyDaysEndingToday() {
        StatsPeriod period = StatsPeriod.resolve(null, null, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.parse("2026-09-11"));
        assertThat(period.to()).isEqualTo(TODAY);
        assertThat(period.days()).isEqualTo(StatsPeriod.DEFAULT_DAYS);
    }

    @Test
    void onlyFromEndsToday() {
        StatsPeriod period = StatsPeriod.resolve(LocalDate.parse("2026-10-01"), null, TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void onlyToStartsTwentyNineDaysBefore() {
        StatsPeriod period = StatsPeriod.resolve(null, LocalDate.parse("2026-10-05"), TODAY);

        assertThat(period.from()).isEqualTo(LocalDate.parse("2026-09-06"));
        assertThat(period.to()).isEqualTo(LocalDate.parse("2026-10-05"));
    }

    @Test
    void sameDayIsOneDay() {
        assertThat(StatsPeriod.resolve(TODAY, TODAY, TODAY).days()).isEqualTo(1);
    }

    @Test
    void exactlyMaxDaysIsAccepted() {
        StatsPeriod period = StatsPeriod.resolve(LocalDate.parse("2025-10-10"), TODAY, TODAY);

        assertThat(period.days()).isEqualTo(366);
    }

    @Test
    void oneDayOverMaxIsInvalidPeriod() {
        assertInvalidPeriod(LocalDate.parse("2025-10-09"), TODAY);
    }

    @Test
    void fromAfterToIsInvalidPeriod() {
        assertInvalidPeriod(LocalDate.parse("2026-10-11"), TODAY);
    }

    @Test
    void boundsAreUtcMidnights() {
        StatsPeriod period = new StatsPeriod(LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-03-05"));

        assertThat(period.startInclusive()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-03-06T00:00:00Z"));
    }
}

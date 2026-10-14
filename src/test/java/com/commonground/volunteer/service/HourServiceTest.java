package com.commonground.volunteer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class HourServiceTest {

  private static final Instant START = Instant.parse("2026-01-01T09:00:00Z");

  private static BigDecimal hoursAfter(long minutes) {
    return HourService.attendedHours(START, START.plus(minutes, ChronoUnit.MINUTES));
  }

  @Test
  void roundsToNearestQuarterHour() {
    assertThat(hoursAfter(60)).isEqualByComparingTo("1.00");
    assertThat(hoursAfter(67)).isEqualByComparingTo("1.00");
    assertThat(hoursAfter(68)).isEqualByComparingTo("1.25");
    assertThat(hoursAfter(205)).isEqualByComparingTo("3.50");
  }

  @Test
  void clampsToValidRange() {
    assertThat(hoursAfter(0)).isEqualByComparingTo("0.25");
    assertThat(hoursAfter(3)).isEqualByComparingTo("0.25");
    assertThat(hoursAfter(30 * 60)).isEqualByComparingTo("24.00");
    assertThat(HourService.attendedHours(null, START)).isEqualByComparingTo("0.25");
  }
}

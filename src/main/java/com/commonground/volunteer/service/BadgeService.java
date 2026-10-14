package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.HourLog;
import com.commonground.volunteer.repository.HourLogRepository;
import com.commonground.volunteer.web.dto.HourDtos.BadgeResponse;
import com.commonground.volunteer.web.dto.HourDtos.BadgeSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hour milestone badges, derived from approved hours (kept in sync with the web/Android milestone lists). */
@Service
public class BadgeService {

  public record Milestone(int hours, String title, String tier) {}

  public static final List<Milestone> MILESTONES = List.of(
      new Milestone(1, "First Step Volunteer", "BRONZE"),
      new Milestone(10, "Bronze Helper", "BRONZE"),
      new Milestone(25, "Community Pillar", "SILVER"),
      new Milestone(50, "Golden Heart Champion", "GOLD"),
      new Milestone(100, "Century Legend", "GOLD"),
      new Milestone(500, "Platinum Master", "PLATINUM"),
      new Milestone(1000, "Diamond Grandmaster", "DIAMOND"),
      new Milestone(2000, "Mythic Overlord", "MYTHIC"),
      new Milestone(5000, "Immortal Sovereign", "IMMORTAL")
  );

  private final HourLogRepository hourLogRepository;

  public BadgeService(HourLogRepository hourLogRepository) {
    this.hourLogRepository = hourLogRepository;
  }

  @Transactional(readOnly = true)
  public BadgeSummary summary(UUID userId) {
    List<HourLog> approved =
        hourLogRepository.findByUserIdAndStatusOrderByWorkDateAscCreatedAtAsc(userId, HourLog.Status.APPROVED);

    BigDecimal running = BigDecimal.ZERO;
    LocalDate[] earnedDates = new LocalDate[MILESTONES.size()];
    for (HourLog log : approved) {
      running = running.add(log.getHours());
      for (int i = 0; i < MILESTONES.size(); i++) {
        if (earnedDates[i] == null && running.compareTo(BigDecimal.valueOf(MILESTONES.get(i).hours())) >= 0) {
          earnedDates[i] = log.getWorkDate();
        }
      }
    }

    BigDecimal total = running;
    List<BadgeResponse> badges = new ArrayList<>();
    for (int i = 0; i < MILESTONES.size(); i++) {
      badges.add(toBadge(MILESTONES.get(i), total, earnedDates[i]));
    }
    BadgeResponse current = badges.stream().filter(BadgeResponse::earned).reduce((a, b) -> b).orElse(null);
    BadgeResponse next = badges.stream().filter(b -> !b.earned()).findFirst().orElse(badges.get(badges.size() - 1));
    return new BadgeSummary(total, current, next, badges);
  }

  /** Milestones crossed when a user's approved total moves from {@code before} to {@code after}. */
  public List<Milestone> newlyEarned(BigDecimal before, BigDecimal after) {
    return MILESTONES.stream()
        .filter(m -> before.compareTo(BigDecimal.valueOf(m.hours())) < 0
            && after.compareTo(BigDecimal.valueOf(m.hours())) >= 0)
        .toList();
  }

  private static BadgeResponse toBadge(Milestone m, BigDecimal total, LocalDate earnedDate) {
    BigDecimal target = BigDecimal.valueOf(m.hours());
    return new BadgeResponse(m.hours(), m.title(), m.tier(), earnedDate != null, earnedDate, total.min(target));
  }
}

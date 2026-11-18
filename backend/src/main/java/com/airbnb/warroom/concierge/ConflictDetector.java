package com.airbnb.warroom.concierge;

import com.airbnb.warroom.board.ItineraryItem;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.concierge.ConciergeDtos.ConflictView;
import com.airbnb.warroom.venue.Venue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic conflict detector (PRD 6.2): flags schedule overlaps (two items overlapping in time
 * on the same day) and budget breaches (cumulative per-person cost exceeding the group's binding
 * ceiling). Pure function of the current board — unit-testable without sockets.
 */
@Component
public class ConflictDetector {

    /**
     * @param items          current board items
     * @param venueById      venue metadata for pricing
     * @param perPersonCeiling the binding per-person budget ceiling (e.g. the lowest among travellers),
     *                         or null to skip the budget check
     */
    public List<ConflictView> detect(List<ItineraryItem> items, Map<Long, Venue> venueById,
                                     BigDecimal perPersonCeiling) {
        List<ConflictView> conflicts = new ArrayList<>();
        conflicts.addAll(scheduleOverlaps(items));
        ConflictView budget = budgetBreach(items, venueById, perPersonCeiling);
        if (budget != null) {
            conflicts.add(budget);
        }
        return conflicts;
    }

    /** All pairs of items on the same day whose [start,end) intervals overlap. */
    public List<ConflictView> scheduleOverlaps(List<ItineraryItem> items) {
        List<ConflictView> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                ItineraryItem a = items.get(i);
                ItineraryItem b = items.get(j);
                if (a.getDayIndex() != b.getDayIndex()) {
                    continue;
                }
                boolean overlap = a.getStartTime().isBefore(b.getEndTime())
                        && b.getStartTime().isBefore(a.getEndTime());
                if (overlap) {
                    out.add(new ConflictView("overlap",
                            List.of(PublicId.encode(PublicId.ITEM, a.getId()),
                                    PublicId.encode(PublicId.ITEM, b.getId())),
                            "Items overlap on day " + a.getDayIndex() + " ("
                                    + a.getStartTime() + "-" + a.getEndTime() + " vs "
                                    + b.getStartTime() + "-" + b.getEndTime() + ")"));
                }
            }
        }
        return out;
    }

    /** Budget breach when the sum of per-person prices across pinned items exceeds the ceiling. */
    public ConflictView budgetBreach(List<ItineraryItem> items, Map<Long, Venue> venueById,
                                     BigDecimal perPersonCeiling) {
        if (perPersonCeiling == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        List<String> itemIds = new ArrayList<>();
        for (ItineraryItem it : items) {
            Venue v = venueById.get(it.getVenueId());
            if (v != null) {
                total = total.add(v.pricePerPerson());
                itemIds.add(PublicId.encode(PublicId.ITEM, it.getId()));
            }
        }
        if (total.compareTo(perPersonCeiling) > 0) {
            return new ConflictView("budgetBreach", itemIds,
                    "Per-person total $" + total.stripTrailingZeros().toPlainString()
                            + " exceeds ceiling $" + perPersonCeiling.stripTrailingZeros().toPlainString());
        }
        return null;
    }
}

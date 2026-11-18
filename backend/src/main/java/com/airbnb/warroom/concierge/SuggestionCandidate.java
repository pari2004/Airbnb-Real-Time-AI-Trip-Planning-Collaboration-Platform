package com.airbnb.warroom.concierge;

import com.airbnb.warroom.venue.Venue;

/** A venue that passed all hard constraints, with the components used to rank it (PRD 6.2). */
public record SuggestionCandidate(Venue venue, Double distanceKm, double score,
                                  double proximityScore, double ratingScore, double headroomScore,
                                  double lexicalSim) {
}

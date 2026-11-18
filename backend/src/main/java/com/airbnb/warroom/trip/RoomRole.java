package com.airbnb.warroom.trip;

/**
 * Per-room roles (PRD 5.1/10.1). Capability model:
 * <ul>
 *   <li>VIEWER  — view board, chat, vote.</li>
 *   <li>PLANNER — everything a viewer can, plus pin/move/ask AI.</li>
 *   <li>LEADER  — everything a planner can, plus lock/override, moderation, room settings.</li>
 * </ul>
 * (Global ADMIN, carried in the JWT, additionally governs venue dataset management.)
 */
public enum RoomRole {
    VIEWER(0),
    PLANNER(1),
    LEADER(2);

    private final int rank;

    RoomRole(int rank) {
        this.rank = rank;
    }

    public boolean canVote() {
        return true;
    }

    public boolean canPin() {
        return rank >= PLANNER.rank;
    }

    public boolean canModerate() {
        return rank >= LEADER.rank;
    }

    public static RoomRole from(String s) {
        return switch (s == null ? "" : s.toLowerCase()) {
            case "leader" -> LEADER;
            case "viewer" -> VIEWER;
            default -> PLANNER;
        };
    }

    public String wire() {
        return name().toLowerCase();
    }
}

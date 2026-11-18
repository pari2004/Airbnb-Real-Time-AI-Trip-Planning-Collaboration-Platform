package com.airbnb.warroom.realtime;

/** Canonical WebSocket event names (PRD 7.3.4). */
public final class Events {

    private Events() {
    }

    // Inbound (client -> server)
    public static final String BOARD_MOVE = "trip.board.move";     // {itemId, dayIndex, startTime, endTime, positionVersion}
    public static final String BOARD_PIN = "trip.board.pin";       // {venueId, dayIndex, startTime, endTime}
    public static final String BOARD_LOCK = "trip.board.lock";     // {itemId}
    public static final String BOARD_UNLOCK = "trip.board.unlock"; // {itemId}  (leader override)
    public static final String VOTE_CAST = "trip.vote.cast";       // {itemId, choiceVenueId}
    public static final String VOTE_OPEN_REQ = "trip.vote.openRequest"; // {itemId, candidateVenueIds, durationSeconds}
    public static final String CHAT_SEND = "trip.chat.send";       // {body}
    public static final String TYPING = "trip.typing";             // {isTyping}
    public static final String SYNC_PING = "trip.sync.ping";       // {clientSentAt}

    // Outbound (server -> clients)
    public static final String BOARD_UPDATE = "trip.board.update"; // {itemId, dayIndex, startTime, endTime, lifecycleStage, positionVersion}
    public static final String BOARD_REMOVE = "trip.board.remove"; // {itemId}
    public static final String BOARD_CONFLICT = "trip.board.conflict"; // {conflictType, itemIds, details}
    public static final String PRESENCE_UPDATE = "trip.presence.update"; // {participantId, action, participantCount}
    public static final String CHAT_MESSAGE = "trip.chat.message"; // {messageId, userId, body, tsSeconds, msgType}
    public static final String TYPING_UPDATE = "trip.typing.update"; // {userId, isTyping}
    public static final String VOTE_OPEN = "trip.vote.open";       // {itemId, candidateVenueIds, closesAt}
    public static final String VOTE_RESOLVED = "trip.vote.resolved"; // {itemId, winningVenueId}
    public static final String AI_SUGGESTION = "trip.ai.suggestion"; // {questionId, userId, answer, suggestedVenueId, sourceEvents}
    public static final String SYNC_PONG = "trip.sync.pong";       // {clientSentAt, serverReceivedAt}
    public static final String BUDGET_UPDATE = "trip.budget.update"; // live budget summary panel
    public static final String VOTE_CAST_ACK = "trip.vote.cast";   // {itemId, userId, choiceVenueId}
    public static final String SNAPSHOT = "trip.sync.snapshot";    // full board state on join
    public static final String ROOM_STATE = "trip.room.state";     // {leaderConnected, status}
    public static final String ERROR = "trip.error";               // {message}
}

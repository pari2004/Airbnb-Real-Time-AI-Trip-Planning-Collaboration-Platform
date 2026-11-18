package com.airbnb.warroom.common;

/**
 * Maps internal numeric primary keys to the prefixed public identifiers used in the
 * API contract (PRD 7.3): users -> {@code u_}, trips -> {@code tr_}, venues -> {@code v_},
 * itinerary items -> {@code it_}, chat messages -> {@code m_}, AI questions -> {@code q_}.
 *
 * <p>Encoding is lenient on input: any trailing digit run is parsed, so both {@code "tr_44210"}
 * and {@code "44210"} decode to 44210.
 */
public final class PublicId {

    public static final String USER = "u_";
    public static final String TRIP = "tr_";
    public static final String VENUE = "v_";
    public static final String ITEM = "it_";
    public static final String MESSAGE = "m_";
    public static final String QUESTION = "q_";

    private PublicId() {
    }

    public static String encode(String prefix, Long id) {
        return id == null ? null : prefix + id;
    }

    /** Decodes a public id (prefixed or bare) to its numeric value, or throws on garbage. */
    public static Long decode(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new ApiException(400, "Missing identifier");
        }
        int i = publicId.length();
        while (i > 0 && Character.isDigit(publicId.charAt(i - 1))) {
            i--;
        }
        String digits = publicId.substring(i);
        if (digits.isEmpty()) {
            throw new ApiException(400, "Malformed identifier: " + publicId);
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Malformed identifier: " + publicId);
        }
    }
}

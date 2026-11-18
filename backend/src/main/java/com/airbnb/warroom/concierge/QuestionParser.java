package com.airbnb.warroom.concierge;

import com.airbnb.warroom.venue.DietaryVocabulary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight NL extraction from a traveller's question so the free-text actually drives filtering
 * (e.g. "under $25/person", "vegan", "dinner"). Everything it extracts is a <em>hard</em> constraint
 * fed into {@link ConstraintContext}; nothing here invents data.
 */
@Component
public class QuestionParser {

    private static final Pattern BUDGET = Pattern.compile(
            "(?:under|below|less than|max|up to|<)\\s*\\$?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> CATEGORIES = Set.of(
            "dinner", "lunch", "breakfast", "cafe", "activity", "lodging", "hotel", "tour");

    public BigDecimal parseBudget(String question) {
        Matcher m = BUDGET.matcher(question);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    public Set<String> parseDietary(String question) {
        String lower = question.toLowerCase();
        Set<String> found = new LinkedHashSet<>();
        for (String tag : DietaryVocabulary.TAGS) {
            if (lower.contains(tag)) {
                found.add(tag);
            }
        }
        // common synonym
        if (lower.contains("veggie")) {
            found.add("vegetarian");
        }
        return found;
    }

    public String parseCategory(String question) {
        String lower = question.toLowerCase();
        for (String c : CATEGORIES) {
            if (lower.contains(c)) {
                return switch (c) {
                    case "hotel" -> "lodging";
                    case "tour" -> "activity";
                    case "lunch", "breakfast" -> "dinner"; // meals map to the dining category in our dataset
                    default -> c;
                };
            }
        }
        return null;
    }
}

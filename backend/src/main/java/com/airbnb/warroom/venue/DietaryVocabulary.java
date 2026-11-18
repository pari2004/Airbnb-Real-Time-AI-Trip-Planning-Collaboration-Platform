package com.airbnb.warroom.venue;

import java.util.Set;

/** Documented dietary-tag vocabulary. Uploads carrying any other tag are rejected (PRD 6.3). */
public final class DietaryVocabulary {

    public static final Set<String> TAGS = Set.of(
            "vegetarian", "vegan", "halal", "kosher", "gluten-free", "dairy-free", "nut-free", "pescatarian");

    private DietaryVocabulary() {
    }

    public static boolean isValid(String tag) {
        return tag != null && TAGS.contains(tag.toLowerCase());
    }
}

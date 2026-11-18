package com.airbnb.warroom.concierge;

import com.airbnb.warroom.venue.Venue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-JVM TF-IDF retriever over venue descriptions — the "retrieval" half of RAG, with zero external
 * vector database (PRD 9.x: pure-Java AI engine). Produces a lexical-similarity {@link Scorer} used
 * to order the grounding context and break ties, while the primary ranking stays the 40/30/30
 * structured score required by PRD 6.2.
 */
@Component
public class RagRetriever {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "for", "near", "our", "we", "us", "with",
            "in", "on", "at", "under", "over", "is", "are", "find", "suggest", "spot", "place",
            "per", "person", "me", "my", "please", "can", "you", "that", "this");

    /** Scores a venue's lexical similarity to the query the scorer was built for. */
    public interface Scorer {
        double similarity(Venue venue);
    }

    public Scorer buildScorer(List<Venue> corpus, String query) {
        if (corpus.isEmpty()) {
            return v -> 0.0;
        }
        // Document frequency across the corpus.
        Map<String, Integer> df = new HashMap<>();
        Map<Long, Map<String, Integer>> tfByVenue = new HashMap<>();
        for (Venue v : corpus) {
            Map<String, Integer> tf = termFreq(docText(v));
            tfByVenue.put(v.id(), tf);
            for (String term : tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
        }
        int n = corpus.size();
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> e : df.entrySet()) {
            idf.put(e.getKey(), Math.log((double) (n + 1) / (e.getValue() + 1)) + 1.0);
        }

        Map<String, Integer> qtf = termFreq(query);
        Map<String, Double> qvec = tfidfVector(qtf, idf);

        Map<Long, Double> sims = new HashMap<>();
        for (Venue v : corpus) {
            Map<String, Double> dvec = tfidfVector(tfByVenue.get(v.id()), idf);
            sims.put(v.id(), cosine(qvec, dvec));
        }
        return v -> sims.getOrDefault(v.id(), 0.0);
    }

    private String docText(Venue v) {
        return (v.name() + " " + v.category() + " " + v.description() + " "
                + String.join(" ", v.dietaryTags())).toLowerCase();
    }

    private Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
            if (token.isBlank() || STOPWORDS.contains(token) || token.length() < 2) {
                continue;
            }
            tf.merge(token, 1, Integer::sum);
        }
        return tf;
    }

    private Map<String, Double> tfidfVector(Map<String, Integer> tf, Map<String, Double> idf) {
        Map<String, Double> vec = new HashMap<>();
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double weight = e.getValue() * idf.getOrDefault(e.getKey(), 0.0);
            if (weight > 0) {
                vec.put(e.getKey(), weight);
            }
        }
        return vec;
    }

    private double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Map<String, Double> smaller = a.size() < b.size() ? a : b;
        Map<String, Double> larger = smaller == a ? b : a;
        double dot = 0.0;
        for (Map.Entry<String, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        double normA = norm(a);
        double normB = norm(b);
        return (normA == 0 || normB == 0) ? 0.0 : dot / (normA * normB);
    }

    private double norm(Map<String, Double> vec) {
        double sum = 0.0;
        for (double w : vec.values()) {
            sum += w * w;
        }
        return Math.sqrt(sum);
    }

    /** Orders candidates by lexical relevance (used to assemble the grounding snippet list). */
    public List<Venue> orderByRelevance(List<Venue> venues, Scorer scorer) {
        List<Venue> copy = new ArrayList<>(venues);
        copy.sort((x, y) -> Double.compare(scorer.similarity(y), scorer.similarity(x)));
        return copy;
    }
}

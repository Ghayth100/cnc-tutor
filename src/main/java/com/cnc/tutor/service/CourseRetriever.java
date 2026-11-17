package com.cnc.tutor.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Construit le contexte « cours » envoyé au LLM.
 * <ul>
 *   <li>Si le cours complet tient dans {@code cours.max-context-chars} : il est envoyé en entier.</li>
 *   <li>Sinon (ex. quota gratuit Groq de 8 000 tokens/min) : on envoie le plan du cours
 *       + les pages les plus pertinentes pour la question (score TF-IDF simple,
 *       les codes G/M comptant beaucoup plus).</li>
 * </ul>
 */
@Service
public class CourseRetriever {

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final Pattern ISO_CODE = Pattern.compile("^[gm]\\d{1,3}$");
    private static final Set<String> STOPWORDS = Set.of(
            "le", "la", "les", "un", "une", "des", "de", "du", "et", "ou", "en", "au", "aux", "a", "est",
            "que", "qui", "quoi", "quel", "quelle", "quels", "quelles", "pour", "par", "sur", "dans", "avec",
            "ce", "cette", "ces", "se", "sa", "son", "ses", "il", "elle", "on", "je", "tu", "nous", "vous",
            "me", "moi", "explique", "expliquer", "donne", "donner", "c", "d", "l", "s", "n", "qu", "j",
            "entre", "difference", "comment", "pourquoi", "cours", "page", "pages", "exemple", "plus",
            "ne", "pas", "y", "mais", "si", "comme", "fait", "faire", "definition");

    private final CourseLoader courseLoader;
    private final int maxChars;

    private List<Map<String, Integer>> pageTerms;
    private Map<String, Double> idf;
    private String outline;

    public CourseRetriever(CourseLoader courseLoader,
                           @Value("${cours.max-context-chars:9000}") int maxChars) {
        this.courseLoader = courseLoader;
        this.maxChars = maxChars;
    }

    /** Texte du cours à placer dans le prompt système pour cette question. */
    public String contextFor(String query) {
        String full = courseLoader.getFullText();
        if (full.length() <= maxChars) {
            return full;
        }
        indexIfNeeded();

        List<Integer> ranked = rank(query);
        StringBuilder sb = new StringBuilder();
        sb.append("PLAN DU COURS (titre de chaque page) :\n").append(outline).append("\n\n");
        sb.append("PAGES DU COURS LES PLUS PERTINENTES POUR LA QUESTION (texte intégral) :\n\n");

        int budget = maxChars;
        Set<Integer> chosen = new TreeSet<>();
        for (int p : ranked) {
            int len = pageBlock(p).length();
            if (len > budget) {
                continue;
            }
            chosen.add(p);
            budget -= len;
        }
        if (chosen.isEmpty()) {
            sb.append("(Aucune page ne correspond directement à la question : ")
              .append("appuie-toi sur le plan ci-dessus pour proposer un sujet proche.)\n");
        }
        for (int p : chosen) {
            sb.append(pageBlock(p));
        }
        return sb.toString().strip();
    }

    /** Pages triées par pertinence décroissante (score > 0 uniquement). */
    List<Integer> rank(String query) {
        indexIfNeeded();
        List<String> terms = tokenize(query);
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < pageTerms.size(); i++) {
            Map<String, Integer> tf = pageTerms.get(i);
            double s = 0;
            for (String t : new HashSet<>(terms)) {
                Integer n = tf.get(t);
                if (n != null) {
                    double w = ISO_CODE.matcher(t).matches() ? 5.0 : 1.0;
                    s += w * (1 + Math.log(n)) * idf.getOrDefault(t, 0.0);
                }
            }
            if (s > 0) {
                scores.put(i + 1, s);
            }
        }
        List<Integer> pages = new ArrayList<>(scores.keySet());
        pages.sort(Comparator.comparingDouble((Integer p) -> scores.get(p)).reversed());
        return pages;
    }

    private String pageBlock(int p) {
        String text = courseLoader.getPage(p);
        return "[Page " + p + "]\n" + (text.isBlank() ? CourseLoader.FIGURE_ONLY : text) + "\n\n";
    }

    private synchronized void indexIfNeeded() {
        if (pageTerms != null) {
            return;
        }
        int n = courseLoader.getPageCount();
        List<Map<String, Integer>> terms = new ArrayList<>(n);
        Map<String, Integer> df = new HashMap<>();
        StringBuilder plan = new StringBuilder();
        String previousTitle = "";
        for (int p = 1; p <= n; p++) {
            String text = courseLoader.getPage(p);
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokenize(text)) {
                tf.merge(t, 1, Integer::sum);
            }
            terms.add(tf);
            tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));

            String title = text.isBlank() ? "(figures uniquement)" : firstLine(text);
            if (!title.equalsIgnoreCase(previousTitle)) {
                plan.append("p").append(p).append(" : ").append(title).append('\n');
            }
            previousTitle = title;
        }
        Map<String, Double> idfMap = new HashMap<>();
        df.forEach((t, d) -> idfMap.put(t, Math.log(1.0 + (double) n / d)));
        this.idf = idfMap;
        this.outline = plan.toString().strip();
        this.pageTerms = terms;
    }

    private static String firstLine(String text) {
        String line = text.strip().lines().findFirst().orElse("").replaceAll("^[•\\-\\s]+", "");
        return line.length() > 60 ? line.substring(0, 60) + "…" : line;
    }

    /** Minuscules, sans accents, mots ≥ 2 lettres hors mots vides ; « G 83 » / « G083 » → « g83 ». */
    static List<String> tokenize(String text) {
        String norm = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\b([gm])\\s?0*(\\d{1,3})\\b", "$1$2");
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(norm);
        while (m.find()) {
            String w = m.group();
            if ((w.length() >= 2 || ISO_CODE.matcher(w).matches()) && !STOPWORDS.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }
}

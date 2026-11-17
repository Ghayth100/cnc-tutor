package com.cnc.tutor.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Charge le cours PDF au démarrage et garde son texte en mémoire,
 * page par page, avec des marqueurs « [Page N] ».
 */
@Service
public class CourseLoader {

    private static final Logger log = LoggerFactory.getLogger(CourseLoader.class);
    static final String FIGURE_ONLY = "(Cette page ne contient que des figures ou schémas, sans texte exploitable.)";

    private final String pdfPath;
    private byte[] pdfBytes;
    private List<String> pages = List.of();
    private String fullText = "";

    public CourseLoader(@Value("${cours.pdf-path:cours/PROGRAMMATION_CNC.pdf}") String pdfPath) {
        this.pdfPath = pdfPath;
    }

    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(pdfPath);
        if (!resource.exists()) {
            throw new IllegalStateException("Cours introuvable dans le classpath : " + pdfPath);
        }
        try (InputStream in = resource.getInputStream()) {
            pdfBytes = in.readAllBytes();
        }

        List<String> extracted = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                extracted.add(clean(stripper.getText(doc)));
            }
        }
        pages = Collections.unmodifiableList(extracted);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            String text = pages.get(i);
            sb.append("[Page ").append(i + 1).append("]\n")
              .append(text.isBlank() ? FIGURE_ONLY : text)
              .append("\n\n");
        }
        fullText = sb.toString().strip();
        log.info("Cours chargé : {} pages, {} caractères", pages.size(), fullText.length());
    }

    /** Normalise les espaces pour réduire la taille envoyée au LLM. */
    static String clean(String raw) {
        return raw.replace(' ', ' ')
                  .replaceAll("[\\uF000-\\uF0FF]", "•") // puces Wingdings/Symbol
                  .replaceAll("(?m)^•\\s*$\\R?", "")      // puces orphelines
                  .replaceAll("[ \\t]+", " ")
                  .replaceAll(" *\\R *", "\n")
                  .replaceAll("\\n{3,}", "\n\n")
                  .strip();
    }

    public int getPageCount() {
        return pages.size();
    }

    /** Texte brut d'une page (1-indexé). */
    public String getPage(int n) {
        return pages.get(n - 1);
    }

    /** Texte complet du cours avec marqueurs [Page N]. */
    public String getFullText() {
        return fullText;
    }

    /** Octets du PDF original (pour le rendu des pages en image). */
    public byte[] getPdfBytes() {
        return pdfBytes;
    }
}

package com.cnc.tutor.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PreDestroy;

/** Rend une page du cours en PNG (PDFRenderer, 120 dpi) avec cache mémoire. */
@Service
public class PageImageService {

    private final CourseLoader courseLoader;
    private final float dpi;
    private final Map<Integer, byte[]> cache = new ConcurrentHashMap<>();
    private PDDocument document;
    private PDFRenderer renderer;

    public PageImageService(CourseLoader courseLoader, @Value("${cours.render-dpi:120}") float dpi) {
        this.courseLoader = courseLoader;
        this.dpi = dpi;
    }

    public byte[] getPagePng(int pageNumber) {
        if (pageNumber < 1 || pageNumber > courseLoader.getPageCount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Page " + pageNumber + " inexistante (le cours a " + courseLoader.getPageCount() + " pages).");
        }
        return cache.computeIfAbsent(pageNumber, this::render);
    }

    /** PDFRenderer n'est pas thread-safe : rendu sérialisé. */
    private synchronized byte[] render(int pageNumber) {
        try {
            if (renderer == null) {
                document = Loader.loadPDF(courseLoader.getPdfBytes());
                renderer = new PDFRenderer(document);
            }
            BufferedImage img = renderer.renderImageWithDPI(pageNumber - 1, dpi, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Rendu impossible de la page " + pageNumber, e);
        }
    }

    @PreDestroy
    public synchronized void close() throws IOException {
        if (document != null) {
            document.close();
        }
    }
}

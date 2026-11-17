package com.cnc.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CourseLoaderTest {

    private static CourseLoader loader;

    @BeforeAll
    static void load() throws Exception {
        loader = new CourseLoader("cours/PROGRAMMATION_CNC.pdf");
        loader.load();
    }

    @Test
    void leCoursContientDesPages() {
        assertThat(loader.getPageCount()).isGreaterThan(0);
        assertThat(loader.getPdfBytes()).isNotEmpty();
    }

    @Test
    void chaquePageAUnMarqueur() {
        String text = loader.getFullText();
        assertThat(text).startsWith("[Page 1]");
        for (int p = 1; p <= loader.getPageCount(); p++) {
            assertThat(text).contains("[Page " + p + "]\n");
        }
        assertThat(text).doesNotContain("[Page " + (loader.getPageCount() + 1) + "]");
    }

    @Test
    void leTexteContientLesNotionsDuCours() {
        String text = loader.getFullText();
        assertThat(text).contains("G83", "G71", "G41");
        assertThat(text.length()).isGreaterThan(10_000);
    }

    @Test
    void lesPagesSansTexteSontSignalees() {
        for (int p = 1; p <= loader.getPageCount(); p++) {
            if (loader.getPage(p).isBlank()) {
                assertThat(loader.getFullText()).contains("[Page " + p + "]\n" + CourseLoader.FIGURE_ONLY);
            }
        }
    }
}

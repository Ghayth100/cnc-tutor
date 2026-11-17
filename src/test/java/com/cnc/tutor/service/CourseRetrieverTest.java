package com.cnc.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CourseRetrieverTest {

    private static CourseLoader loader;

    @BeforeAll
    static void load() throws Exception {
        loader = new CourseLoader("cours/PROGRAMMATION_CNC.pdf");
        loader.load();
    }

    @Test
    void coursCompletSiLeBudgetLePermet() {
        CourseRetriever r = new CourseRetriever(loader, 1_000_000);
        assertThat(r.contextFor("G83")).isEqualTo(loader.getFullText());
    }

    @Test
    void sinonPlanPlusPagesPertinentesDansLeBudget() {
        CourseRetriever r = new CourseRetriever(loader, 9000);
        String ctx = r.contextFor("Explique le cycle G83");
        assertThat(ctx).contains("PLAN DU COURS").contains("G83");
        assertThat(ctx.length()).isLessThan(9000 + 6000); // budget pages + plan
    }

    @Test
    void laPremierePageClasseeContientLeCodeDemande() {
        CourseRetriever r = new CourseRetriever(loader, 9000);
        for (String code : List.of("G83", "G71", "G41")) {
            List<Integer> ranked = r.rank("Explique " + code);
            assertThat(ranked).isNotEmpty();
            assertThat(loader.getPage(ranked.getFirst())).contains(code);
        }
    }

    @Test
    void normaliseLesCodesIsoEtLesAccents() {
        assertThat(CourseRetriever.tokenize("Différence entre G02 et G 03, cycle M03"))
                .containsExactly("g2", "g3", "cycle", "m3");
        assertThat(CourseRetriever.tokenize("Opération d'usinage")).contains("operation", "usinage");
    }
}

package com.smartkb.agent.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectIntakeTextExtractorTest {

    private final ProjectIntakeTextExtractor extractor = new ProjectIntakeTextExtractor();

    @Test
    void shouldExtractSectionUntilNextLevelTwoHeading() {
        String content = """
                # HANDOFF

                ## Current Goal

                Ship the local demo.

                ### Detail
                Keep the scope small.

                ## Next Step

                Run smoke tests.
                """;

        String section = extractor.section(content, "## Current Goal");

        assertTrue(section.contains("Ship the local demo."));
        assertTrue(section.contains("Keep the scope small."));
        assertTrue(section.endsWith("Keep the scope small."));
    }

    @Test
    void shouldExtractParagraphLinesAndLists() {
        String text = """
                First paragraph
                continues here.

                - completed item
                1. numbered item
                2. second numbered item
                """;

        assertEquals("First paragraph continues here.", extractor.firstParagraph(text));
        assertEquals("First paragraph", extractor.firstMeaningfulLine("# Notes\n\nFirst paragraph"));
        assertEquals(List.of("completed item"), extractor.bullets(text));
        assertEquals(
                List.of("completed item", "numbered item", "second numbered item"),
                extractor.numberedOrBullets(text)
        );
    }

    @Test
    void shouldExtractCheckedAndUncheckedItems() {
        String text = """
                - [x] Docker Compose verified
                - [ ] k6 benchmark
                - [x] Grafana dashboard
                - plain bullet
                """;

        assertEquals(
                List.of("Docker Compose verified", "Grafana dashboard"),
                extractor.checkedItems(text, true)
        );
        assertEquals(List.of("k6 benchmark"), extractor.checkedItems(text, false));
    }

    @Test
    void shouldMergeDeduplicateAndLimitLists() {
        List<String> merged = extractor.merge(
                List.of("Redis", "Docker"),
                List.of("Docker", "k6"),
                List.of()
        );

        assertEquals(List.of("Redis", "Docker", "k6"), merged);
        assertEquals(List.of("Redis", "Docker"), extractor.limit(merged, 2));
        assertEquals("Redis", extractor.firstItem(merged));
        assertEquals(List.of("fallback"), extractor.firstNonEmpty(List.of(), List.of("fallback")));
        assertEquals("goal", extractor.firstNonBlank(" ", "goal", "other"));
    }
}

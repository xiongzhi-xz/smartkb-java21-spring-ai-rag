package com.smartkb.agent.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Small Markdown-oriented extraction helpers for deterministic intake.
 */
class ProjectIntakeTextExtractor {

    String section(String content, String heading) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int start = content.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int bodyStart = content.indexOf('\n', start);
        if (bodyStart < 0) {
            return "";
        }
        int nextHeading = content.indexOf("\n## ", bodyStart + 1);
        if (nextHeading < 0) {
            nextHeading = content.length();
        }
        return content.substring(bodyStart + 1, nextHeading).trim();
    }

    String firstParagraph(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String normalized = paragraph.strip();
            if (!normalized.isBlank() && !normalized.startsWith("-")) {
                return normalized.replaceAll("\\R+", " ");
            }
        }
        return "";
    }

    String firstMeaningfulLine(String text) {
        if (text == null) {
            return "";
        }
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .findFirst()
                .orElse("");
    }

    List<String> bullets(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return text.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2).strip())
                .filter(line -> !line.isBlank())
                .toList();
    }

    List<String> numberedOrBullets(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return text.lines()
                .map(String::strip)
                .filter(line -> line.matches("^[0-9]+\\.\\s+.+") || line.startsWith("- "))
                .map(line -> line.replaceFirst("^[0-9]+\\.\\s+", "").replaceFirst("^-\\s+", "").strip())
                .filter(line -> !line.isBlank())
                .toList();
    }

    List<String> labeledBullets(String text, String label) {
        if (text == null || text.isBlank() || label == null || label.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        boolean reading = false;
        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.strip();
            if (!reading) {
                reading = line.equalsIgnoreCase(label.strip());
                continue;
            }
            if (line.isBlank()) {
                if (!values.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!line.startsWith("- ") && line.endsWith(":")) {
                break;
            }
            if (line.startsWith("- ")) {
                values.add(line.substring(2).strip());
            } else if (values.isEmpty()) {
                values.add(line);
            } else {
                break;
            }
        }
        return values;
    }

    List<String> checkedItems(String text, boolean checked) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String marker = checked ? "- [x]" : "- [ ]";
        return text.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(marker))
                .map(line -> line.substring(marker.length()).strip())
                .filter(line -> !line.isBlank())
                .toList();
    }

    String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    @SafeVarargs
    final List<String> firstNonEmpty(List<String>... lists) {
        for (List<String> list : lists) {
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        return List.of();
    }

    @SafeVarargs
    final List<String> merge(List<String>... lists) {
        Set<String> merged = new LinkedHashSet<>();
        for (List<String> list : lists) {
            if (list != null) {
                merged.addAll(list);
            }
        }
        return new ArrayList<>(merged);
    }

    List<String> limit(List<String> values, int limit) {
        if (values == null || values.size() <= limit) {
            return values == null ? List.of() : values;
        }
        return values.subList(0, limit);
    }

    String firstItem(List<String> values) {
        return values == null || values.isEmpty() ? "" : values.get(0);
    }
}

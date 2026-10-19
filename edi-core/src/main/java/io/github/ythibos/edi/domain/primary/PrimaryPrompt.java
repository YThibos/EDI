package io.github.ythibos.edi.domain.primary;

import java.util.ArrayList;
import java.util.List;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.memory.MemoryFile;

public final class PrimaryPrompt {

    public static final int ANCHOR_BUDGET_BYTES = 10_000;
    private static final String MEMORY_HEADING = "# The captain's memory, loaded at session start";

    private PrimaryPrompt() {
    }

    public static String composed(String anchor, String personalAddendum, List<MemoryFile> memory) {
        requireTheAnchorWithinItsBudget(anchor, personalAddendum);
        List<String> parts = new ArrayList<>(List.of(anchor.strip()));
        if (!personalAddendum.isBlank()) {
            parts.add(personalAddendum.strip());
        }
        List<MemoryFile> loaded = memory.stream().filter(file -> !file.text().isBlank()).toList();
        if (!loaded.isEmpty()) {
            parts.add(MEMORY_HEADING);
            loaded.forEach(file -> parts.add("<!-- memory/" + file.name() + " -->\n" + file.text().strip()));
        }
        return String.join("\n\n", parts) + "\n";
    }

    private static void requireTheAnchorWithinItsBudget(String anchor, String personalAddendum) {
        int bytes = utf8Bytes(anchor) + utf8Bytes(personalAddendum);
        if (bytes > ANCHOR_BUDGET_BYTES) {
            throw new Refusal("The primary's anchor and its personal addendum hold " + bytes + " bytes, above the " + ANCHOR_BUDGET_BYTES
                    + " the anchor may load (I-35); shorten personal/anchor.local.md.");
        }
    }

    private static int utf8Bytes(String text) {
        return text.codePoints().map(PrimaryPrompt::utf8BytesOf).sum();
    }

    private static int utf8BytesOf(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}

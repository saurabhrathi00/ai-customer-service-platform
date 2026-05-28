package com.aiassistant.callorchestration.transcription;

/**
 * Converts Devanagari text to natural Hinglish (Latin script) romanisation.
 * Only Devanagari code-points are transliterated; Latin characters, digits,
 * and punctuation pass through unchanged — so mixed-script STT output
 * ("hello मैं ठीक हूं") becomes "hello main theek hoon" in one pass.
 */
public final class DevanagariTransliterator {

    private DevanagariTransliterator() {}

    // ---- Devanagari Unicode block: U+0900 – U+097F ----

    private static final char VIRAMA   = '्'; // ्  (halant — kills inherent 'a')
    private static final char NUKTA    = '़'; // ़  (dot below — ज़ = za, फ़ = fa …)
    private static final char ANUSVARA = 'ं'; // ं
    private static final char CHANDRABINDU = 'ँ'; // ँ
    private static final char VISARGA  = 'ः'; // ः

    /** Independent vowels (अ–औ, plus ऑ for English-loan "o"). */
    private static String independentVowel(char ch) {
        return switch (ch) {
            case 'अ' -> "a";
            case 'आ' -> "aa";
            case 'इ' -> "i";
            case 'ई' -> "ee";
            case 'उ' -> "u";
            case 'ऊ' -> "oo";
            case 'ऋ' -> "ri";
            case 'ए' -> "e";
            case 'ऐ' -> "ai";
            case 'ओ' -> "o";
            case 'औ' -> "au";
            case 'ऑ' -> "o";   // ऑ (candra o)
            case 'ऌ' -> "lri"; // ऌ (rare)
            default -> null;
        };
    }

    /** Dependent vowel signs (matras). Returns null if ch is not a matra. */
    private static String matra(char ch) {
        return switch (ch) {
            case 'ा' -> "a";   // ा
            case 'ि' -> "i";   // ि
            case 'ी' -> "ee";  // ी
            case 'ु' -> "u";   // ु
            case 'ू' -> "oo";  // ू
            case 'ृ' -> "ri";  // ृ
            case 'े' -> "e";   // े
            case 'ै' -> "ai";  // ै
            case 'ो' -> "o";   // ो
            case 'ौ' -> "au";  // ौ
            case 'ॉ' -> "o";   // ॉ (candra o matra)
            default -> null;
        };
    }

    /** Base consonant → Latin (without inherent 'a'). */
    private static String consonant(char ch) {
        return switch (ch) {
            case 'क' -> "k";
            case 'ख' -> "kh";
            case 'ग' -> "g";
            case 'घ' -> "gh";
            case 'ङ' -> "ng";
            case 'च' -> "ch";
            case 'छ' -> "chh";
            case 'ज' -> "j";
            case 'झ' -> "jh";
            case 'ञ' -> "ny";
            case 'ट' -> "t";
            case 'ठ' -> "th";
            case 'ड' -> "d";
            case 'ढ' -> "dh";
            case 'ण' -> "n";
            case 'त' -> "t";
            case 'थ' -> "th";
            case 'द' -> "d";
            case 'ध' -> "dh";
            case 'न' -> "n";
            case 'प' -> "p";
            case 'फ' -> "ph";
            case 'ब' -> "b";
            case 'भ' -> "bh";
            case 'म' -> "m";
            case 'य' -> "y";
            case 'र' -> "r";
            case 'ऱ' -> "r";   // ऱ (eyelash ra — treat as r)
            case 'ल' -> "l";
            case 'ळ' -> "l";   // ळ
            case 'व' -> "v";
            case 'श' -> "sh";
            case 'ष' -> "sh";
            case 'स' -> "s";
            case 'ह' -> "h";
            default -> null;
        };
    }

    /** Nukta-modified consonants (ज़ → z, फ़ → f, etc.). */
    private static String nuktaConsonant(char ch) {
        return switch (ch) {
            case 'ज' -> "z";   // ज़
            case 'फ' -> "f";   // फ़
            case 'ख' -> "kh";  // ख़ (keep kh — most Indians write "kh")
            case 'ग' -> "gh";  // ग़
            case 'ड' -> "d";   // ड़
            case 'ढ' -> "dh";  // ढ़
            default -> null;
        };
    }

    private static boolean isDevanagari(char ch) {
        return ch >= 'ऀ' && ch <= 'ॿ';
    }

    private static boolean isConsonant(char ch) {
        return consonant(ch) != null;
    }

    private static boolean isMatra(char ch) {
        return matra(ch) != null;
    }

    /**
     * Transliterate any Devanagari in {@code input} to natural Latin script.
     * Non-Devanagari characters pass through unchanged.
     */
    public static String transliterate(String input) {
        if (input == null || input.isEmpty()) return input;

        boolean hasDevanagari = false;
        for (int i = 0; i < input.length(); i++) {
            if (isDevanagari(input.charAt(i))) { hasDevanagari = true; break; }
        }
        if (!hasDevanagari) return input;

        StringBuilder out = new StringBuilder(input.length() * 2);
        int len = input.length();

        for (int i = 0; i < len; i++) {
            char ch = input.charAt(i);

            // --- Non-Devanagari: pass through ---
            if (!isDevanagari(ch)) {
                out.append(ch);
                continue;
            }

            // --- Anusvara / Chandrabindu / Visarga ---
            if (ch == ANUSVARA)      { out.append('n'); continue; }
            if (ch == CHANDRABINDU)  { out.append('n'); continue; }
            if (ch == VISARGA)       { out.append('h'); continue; }

            // --- Independent vowel ---
            String iv = independentVowel(ch);
            if (iv != null) {
                out.append(iv);
                continue;
            }

            // --- Consonant ---
            String c = consonant(ch);
            if (c != null) {
                // Check for nukta immediately after → use nukta mapping
                if (i + 1 < len && input.charAt(i + 1) == NUKTA) {
                    String nc = nuktaConsonant(ch);
                    if (nc != null) c = nc;
                    i++; // consume nukta
                }

                out.append(c);

                // What follows the consonant determines the vowel:
                if (i + 1 < len) {
                    char next = input.charAt(i + 1);
                    if (next == VIRAMA) {
                        // Halant: no inherent 'a'. Consume it.
                        i++;
                    } else if (isMatra(next)) {
                        // Explicit vowel sign — use it instead of 'a'.
                        out.append(matra(next));
                        i++;
                    } else if (isConsonant(next) || independentVowel(next) != null
                            || next == ANUSVARA || next == CHANDRABINDU || next == VISARGA) {
                        // Another Devanagari follows — append inherent 'a'
                        out.append('a');
                    } else if (isDevanagari(next)) {
                        // Some other Devanagari mark — append 'a' to be safe
                        out.append('a');
                    } else {
                        // Non-Devanagari follows (space, punctuation, Latin) —
                        // word boundary, append inherent 'a'
                        out.append('a');
                    }
                } else {
                    // End of string — append inherent 'a'
                    out.append('a');
                }
                continue;
            }

            // --- Virama / Nukta / Matra appearing without a preceding consonant ---
            if (ch == VIRAMA || ch == NUKTA) continue; // orphaned; skip
            String m = matra(ch);
            if (m != null) { out.append(m); continue; }

            // --- Devanagari digits ---
            if (ch >= '०' && ch <= '९') {
                out.append((char) ('0' + (ch - '०')));
                continue;
            }

            // --- Danda / double danda → period ---
            if (ch == '।' || ch == '॥') { out.append('.'); continue; }

            // Fallback: emit the character as-is
            out.append(ch);
        }

        return out.toString();
    }
}

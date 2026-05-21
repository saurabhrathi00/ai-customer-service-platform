package com.aiassistant.callorchestration.transcription;

/**
 * A single transcript update from the STT provider.
 *
 * @param text       the recognised text (interim or final)
 * @param isFinal    true if the provider considers this segment finalised
 * @param confidence 0.0–1.0 if reported by the provider, else null
 */
public record TranscriptEvent(String text, boolean isFinal, Double confidence) {}
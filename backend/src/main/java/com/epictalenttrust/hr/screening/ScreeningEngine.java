package com.epictalenttrust.hr.screening;

/**
 * Scores a CV against a job's criteria. Implementations must be explainable: every point needs
 * a CV line behind it. A language-model engine can be added behind this interface later.
 */
public interface ScreeningEngine {

    String modelVersion();

    ScreeningOutcome screen(ScreeningInput input);
}

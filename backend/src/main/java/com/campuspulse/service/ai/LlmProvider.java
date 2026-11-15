package com.campuspulse.service.ai;

/**
 * One OpenAI-compatible chat API (Llama, Gemini, Groq…). A provider with no key is skipped.
 * {@code visionModel} is used for page images; blank means this provider can't read images.
 */
public record LlmProvider(String name, String url, String key, String model, String visionModel) {

    public LlmProvider {
        name = clean(name);
        url = clean(url);
        key = clean(key);
        model = clean(model);
        visionModel = clean(visionModel);
    }

    /** URL, model and a usable key are set. */
    public boolean configured() {
        return !url.isEmpty() && !model.isEmpty() && !key.isEmpty()
                && key.chars().noneMatch(Character::isISOControl) && !key.contains(" ");
    }

    public boolean supportsVision() {
        return configured() && !visionModel.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        return name + "(" + model + ")"; // never print the key
    }
}

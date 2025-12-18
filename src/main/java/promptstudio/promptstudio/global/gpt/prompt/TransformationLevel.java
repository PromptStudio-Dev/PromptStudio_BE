package promptstudio.promptstudio.global.gpt.prompt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransformationLevel {

    NONE("realistic", "No modifications to style."),
    LIGHT("light", "No photorealistic details."),
    HEAVY("heavy", "No realistic anatomy, no human proportions, no detailed facial features.");

    private final String level;
    private final String prohibition;

    public static TransformationLevel fromStyle(String style) {
        if (style == null) return NONE;

        return switch (style.toLowerCase()) {
            case "animal_crossing", "chibi_game" -> HEAVY;
            case "ghibli", "anime_film", "pixar", "disney", "cgi_animation", "cgi_animation_soft" -> LIGHT;
            default -> NONE;
        };
    }
}
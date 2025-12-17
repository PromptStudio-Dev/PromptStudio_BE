package promptstudio.promptstudio.global.gpt.prompt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StyleMode {

    REALISTIC("realistic", "Photorealistic style"),
    GHIBLI("ghibli", "Studio Ghibli 2D anime style"),
    ANIMAL_CROSSING("animal_crossing", "Animal Crossing chibi style"),
    PIXAR("pixar", "Pixar 3D animation style"),
    DISNEY("disney", "Disney 2D animation style"),
    ANIME("anime", "Japanese anime style"),
    UNKNOWN("unknown", "Custom style");

    private final String key;
    private final String description;

    public static StyleMode fromString(String style) {
        if (style == null) return REALISTIC;

        return switch (style.toLowerCase()) {
            case "ghibli" -> GHIBLI;
            case "animal_crossing" -> ANIMAL_CROSSING;
            case "pixar" -> PIXAR;
            case "disney" -> DISNEY;
            case "anime" -> ANIME;
            default -> UNKNOWN;
        };
    }
}
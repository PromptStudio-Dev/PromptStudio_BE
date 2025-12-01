package promptstudio.promptstudio.global.gpt.prompt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FewshotStyle {

    GHIBLI("prompts/fewshot/ghibli.txt"),
    ANIMAL_CROSSING("prompts/fewshot/animal-crossing.txt"),
    PIXAR("prompts/fewshot/pixar.txt");

    private final String path;

    public static FewshotStyle fromStyleName(String styleName) {
        return switch (styleName.toLowerCase()) {
            case "ghibli" -> GHIBLI;
            case "animal_crossing" -> ANIMAL_CROSSING;
            case "pixar" -> PIXAR;
            default -> null;
        };
    }
}
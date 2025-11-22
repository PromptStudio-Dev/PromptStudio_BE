package promptstudio.promptstudio.domain.prompt.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromptUpdateRequest {
    private String title;
    private String introduction;
    private String content;
    private String category;
    private Boolean visible;
    private String imageUrl;
    private String result;
    private Boolean imageRequired;
    private String aiEnvironment;
}

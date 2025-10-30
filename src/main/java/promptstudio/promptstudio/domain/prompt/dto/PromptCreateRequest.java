package promptstudio.promptstudio.domain.prompt.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromptCreateRequest {
    private String title;
    private String introduction;
    private String content;
    private boolean visible;
    private String imageUrl;
    private String result;
    private boolean imageRequired;
    private String aiEnvironment;
}

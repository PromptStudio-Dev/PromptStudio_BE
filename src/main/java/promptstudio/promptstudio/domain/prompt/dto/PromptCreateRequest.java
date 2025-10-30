package promptstudio.promptstudio.domain.prompt.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PromptCreateRequest {
    private String title;
    private String introduction;
    private String content;
    private String category;
    private boolean visible;
    private String result;
    private boolean imageRequired;
    private String aiEnvironment;
}

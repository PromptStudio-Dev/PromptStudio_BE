package promptstudio.promptstudio.domain.promptplaceholder.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PromptPlaceholderResponse {
    private Long promptId;
    private String content;
    private boolean imageRequired;
    private boolean placeholderRequired;
    private List<String> placeholders;
}

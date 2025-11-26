package promptstudio.promptstudio.domain.prompt.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromptCopyResponse {
    private Long promptId;
    private long copyCount;
    private String content;
}

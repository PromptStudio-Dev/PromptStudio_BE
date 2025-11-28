package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PromptFeedbackResponse {
    private String feedback;
}
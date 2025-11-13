package promptstudio.promptstudio.domain.history.dto;

import lombok.Builder;
import lombok.Getter;
import promptstudio.promptstudio.domain.history.domain.entity.ResultType;

@Getter
@Builder
public class GptRunResult {
    private ResultType resultType;
    private String resultText;
    private String resultImageUrl;
}
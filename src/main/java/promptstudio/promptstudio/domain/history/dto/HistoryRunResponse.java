package promptstudio.promptstudio.domain.history.dto;

import lombok.Builder;
import lombok.Getter;
import promptstudio.promptstudio.domain.history.domain.entity.History;
import promptstudio.promptstudio.domain.history.domain.entity.ResultType;

import java.time.LocalDateTime;

@Getter
@Builder
public class HistoryRunResponse {
    private Long historyId;
    private ResultType resultType;
    private String resultText;
    private String resultImageUrl;
    private LocalDateTime createdAt;

    public static HistoryRunResponse from(History history) {
        return HistoryRunResponse.builder()
                .historyId(history.getId())
                .resultType(history.getResultType())
                .resultText(history.getResultText())
                .resultImageUrl(history.getResultImageUrl())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
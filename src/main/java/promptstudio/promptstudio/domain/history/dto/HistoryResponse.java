package promptstudio.promptstudio.domain.history.dto;

import lombok.Builder;
import lombok.Getter;
import promptstudio.promptstudio.domain.history.domain.entity.History;

@Getter
@Builder
public class HistoryResponse {
    private Long historyId;
    private String title;

    public static HistoryResponse from(History history) {
        return HistoryResponse.builder()
                .historyId(history.getId())
                .title(history.getTitle())
                .build();
    }
}
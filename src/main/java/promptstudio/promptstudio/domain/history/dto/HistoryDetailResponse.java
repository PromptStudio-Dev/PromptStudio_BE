package promptstudio.promptstudio.domain.history.dto;

import lombok.Builder;
import lombok.Getter;
import promptstudio.promptstudio.domain.history.domain.entity.History;
import promptstudio.promptstudio.domain.history.domain.entity.ResultType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class HistoryDetailResponse {
    private Long historyId;
    private String snapshotTitle;
    private String snapshotContent;
    private List<HistorySnapshotImageResponse> snapshotImages;
    private ResultType resultType;
    private String resultText;
    private String resultImageUrl;
    private LocalDateTime createdAt;

    public static HistoryDetailResponse from(History history) {
        return HistoryDetailResponse.builder()
                .historyId(history.getId())
                .snapshotTitle(history.getSnapshotTitle())
                .snapshotContent(history.getSnapshotContent())
                .snapshotImages(history.getSnapshotImages().stream()
                        .map(HistorySnapshotImageResponse::from)
                        .toList())
                .resultType(history.getResultType())
                .resultText(history.getResultText())
                .resultImageUrl(history.getResultImageUrl())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
package promptstudio.promptstudio.domain.history.dto;

import lombok.Builder;
import lombok.Getter;
import promptstudio.promptstudio.domain.history.domain.entity.HistorySnapshotImage;

@Getter
@Builder
public class HistorySnapshotImageResponse {
    private String imageUrl;
    private Integer orderIndex;

    public static HistorySnapshotImageResponse from(HistorySnapshotImage image) {
        return HistorySnapshotImageResponse.builder()
                .imageUrl(image.getImageUrl())
                .orderIndex(image.getOrderIndex())
                .build();
    }
}
package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class MakerDetailResponse {
    private Long makerId;
    private String title;
    private String content;
    private List<MakerImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MakerDetailResponse from(Maker maker) {
        return MakerDetailResponse.builder()
                .makerId(maker.getId())
                .title(maker.getTitle())
                .content(maker.getContent())
                .images(maker.getImages().stream()
                        .map(MakerImageResponse::from)
                        .collect(Collectors.toList()))
                .createdAt(maker.getCreatedAt())
                .updatedAt(maker.getUpdatedAt())
                .build();
    }
}
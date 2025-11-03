package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;
import promptstudio.promptstudio.domain.maker.domain.entity.MakerImage;

@Getter
@Builder
public class MakerImageResponse {
    private Long imageId;
    private String imageUrl;
    private Integer orderIndex;

    public static MakerImageResponse from(MakerImage makerImage) {
        return MakerImageResponse.builder()
                .imageId(makerImage.getId())
                .imageUrl(makerImage.getImageUrl())
                .orderIndex(makerImage.getOrderIndex())
                .build();
    }
}
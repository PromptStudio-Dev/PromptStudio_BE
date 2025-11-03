package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MakerCreateResponse {
    private Long makerId;
}
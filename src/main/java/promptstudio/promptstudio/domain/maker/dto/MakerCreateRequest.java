package promptstudio.promptstudio.domain.maker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MakerCreateRequest {
    private String title;
    private String content;
}
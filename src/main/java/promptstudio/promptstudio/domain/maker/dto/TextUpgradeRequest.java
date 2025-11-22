package promptstudio.promptstudio.domain.maker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TextUpgradeRequest {
    private String fullText;
    private String selectedText;
    private String direction;
}
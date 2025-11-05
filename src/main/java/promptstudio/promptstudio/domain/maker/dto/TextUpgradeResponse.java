package promptstudio.promptstudio.domain.maker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TextUpgradeResponse {
    private String originalText;
    private String upgradedText;
    private String direction;
}
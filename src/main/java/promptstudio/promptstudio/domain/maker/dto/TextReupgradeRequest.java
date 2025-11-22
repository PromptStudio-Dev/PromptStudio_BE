package promptstudio.promptstudio.domain.maker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextReupgradeRequest {
    private String fullText;
    private String selectedText;
    private String prevDirection;
    private String prevResult;
    private String direction;
}

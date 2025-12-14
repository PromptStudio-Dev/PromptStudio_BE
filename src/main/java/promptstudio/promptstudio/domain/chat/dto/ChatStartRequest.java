package promptstudio.promptstudio.domain.chat.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ChatStartRequest {
    private Long promptId;
    private Map<String, String> placeholderValues;
    private String message;
    private List<String> images;
}
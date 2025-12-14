package promptstudio.promptstudio.domain.chat.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
public class ChatSendRequest {
    private String sessionId;
    private String message;
    private List<MultipartFile> images;
}
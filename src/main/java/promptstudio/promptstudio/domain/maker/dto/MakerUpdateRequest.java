package promptstudio.promptstudio.domain.maker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class MakerUpdateRequest {
    private String title;
    private String content;
    private List<String> existingImageUrls = new ArrayList<>(); // 기존에 유지할 이미지 URL들
}
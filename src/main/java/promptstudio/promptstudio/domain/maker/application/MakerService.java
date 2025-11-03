package promptstudio.promptstudio.domain.maker.application;

import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.maker.dto.MakerCreateRequest;
import promptstudio.promptstudio.domain.maker.dto.MakerUpdateRequest;
import promptstudio.promptstudio.domain.maker.dto.MakerUpdateResponse;

import java.util.List;

public interface MakerService {
    Long createMaker(Long memberId, MakerCreateRequest request);

    MakerUpdateResponse updateMaker(Long makerId, MakerUpdateRequest request, List<MultipartFile> newImages);
}
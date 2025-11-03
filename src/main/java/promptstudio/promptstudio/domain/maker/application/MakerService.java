package promptstudio.promptstudio.domain.maker.application;

import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.maker.dto.*;

import java.util.List;

public interface MakerService {
    Long createMaker(Long memberId, MakerCreateRequest request);

    MakerUpdateResponse updateMaker(Long makerId, MakerUpdateRequest request, List<MultipartFile> newImages);

    MakerDetailResponse getMakerDetail(Long makerId);

    TextUpgradeResponse upgradeText(Long makerId, TextUpgradeRequest request);
}
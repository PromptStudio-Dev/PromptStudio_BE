package promptstudio.promptstudio.domain.maker.application;

import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.maker.dto.*;

import java.util.List;

public interface MakerService {
    Long createMaker(Long memberId);

    MakerUpdateResponse updateMaker(Long makerId, MakerUpdateRequest request, List<MultipartFile> newImages);

    MakerDetailResponse getMakerDetail(Long makerId);

    TextUpgradeResponse upgradeText(TextUpgradeRequest request);

    TextUpgradeResponse reupgradeText(TextReupgradeRequest request);
}
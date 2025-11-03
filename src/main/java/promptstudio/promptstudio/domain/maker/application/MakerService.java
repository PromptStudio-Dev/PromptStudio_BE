package promptstudio.promptstudio.domain.maker.application;

import promptstudio.promptstudio.domain.maker.dto.MakerCreateRequest;

public interface MakerService {
    Long createMaker(Long memberId, MakerCreateRequest request);
}
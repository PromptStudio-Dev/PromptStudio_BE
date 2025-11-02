package promptstudio.promptstudio.domain.likes.application;

import promptstudio.promptstudio.domain.likes.dto.LikesToggleResponse;

public interface LikesService {
    LikesToggleResponse toggleLikes(Long memberId, Long promptId);
}

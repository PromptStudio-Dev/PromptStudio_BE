package promptstudio.promptstudio.domain.likes.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import promptstudio.promptstudio.domain.likes.application.LikesService;
import promptstudio.promptstudio.domain.likes.dto.LikesToggleResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "좋아요 API", description = "좋아요 API입니다.")
public class LikesController {

    private final LikesService likesService;

    @PostMapping("/prompts/{promptId}/likes")
    @Operation(summary = "좋아요 토글", description = "좋아요 토글 API")
    public ResponseEntity<LikesToggleResponse> toggleHeart(
            @AuthenticationPrincipal Long memberId,
            @PathVariable("promptId") Long promptId) {
        LikesToggleResponse response = likesService.toggleLikes(memberId, promptId);
        return ResponseEntity.ok(response);
    }
}
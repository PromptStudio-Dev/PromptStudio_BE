package promptstudio.promptstudio.domain.promptplaceholder.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import promptstudio.promptstudio.domain.promptplaceholder.application.PromptPlaceholderService;
import promptstudio.promptstudio.domain.promptplaceholder.dto.PromptPlaceholderResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "프롬프트 플레이스홀더 API", description = "프롬프트 플레이스홀더 API입니다.")
public class PromptPlaceholderController {

    private final PromptPlaceholderService promptPlaceholderService;

    @GetMapping("/prompts/{prompt_id}/placeholders")
    @Operation(summary = "프롬프트 플레이스홀더 조회", description = "프롬프트 플레이스홀더 조회 API")
    public ResponseEntity<PromptPlaceholderResponse> getPromptPlaceholders(@PathVariable("prompt_id") Long promptId) {
        PromptPlaceholderResponse response = promptPlaceholderService.getPromptPlaceholders(promptId);
        return ResponseEntity.ok(response);
    }
}

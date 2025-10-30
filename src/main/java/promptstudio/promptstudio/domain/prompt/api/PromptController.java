package promptstudio.promptstudio.domain.prompt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.prompt.application.PromptService;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;

import java.net.URI;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "프롬프트 API", description = "프롬프트 API입니다.")
public class PromptController {

    private final PromptService promptService;

    @PostMapping(
            value = "/prompt/member/{memberId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "프롬프트 등록", description = "프롬프트 등록 API")
    public ResponseEntity<Void> createPrompt(@PathVariable Long memberId,
                                             @ModelAttribute PromptCreateRequest request,
                                             @RequestPart(value = "file", required = false) MultipartFile file) {
        Long promptId = promptService.createPrompt(memberId, request, file);
        URI location = URI.create("/api/prompt/" + promptId);
        return ResponseEntity.created(location).build();
    }
}

package promptstudio.promptstudio.domain.prompt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.prompt.application.PromptService;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse;
import promptstudio.promptstudio.domain.prompt.dto.PromptCreateRequest;
import promptstudio.promptstudio.domain.prompt.dto.PromptResponse;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "프롬프트 API", description = "프롬프트 API입니다.")
public class PromptController {

    private final PromptService promptService;

    @PostMapping(
            value = "/prompts/members/{memberId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "프롬프트 등록", description = "프롬프트 등록 API")
    public ResponseEntity<Void> createPrompt(@PathVariable Long memberId,
                                             @ModelAttribute PromptCreateRequest request,
                                             @RequestPart(value = "file", required = false) MultipartFile file) {
        Long promptId = promptService.createPrompt(memberId, request, file);
        URI location = URI.create("/api/prompts/" + promptId);
        return ResponseEntity.created(location).build();
    }

    @GetMapping("/prompts")
    @Operation(summary = "프롬프트 전체 조회", description = "프롬프트 전체 조회 API")
    public ResponseEntity<List<PromptCardNewsResponse>> getAllPrompts(@RequestParam(value = "memberId", required = false) Long memberId,
                                                                      @RequestParam(value = "category", defaultValue = "전체") String category) {
        List<PromptCardNewsResponse> response = promptService.getAllPrompts(memberId, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/prompts/hot")
    @Operation(summary = "인기 프롬프트 조회", description = "인기 프롬프트 조회 API")
    public ResponseEntity<List<PromptCardNewsResponse>> getHotPrompts(@RequestParam(value = "memberId", required = false) Long memberId,
                                                                      @RequestParam(value = "category", defaultValue = "전체") String category) {
        List<PromptCardNewsResponse> response = promptService.getHotPrompts(memberId, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/prompts/likes")
    @Operation(summary = "좋아요한 프롬프트 조회", description = "좋아요한 프롬프트 조회 API")
    public ResponseEntity<List<PromptCardNewsResponse>> getLikedPrompts(@RequestParam Long memberId,
                                                                        @RequestParam(value = "category", defaultValue = "전체") String category) {
        List<PromptCardNewsResponse> response = promptService.getLikedPrompts(memberId, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/prompts/me")
    @Operation(summary = "내 프롬프트 조회", description = "내 프롬프트 조회 API")
    public ResponseEntity<List<PromptCardNewsResponse>> getMyPrompts(@RequestParam Long memberId,
                                                                     @RequestParam(value = "category", defaultValue = "전체") String category) {
        List<PromptCardNewsResponse> response = promptService.getMyPrompts(memberId, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/prompts/{promptId}")
    @Operation(summary = "프롬프트 상세 조회", description = "프롬프트 상세 조회 API")
    public ResponseEntity<PromptResponse> getPromptDetail(@PathVariable("promptId") Long promptId,
                                                    @RequestParam(value = "memberId", required = false) Long memberId) {
        PromptResponse response = promptService.getPromptDetail(memberId, promptId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/prompts/search")
    @Operation(summary = "프롬프트 검색", description = "프롬프트 검색 API")
    public ResponseEntity<List<PromptCardNewsResponse>> searchPrompts(@RequestParam(value = "memberId", required = false) Long memberId,
                                                                      @RequestParam(value = "category", defaultValue = "전체") String category,
                                                                      @RequestParam("q") String query) {
        List<PromptCardNewsResponse> response = promptService.searchPrompts(memberId, category, query);
        return ResponseEntity.ok(response);
    }
}

package promptstudio.promptstudio.domain.history.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import promptstudio.promptstudio.domain.history.application.HistoryService;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.domain.history.dto.HistoryDetailResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryResponse;
import promptstudio.promptstudio.domain.history.dto.HistoryRunResponse;
import promptstudio.promptstudio.domain.maker.domain.entity.Maker;
import promptstudio.promptstudio.domain.maker.domain.entity.MakerImage;
import promptstudio.promptstudio.domain.maker.domain.repository.MakerRepository;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.gpt.application.GptService;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/makers/{makerId}/histories")
@RequiredArgsConstructor
@Tag(name = "히스토리 API", description = "메이커 히스토리 API입니다.")
public class HistoryController {

    private final HistoryService historyService;
    private final GptService gptService;
    private final MakerRepository makerRepository;

    @Operation(summary = "GPT Run 실행", description = "프롬프트를 GPT로 실행하고 History 생성")
    @PostMapping("/run")
    public ResponseEntity<HistoryRunResponse> runGpt(
            @PathVariable Long makerId,
            @RequestParam String prompt
    ) {
        // 1. Maker 조회 (이미지 포함)
        Maker maker = makerRepository.findByIdWithImages(makerId)
                .orElseThrow(() -> new NotFoundException("메이커를 찾을 수 없습니다."));

        // 2. 이미지 URL 추출
        List<String> imageUrls = maker.getImages().stream()
                .sorted(Comparator.comparing(MakerImage::getOrderIndex))
                .map(MakerImage::getImageUrl)
                .toList();

        // 3. 이미지 있으면 runPromptWithImages, 없으면 runPrompt 호출
        GptRunResult gptRunResult;
        if (imageUrls.isEmpty()) {
            gptRunResult = gptService.runPrompt(prompt);
        } else {
            gptRunResult = gptService.runPromptWithImages(prompt, imageUrls);
        }

        // 4. History 생성
        HistoryRunResponse response = historyService.createHistory(makerId, gptRunResult);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "History 목록 조회", description = "메이커의 히스토리 목록 조회 (페이징)")
    @GetMapping
    public ResponseEntity<Page<HistoryResponse>> getHistoryList(
            @PathVariable Long makerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<HistoryResponse> response = historyService.getHistoryList(makerId, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "History 복원", description = "선택한 히스토리로 메이커 복원")
    @PatchMapping("/{historyId}/restore")
    public ResponseEntity<HistoryDetailResponse> restoreHistory(
            @PathVariable Long makerId,
            @PathVariable Long historyId
    ) {
        HistoryDetailResponse response = historyService.restoreHistory(makerId, historyId);
        return ResponseEntity.ok(response);
    }
}
package promptstudio.promptstudio.domain.maker.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import promptstudio.promptstudio.domain.maker.application.MakerService;
import promptstudio.promptstudio.domain.maker.dto.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/makers")
@RequiredArgsConstructor
@Tag(name = "메이커 API", description = "프롬프트 메이커 API입니다.")
public class MakerController {

    private final MakerService makerService;

    @PostMapping("")
    @Operation(summary = "메이커 생성", description = "새로운 메이커를 생성합니다.")
    public ResponseEntity<MakerCreateResponse> createMaker(
            @AuthenticationPrincipal Long memberId) {

        Long makerId = makerService.createMaker(memberId);

        MakerCreateResponse response = MakerCreateResponse.builder()
                .makerId(makerId)
                .build();

        URI location = URI.create("/api/makers/" + makerId);
        return ResponseEntity.created(location).body(response);
    }

    @PatchMapping(
            value = "/{makerId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "메이커 자동 저장", description = "메이커를 자동 저장합니다. (2초 debounce)")
    public ResponseEntity<MakerUpdateResponse> updateMaker(
            @PathVariable Long makerId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "existingImageUrls", required = false) List<String> existingImageUrls,
            @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages) {

        // DTO 직접 생성
        MakerUpdateRequest request = new MakerUpdateRequest();
        request.setTitle(title);
        request.setContent(content);
        if (existingImageUrls != null) {
            request.setExistingImageUrls(existingImageUrls);
        }

        MakerUpdateResponse response = makerService.updateMaker(makerId, request, newImages);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{makerId}")
    @Operation(summary = "메이커 상세 조회", description = "메이커의 상세 정보를 조회합니다.")
    public ResponseEntity<MakerDetailResponse> getMakerDetail(@PathVariable Long makerId) {
        MakerDetailResponse response = makerService.getMakerDetail(makerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upgrade")
    @Operation(summary = "텍스트 업그레이드", description = "선택한 텍스트를 GPT로 업그레이드합니다.")
    public ResponseEntity<TextUpgradeResponse> upgradeText(
            @RequestBody TextUpgradeRequest request) {
        TextUpgradeResponse response = makerService.upgradeText(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reupgrade")
    @Operation(summary = "텍스트 재업그레이드", description = "선택한 텍스트를 GPT로 재업그레이드합니다.")
    public ResponseEntity<TextUpgradeResponse> reupgradeText(
            @RequestBody TextReupgradeRequest request) {
        TextUpgradeResponse response = makerService.reupgradeText(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{makerId}/feedback")
    @Operation(summary = "프롬프트 피드백", description = "현재 프롬프트에 대한 피드백을 제공합니다.")
    public ResponseEntity<PromptFeedbackResponse> getPromptFeedback(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long makerId) {

        PromptFeedbackResponse response = makerService.getPromptFeedback(memberId, makerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("")
    @Operation(summary = "메이커 전체 조회", description = "내 메이커 목록을 조회합니다.")
    public ResponseEntity<MakerPageResponse> getMyMakers(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(value = "hasHistory") boolean hasHistory,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "9") int size) {

        MakerPageResponse response = makerService.getMyMakers(memberId, hasHistory, page, size);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{makerId}")
    @Operation(summary = "메이커 삭제", description = "메이커와 연관된 모든 데이터를 삭제합니다.")
    public ResponseEntity<Void> deleteMaker(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long makerId) {

        makerService.deleteMaker(memberId, makerId);
        return ResponseEntity.noContent().build();
    }

}
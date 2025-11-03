package promptstudio.promptstudio.domain.maker.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import promptstudio.promptstudio.domain.maker.application.MakerService;
import promptstudio.promptstudio.domain.maker.dto.MakerCreateRequest;
import promptstudio.promptstudio.domain.maker.dto.MakerCreateResponse;

import java.net.URI;

@RestController
@RequestMapping("/api/makers")
@RequiredArgsConstructor
@Tag(name = "메이커 API", description = "프롬프트 메이커 API입니다.")
public class MakerController {

    private final MakerService makerService;

    @PostMapping("/members/{memberId}")
    @Operation(summary = "메이커 생성", description = "새로운 메이커를 생성합니다.")
    public ResponseEntity<MakerCreateResponse> createMaker(
            @PathVariable Long memberId,
            @RequestBody MakerCreateRequest request) {

        Long makerId = makerService.createMaker(memberId, request);

        MakerCreateResponse response = MakerCreateResponse.builder()
                .makerId(makerId)
                .build();

        URI location = URI.create("/api/makers/" + makerId);
        return ResponseEntity.created(location).body(response);
    }
}
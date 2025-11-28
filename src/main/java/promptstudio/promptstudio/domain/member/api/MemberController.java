package promptstudio.promptstudio.domain.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import promptstudio.promptstudio.domain.member.application.MemberService;
import promptstudio.promptstudio.domain.member.dto.MemberIntroductionUpdateRequest;
import promptstudio.promptstudio.domain.member.dto.MemberIntroductionUpdateResponse;
import promptstudio.promptstudio.domain.member.dto.MemberResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "멤버 API", description = "멤버 API입니다.")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/members/me")
    @Operation(summary = "유저 정보 조회", description = "유저 정보 조회 API")
    public ResponseEntity<MemberResponse> getMember(@AuthenticationPrincipal Long memberId) {
        MemberResponse response = memberService.getMember(memberId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/members/me")
    @Operation(summary = "한줄소개 수정", description = "한줄소개 수정 API")
    public ResponseEntity<MemberIntroductionUpdateResponse> updateIntroduction(@AuthenticationPrincipal Long memberId,
                                                                               @RequestBody MemberIntroductionUpdateRequest request) {
        MemberIntroductionUpdateResponse response = memberService.updateIntroduction(memberId, request);
        return ResponseEntity.ok(response);
    }
}

package promptstudio.promptstudio.domain.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import promptstudio.promptstudio.domain.member.application.MemberService;
import promptstudio.promptstudio.domain.member.dto.MemberResponse;
import promptstudio.promptstudio.domain.prompt.dto.PromptCardNewsResponse;

import java.util.List;

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
}

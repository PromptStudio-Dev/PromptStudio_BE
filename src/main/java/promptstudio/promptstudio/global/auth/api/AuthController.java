package promptstudio.promptstudio.global.auth.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import promptstudio.promptstudio.domain.member.application.MemberService;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.entity.SocialProvider;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginRequest;
import promptstudio.promptstudio.global.google.client.GoogleIdTokenVerifierClient;
import promptstudio.promptstudio.global.google.client.GoogleTokenClient;
import promptstudio.promptstudio.global.google.dto.GoogleTokenResponse;
import promptstudio.promptstudio.global.google.dto.GoogleUserInfo;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/auth")
public class AuthController {

    private final GoogleTokenClient googleTokenClient;
    private final GoogleIdTokenVerifierClient googleVerifier;
    private final MemberService memberService;

    @PostMapping("/google")
    public Long googleLogin(@RequestBody GoogleLoginRequest req) {

        GoogleTokenResponse tokens =
                googleTokenClient.exchangeCode(req.getCode(), req.getRedirectUri(), req.getCodeVerifier());

        GoogleUserInfo info = googleVerifier.verify(tokens.getIdToken());

        Member member = memberService.findOrCreateMember(SocialProvider.GOOGLE, info);

        return member.getId();
    }

}

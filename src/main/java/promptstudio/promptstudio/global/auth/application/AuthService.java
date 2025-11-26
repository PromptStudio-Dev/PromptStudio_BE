package promptstudio.promptstudio.global.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import promptstudio.promptstudio.domain.member.application.MemberService;
import promptstudio.promptstudio.domain.member.domain.entity.Member;
import promptstudio.promptstudio.domain.member.domain.entity.SocialProvider;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginRequest;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginResponse;
import promptstudio.promptstudio.global.exception.http.BadRequestException;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.exception.http.UnauthorizedException;
import promptstudio.promptstudio.global.google.client.GoogleIdTokenVerifierClient;
import promptstudio.promptstudio.global.google.client.GoogleTokenClient;
import promptstudio.promptstudio.global.google.dto.GoogleTokenResponse;
import promptstudio.promptstudio.global.google.dto.GoogleUserInfo;
import promptstudio.promptstudio.global.jwt.JwtTokenProvider;
import promptstudio.promptstudio.global.jwt.domain.entity.RefreshToken;
import promptstudio.promptstudio.global.jwt.domain.repository.RefreshTokenRepository;
import promptstudio.promptstudio.global.jwt.dto.RefreshRequest;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final GoogleTokenClient googleTokenClient;
    private final GoogleIdTokenVerifierClient googleIdTokenVerifierClient;

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberService memberService;

    public GoogleLoginResponse loginWithGoogle(GoogleLoginRequest req) {

        // code -> Google token 교환 + id_token 검증 -> GoogleUserInfo
        GoogleUserInfo userInfo = verifyGoogle(req);

        // 신규 멤버 생성 or 기존 멤버 객체 반환
        Member member = memberService.findOrCreateMember(SocialProvider.GOOGLE, userInfo);
        Long memberId = member.getId();

        // 기존 refresh 폐기
        refreshTokenRepository.deleteByMemberId(memberId);

        // JWT 생성
        String accessToken = jwtTokenProvider.createAccessToken(memberId);
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);

        // refresh token DB 저장
        RefreshToken rt = RefreshToken.builder()
                .token(refreshToken)
                .memberId(memberId)
                .expiresAt(LocalDateTime.now()
                        .plusSeconds(jwtTokenProvider.getRefreshExpMs() / 1000))
                .build();
        refreshTokenRepository.save(rt);

        GoogleLoginResponse response = new GoogleLoginResponse();
        response.setMemberId(memberId);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);

        return response;
    }

    public GoogleLoginResponse reissue(RefreshRequest req) {

        String refreshToken = req.getRefreshToken();

        // JWT 형식 검증
        if (!jwtTokenProvider.validate(refreshToken)) {
            throw new BadRequestException("유효하지 않은 refreshToken");
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);

        // DB 존재/만료 검증
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("refreshToken not found"));


        if (stored.isExpired(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("refreshToken expired");
        }

        // 새 토큰 발급
        String newAccess = jwtTokenProvider.createAccessToken(memberId);
        String newRefresh = jwtTokenProvider.createRefreshToken(memberId);

        // 기존 삭제 후 새로 저장
        refreshTokenRepository.delete(stored);
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(newRefresh)
                        .memberId(memberId)
                        .expiresAt(LocalDateTime.now()
                                .plusSeconds(jwtTokenProvider.getRefreshExpMs() / 1000))
                        .build()
        );

        GoogleLoginResponse response = new GoogleLoginResponse();
        response.setMemberId(memberId);
        response.setAccessToken(newAccess);
        response.setRefreshToken(newRefresh);

        return response;
    }

    private GoogleUserInfo verifyGoogle(GoogleLoginRequest req) {
        GoogleTokenResponse tokenRes = googleTokenClient.exchangeCode(
                req.getCode(),
                req.getRedirectUri(),
                req.getCodeVerifier()
        );

        return googleIdTokenVerifierClient.verify(tokenRes.getIdToken());
    }
}

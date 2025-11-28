package promptstudio.promptstudio.global.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import promptstudio.promptstudio.global.auth.application.AuthService;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginRequest;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginResponse;

import java.io.IOException;

@Profile("local")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "[DEV] Auth", description = "로컬 테스트용 인증 API")
public class DevAuthController {

    private final AuthService authService;

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    @GetMapping("/google/login")
    @Operation(summary = "[DEV] 구글 로그인 페이지", description = "브라우저에서 직접 구글 로그인")
    public void googleLoginRedirect(HttpServletResponse response) throws IOException {
        String googleAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=email%20profile"
                + "&access_type=offline";

        response.sendRedirect(googleAuthUrl);
    }

    @GetMapping("/google/callback")
    @Operation(summary = "[DEV] 구글 로그인 콜백", description = "구글 로그인 후 콜백 처리")
    public ResponseEntity<GoogleLoginResponse> googleCallback(@RequestParam String code) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setCode(code);
        request.setRedirectUri(redirectUri);

        GoogleLoginResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }
}
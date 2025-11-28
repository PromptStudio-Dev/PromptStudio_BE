package promptstudio.promptstudio.global.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import promptstudio.promptstudio.global.auth.application.AuthService;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginRequest;
import promptstudio.promptstudio.global.auth.dto.GoogleLoginResponse;
import promptstudio.promptstudio.global.jwt.dto.RefreshRequest;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/auth")
@Tag(name = "Auth API", description = "Auth API입니다.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    @Operation(summary = "구글 로그인", description = "구글 로그인 API")
    public ResponseEntity<GoogleLoginResponse> googleLogin(@RequestBody GoogleLoginRequest request){
        GoogleLoginResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/reissue")
    @Operation(summary = "토큰 재발급", description = "토큰 재발급 API")
    public ResponseEntity<GoogleLoginResponse> reissue(@RequestBody RefreshRequest request){
        GoogleLoginResponse response =  authService.reissue(request);
        return ResponseEntity.ok(response);
    }

}

package promptstudio.promptstudio.domain.chat.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import promptstudio.promptstudio.domain.chat.application.ChatService;
import promptstudio.promptstudio.domain.chat.dto.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "채팅 API", description = "GPT 채팅 API입니다.")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/start")
    @Operation(summary = "채팅 시작", description = "새로운 채팅 세션을 시작합니다.")
    public ResponseEntity<ChatStartResponse> startChat(
            @AuthenticationPrincipal Long memberId,
            @RequestBody ChatStartRequest request) {

        ChatStartResponse response = chatService.startChat(memberId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send")
    @Operation(summary = "메시지 전송", description = "채팅 메시지를 전송합니다.")
    public ResponseEntity<ChatSendResponse> sendMessage(
            @AuthenticationPrincipal Long memberId,
            @RequestBody ChatSendRequest request) {

        ChatSendResponse response = chatService.sendMessage(memberId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/image/download")
    @Operation(summary = "이미지 다운로드", description = "채팅에서 생성된 이미지를 다운로드합니다.")
    public ResponseEntity<byte[]> downloadImage(
            @RequestParam String imageUrl) {

        ChatImageDownloadData data = chatService.downloadImage(imageUrl);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + data.getFileName() + "\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(data.getImageBytes());
    }
}

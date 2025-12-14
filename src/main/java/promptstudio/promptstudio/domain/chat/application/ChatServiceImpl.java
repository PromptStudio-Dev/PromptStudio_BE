package promptstudio.promptstudio.domain.chat.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import promptstudio.promptstudio.domain.chat.domain.ChatMessage;
import promptstudio.promptstudio.domain.chat.domain.ChatSession;
import promptstudio.promptstudio.domain.chat.dto.*;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;
import promptstudio.promptstudio.domain.prompt.domain.repository.PromptRepository;
import promptstudio.promptstudio.domain.promptplaceholder.domain.repository.PromptPlaceholderRepository;
import promptstudio.promptstudio.global.config.ChatSessionCache;
import promptstudio.promptstudio.global.dall_e.application.ImageService;
import promptstudio.promptstudio.global.exception.http.BadRequestException;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.gpt.prompt.PromptRegistry;
import promptstudio.promptstudio.global.gpt.prompt.PromptType;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatSessionCache chatSessionCache;
    private final PromptRepository promptRepository;
    private final PromptPlaceholderRepository promptPlaceholderRepository;
    private final PromptRegistry promptRegistry;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private static final String GPT_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_IMAGES = 6;

    @Override
    public ChatStartResponse startChat(Long memberId, ChatStartRequest request) {
        // 이미지 개수 검증
        if (request.getImages() != null && request.getImages().size() > MAX_IMAGES) {
            throw new BadRequestException("이미지는 최대 " + MAX_IMAGES + "개까지 첨부할 수 있습니다.");
        }

        // 세션 생성
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .memberId(memberId)
                .build();

        // 시스템 프롬프트 추가
        String systemPrompt = promptRegistry.get(PromptType.RUN_SYSTEM);
        session.addMessage(ChatMessage.system(systemPrompt));

        // 첫 번째 유저 메시지 구성
        String userContent = buildFirstUserMessage(request);
        List<String> images = request.getImages() != null ? request.getImages() : List.of();
        session.addMessage(ChatMessage.user(userContent, images));

        // GPT 호출
        String gptResponse = callGptApi(session.getMessages());

        // 응답 파싱
        ChatGptResult result = parseGptResponse(gptResponse);

        // assistant 메시지 추가
        session.addMessage(ChatMessage.assistant(gptResponse));

        // 캐시에 저장
        chatSessionCache.put(sessionId, session);

        return ChatStartResponse.builder()
                .sessionId(sessionId)
                .resultType(result.getResultType())
                .content(result.getContent())
                .imageUrl(result.getImageUrl())
                .build();
    }

    @Override
    public ChatSendResponse sendMessage(Long memberId, ChatSendRequest request) {
        // 세션 조회
        ChatSession session = chatSessionCache.get(request.getSessionId());
        if (session == null) {
            throw new NotFoundException("채팅 세션이 존재하지 않거나 만료되었습니다.");
        }

        // 세션 소유자 검증
        if (!session.getMemberId().equals(memberId)) {
            throw new BadRequestException("해당 세션에 접근 권한이 없습니다.");
        }

        // 이미지 개수 검증
        if (request.getImages() != null && request.getImages().size() > MAX_IMAGES) {
            throw new BadRequestException("이미지는 최대 " + MAX_IMAGES + "개까지 첨부할 수 있습니다.");
        }

        // 유저 메시지 추가
        List<String> images = request.getImages() != null ? request.getImages() : List.of();
        session.addMessage(ChatMessage.user(request.getMessage(), images));

        // GPT 호출
        String gptResponse = callGptApi(session.getMessages());

        // 응답 파싱
        ChatGptResult result = parseGptResponse(gptResponse);

        // assistant 메시지 추가
        session.addMessage(ChatMessage.assistant(gptResponse));

        return ChatSendResponse.builder()
                .resultType(result.getResultType())
                .content(result.getContent())
                .imageUrl(result.getImageUrl())
                .build();
    }

    private String buildFirstUserMessage(ChatStartRequest request) {
        StringBuilder sb = new StringBuilder();

        // promptId가 있으면 프롬프트 조회 및 치환
        if (request.getPromptId() != null && request.getPromptId() > 0) {
            Prompt prompt = promptRepository.findById(request.getPromptId())
                    .orElseThrow(() -> new NotFoundException("프롬프트가 존재하지 않습니다."));

            String content = prompt.getContent();

            // placeholder 치환
            if (request.getPlaceholderValues() != null && !request.getPlaceholderValues().isEmpty()) {
                content = replacePlaceholders(content, request.getPlaceholderValues());
            }

            sb.append(content);
        }

        // 추가 메시지가 있으면 붙이기
        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(request.getMessage());
        }

        if (sb.length() == 0) {
            throw new BadRequestException("프롬프트 또는 메시지 중 하나는 필수입니다.");
        }

        return sb.toString();
    }

    private String replacePlaceholders(String content, Map<String, String> values) {
        String result = content;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "[" + entry.getKey() + "]";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }

    private String callGptApi(List<ChatMessage> messages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<Map<String, Object>> apiMessages = new ArrayList<>();

            for (ChatMessage msg : messages) {
                Map<String, Object> apiMsg = new HashMap<>();
                apiMsg.put("role", msg.getRole());

                // 이미지가 있는 user 메시지인 경우
                if ("user".equals(msg.getRole()) && msg.getImages() != null && !msg.getImages().isEmpty()) {
                    List<Map<String, Object>> contentParts = new ArrayList<>();

                    // 텍스트 파트
                    Map<String, Object> textPart = new HashMap<>();
                    textPart.put("type", "text");
                    textPart.put("text", msg.getContent());
                    contentParts.add(textPart);

                    // 이미지 파트들
                    for (String imageUrl : msg.getImages()) {
                        Map<String, Object> imagePart = new HashMap<>();
                        imagePart.put("type", "image_url");
                        Map<String, String> imageUrlMap = new HashMap<>();
                        imageUrlMap.put("url", imageUrl);
                        imagePart.put("image_url", imageUrlMap);
                        contentParts.add(imagePart);
                    }

                    apiMsg.put("content", contentParts);
                } else {
                    apiMsg.put("content", msg.getContent());
                }

                apiMessages.add(apiMsg);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o");
            requestBody.put("messages", apiMessages);
            requestBody.put("max_tokens", 4096);

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    GPT_CHAT_URL,
                    HttpMethod.POST,
                    httpRequest,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

        } catch (Exception e) {
            log.error("GPT API 호출 실패: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GPT API 호출 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }

    private ChatGptResult parseGptResponse(String gptResponse) {
        try {
            String cleanedResponse = gptResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode jsonNode = objectMapper.readTree(cleanedResponse);
            String type = jsonNode.get("type").asText();

            if ("IMAGE".equals(type)) {
                String imagePrompt = jsonNode.get("prompt").asText();
                String imageUrl = imageService.generateImage(imagePrompt);

                return ChatGptResult.builder()
                        .resultType("IMAGE")
                        .imageUrl(imageUrl)
                        .build();
            } else {
                String content = jsonNode.get("content").asText();

                return ChatGptResult.builder()
                        .resultType("TEXT")
                        .content(content)
                        .build();
            }

        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패, 원본 텍스트 반환: {}", e.getMessage());
            // JSON 파싱 실패 시 원본 텍스트 반환
            return ChatGptResult.builder()
                    .resultType("TEXT")
                    .content(gptResponse)
                    .build();
        }
    }

    @lombok.Getter
    @lombok.Builder
    private static class ChatGptResult {
        private String resultType;
        private String content;
        private String imageUrl;
    }
}
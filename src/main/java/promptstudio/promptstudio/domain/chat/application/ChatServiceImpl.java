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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import promptstudio.promptstudio.domain.chat.domain.ChatMessage;
import promptstudio.promptstudio.domain.chat.domain.ChatSession;
import promptstudio.promptstudio.domain.chat.dto.*;
import promptstudio.promptstudio.domain.history.domain.entity.ResultType;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.global.config.ChatSessionCache;
import promptstudio.promptstudio.global.dall_e.application.ImageService;
import promptstudio.promptstudio.global.exception.http.BadRequestException;
import promptstudio.promptstudio.global.exception.http.NotFoundException;
import promptstudio.promptstudio.global.gpt.application.GptService;
import promptstudio.promptstudio.global.gpt.prompt.PromptRegistry;
import promptstudio.promptstudio.global.gpt.prompt.PromptType;
import promptstudio.promptstudio.global.s3.service.S3StorageService;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatSessionCache chatSessionCache;
    private final PromptRegistry promptRegistry;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final S3StorageService s3StorageService;
    private final GptService gptService;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private static final String GPT_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_IMAGES = 6;

    @Override
    public ChatStartResponse startChat(Long memberId, ChatStartRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BadRequestException("메시지는 필수입니다.");
        }

        if (request.getImages() != null && request.getImages().size() > MAX_IMAGES) {
            throw new BadRequestException("이미지는 최대 " + MAX_IMAGES + "개까지 첨부할 수 있습니다.");
        }

        List<String> imageUrls = uploadImages(request.getImages());

        String sessionId = UUID.randomUUID().toString();
        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .memberId(memberId)
                .build();

        String systemPrompt = promptRegistry.get(PromptType.RUN_SYSTEM);
        session.addMessage(ChatMessage.system(systemPrompt));
        session.addMessage(ChatMessage.user(request.getMessage(), imageUrls));

        ChatGptResult result;

        // ✅ 이미지가 있고 이미지 생성 요청이면 바로 Vision 파이프라인
        if (!imageUrls.isEmpty() && isImageGenerationRequest(request.getMessage())) {
            result = handleImageWithVision(request.getMessage(), imageUrls, session);
        } else {
            String gptResponse = callGptApi(session.getMessages());
            result = parseGptResponse(gptResponse, session, imageUrls);
            session.addMessage(ChatMessage.assistant(gptResponse));
        }

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
        ChatSession session = chatSessionCache.get(request.getSessionId());
        if (session == null) {
            throw new NotFoundException("채팅 세션이 존재하지 않거나 만료되었습니다.");
        }

        if (!session.getMemberId().equals(memberId)) {
            throw new BadRequestException("해당 세션에 접근 권한이 없습니다.");
        }

        if (request.getImages() != null && request.getImages().size() > MAX_IMAGES) {
            throw new BadRequestException("이미지는 최대 " + MAX_IMAGES + "개까지 첨부할 수 있습니다.");
        }

        List<String> imageUrls = uploadImages(request.getImages());

        // 이전 이미지 참조 감지 및 자동 첨부
        if (referencesPreviousImage(request.getMessage()) && session.getLastGeneratedImageUrl() != null) {
            if (imageUrls.isEmpty()) {
                imageUrls = new ArrayList<>();
            } else {
                imageUrls = new ArrayList<>(imageUrls);
            }
            imageUrls.add(session.getLastGeneratedImageUrl());
            log.info("이전 이미지 자동 첨부: {}", session.getLastGeneratedImageUrl());
        }

        session.addMessage(ChatMessage.user(request.getMessage(), imageUrls));

        ChatGptResult result;

        // ✅ 이미지가 있고 이미지 생성 요청이면 바로 Vision 파이프라인
        if (!imageUrls.isEmpty() && isImageGenerationRequest(request.getMessage())) {
            result = handleImageWithVision(request.getMessage(), imageUrls, session);
        } else {
            String gptResponse = callGptApi(session.getMessages());
            result = parseGptResponse(gptResponse, session, imageUrls);
            session.addMessage(ChatMessage.assistant(gptResponse));
        }

        return ChatSendResponse.builder()
                .resultType(result.getResultType())
                .content(result.getContent())
                .imageUrl(result.getImageUrl())
                .build();
    }

    /**
     * 이미지 생성 요청인지 판단
     */
    private boolean isImageGenerationRequest(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();

        return lower.contains("그려") ||
                lower.contains("만들어") ||
                lower.contains("생성") ||
                lower.contains("바꿔") ||
                lower.contains("변환") ||
                lower.contains("변경") ||
                lower.contains("스타일") ||
                lower.contains("이미지") ||
                lower.contains("그림") ||
                lower.contains("draw") ||
                lower.contains("create") ||
                lower.contains("generate") ||
                lower.contains("transform") ||
                lower.contains("convert") ||
                lower.contains("style");
    }

    /**
     * Vision 파이프라인으로 이미지 처리
     */
    private ChatGptResult handleImageWithVision(String message, List<String> imageUrls, ChatSession session) {
        try {
            GptRunResult result = gptService.runPromptWithImages(message, imageUrls);

            if (result.getResultType() == ResultType.IMAGE) {
                String s3ImageUrl = s3StorageService.copyImage(result.getResultImageUrl());
                session.setLastGeneratedImageUrl(s3ImageUrl);

                return ChatGptResult.builder()
                        .resultType("IMAGE")
                        .imageUrl(s3ImageUrl)
                        .build();
            } else {
                return ChatGptResult.builder()
                        .resultType("TEXT")
                        .content(result.getResultText())
                        .build();
            }
        } catch (Exception e) {
            log.error("Vision 파이프라인 처리 실패: {}", e.getMessage());
            return ChatGptResult.builder()
                    .resultType("TEXT")
                    .content("이미지 처리 중 오류가 발생했습니다: " + e.getMessage())
                    .build();
        }
    }

    private boolean referencesPreviousImage(String message) {
        if (message == null) return false;

        String lower = message.toLowerCase();

        return lower.contains("방금") ||
                lower.contains("아까") ||
                lower.contains("이전") ||
                lower.contains("위에") ||
                lower.contains("그 이미지") ||
                lower.contains("저 이미지") ||
                lower.contains("만들어준 이미지") ||
                lower.contains("그려준 이미지") ||
                lower.contains("생성한 이미지") ||
                lower.contains("생성해준 이미지") ||
                lower.contains("previous image") ||
                lower.contains("that image") ||
                lower.contains("the image");
    }

    private ChatGptResult parseGptResponse(String gptResponse, ChatSession session, List<String> currentImageUrls) {
        try {
            String cleanedResponse = gptResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode jsonNode = objectMapper.readTree(cleanedResponse);
            String type = jsonNode.get("type").asText();

            if ("IMAGE".equals(type)) {
                String imagePrompt = jsonNode.get("prompt").asText();
                String enhancedPrompt = enhanceImagePrompt(imagePrompt);
                log.info("Original prompt: {}", imagePrompt);
                log.info("Enhanced prompt: {}", enhancedPrompt);

                String resultImageUrl = imageService.generateImageHD(enhancedPrompt);
                resultImageUrl = s3StorageService.copyImage(resultImageUrl);

                session.setLastGeneratedImageUrl(resultImageUrl);

                return ChatGptResult.builder()
                        .resultType("IMAGE")
                        .imageUrl(resultImageUrl)
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
            return ChatGptResult.builder()
                    .resultType("TEXT")
                    .content(gptResponse)
                    .build();
        }
    }

    private List<String> uploadImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        List<String> imageUrls = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image != null && !image.isEmpty()) {
                String imageUrl = s3StorageService.uploadImage(image, "chat");
                imageUrls.add(imageUrl);
            }
        }
        return imageUrls;
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

                if ("user".equals(msg.getRole()) && msg.getImages() != null && !msg.getImages().isEmpty()) {
                    List<Map<String, Object>> contentParts = new ArrayList<>();

                    Map<String, Object> textPart = new HashMap<>();
                    textPart.put("type", "text");
                    textPart.put("text", msg.getContent());
                    contentParts.add(textPart);

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

    private String enhanceImagePrompt(String originalPrompt) {
        String prompt = originalPrompt.toLowerCase();
        StringBuilder enhanced = new StringBuilder();

        String cleaned = originalPrompt
                .replaceAll("(?i)\\s*character\\s*", " ")
                .replaceAll("(?i)\\s*캐릭터\\s*", " ")
                .replaceAll("(?i)\\s*케릭터\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();

        enhanced.append("A single illustration showing only one subject, ");

        if (prompt.contains("animal crossing") || prompt.contains("동물의 숲")) {
            enhanced.append(cleaned);
            enhanced.append(". Animal Crossing New Horizons style, chibi proportions, oversized round head, ");
            enhanced.append("large sparkling oval eyes, soft pastel colors, flat cel-shading, kawaii toylike aesthetic. ");
        } else if (prompt.contains("pixar") || prompt.contains("픽사")) {
            enhanced.append(cleaned);
            enhanced.append(". Pixar 3D animation style, stylized realistic proportions, ");
            enhanced.append("smooth subsurface scattering skin, expressive large eyes with reflections, ");
            enhanced.append("soft cinematic lighting, vibrant colors, high-end CGI quality. ");
        } else if (prompt.contains("ghibli") || prompt.contains("지브리")) {
            enhanced.append(cleaned);
            enhanced.append(". Studio Ghibli anime style, hand-painted watercolor aesthetic, ");
            enhanced.append("soft warm lighting, gentle earth tone palette, dreamy atmosphere. ");
        } else if (prompt.contains("disney") || prompt.contains("디즈니")) {
            enhanced.append(cleaned);
            enhanced.append(". Disney animation style, expressive large eyes, ");
            enhanced.append("smooth flowing lines, vibrant colors, magical aesthetic. ");
        } else {
            enhanced.append(cleaned);
            enhanced.append(". Appealing cartoon illustration style, friendly polished design, ");
            enhanced.append("professional digital art quality. ");
        }

        enhanced.append("Plain solid color background, centered subject. ");

        enhanced.append("The image must contain ONLY ONE single subject with NOTHING else. ");
        enhanced.append("Absolutely NO color palette, NO color swatches, NO color samples. ");
        enhanced.append("Absolutely NO additional characters, NO thumbnails, NO small versions. ");
        enhanced.append("Absolutely NO character sheet, NO reference sheet, NO model sheet. ");
        enhanced.append("Absolutely NO multiple views, NO multiple angles, NO turnaround. ");
        enhanced.append("Absolutely NO text, NO labels, NO annotations. ");
        enhanced.append("Just one clean illustration of the subject alone.");

        return enhanced.toString();
    }

    @lombok.Getter
    @lombok.Builder
    private static class ChatGptResult {
        private String resultType;
        private String content;
        private String imageUrl;
    }

    @Override
    public ChatImageDownloadData downloadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BadRequestException("이미지 URL이 필요합니다.");
        }

        byte[] imageBytes = s3StorageService.downloadImageFromUrl(imageUrl);
        String fileName = "chat_image_" + System.currentTimeMillis() + ".png";

        return ChatImageDownloadData.builder()
                .fileName(fileName)
                .imageBytes(imageBytes)
                .build();
    }
}
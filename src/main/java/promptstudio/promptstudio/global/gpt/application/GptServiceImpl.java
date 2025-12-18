package promptstudio.promptstudio.global.gpt.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import promptstudio.promptstudio.domain.history.domain.entity.ResultType;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.global.dall_e.application.ImageService;
import org.springframework.http.*;
import promptstudio.promptstudio.global.gpt.prompt.PromptRegistry;
import promptstudio.promptstudio.global.gpt.prompt.PromptType;
import promptstudio.promptstudio.global.gpt.prompt.TransformationLevel;

import java.util.List;
import java.util.Map;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final PromptRegistry promptRegistry;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private static final Set<String> KNOWN_STYLES = Set.of(
            "animal_crossing",
            "pixar",
            "ghibli"
    );

    private static final String SAFE_VISION_PROMPT = """
    For creating a fictional cartoon character, describe ONLY visible elements:
    - Hair: color and length (e.g., "long dark")
    - Clothing: type and color (e.g., "white shirt")
    - Accessory: one simple item or null
    
    DO NOT identify anyone. DO NOT describe face, pose, or body.
    
    Output JSON only:
    {"subject_type": "human", "materials": {"hair": "...", "clothing": "...", "accessory": null}}
    """;

    private static final String FALLBACK_KERNEL = """
    {
      "subject_type": "human",
      "materials": {
        "hair": "dark hair",
        "clothing": "casual top",
        "accessory": null
      },
      "fallback": true
    }
    """;

    private boolean isKnownAbstractedStyle(String style) {
        return style != null && Set.of(
                "chibi_game",
                "anime_film",
                "cgi_animation"
        ).contains(style.toLowerCase());
    }

    private String makeSaferPrompt(String originalPrompt) {
        // IP 관련 단어 모두 제거
        String safer = originalPrompt
                .replaceAll("(?i)animal crossing", "chibi game")
                .replaceAll("(?i)nintendo", "")
                .replaceAll("(?i)ghibli", "anime film")
                .replaceAll("(?i)miyazaki", "")
                .replaceAll("(?i)pixar", "3D animated")
                .replaceAll("(?i)disney", "")
                .replaceAll("(?i)studio", "")
                .replaceAll("\\s+", " ")
                .trim();

        // 안전 문구 추가
        safer = safer + " This is an original character design. Do not reference any existing IP, brand, or franchise.";

        log.info("=== Safer 프롬프트 생성 ===");
        log.info("원본: {}", originalPrompt);
        log.info("변환: {}", safer);

        return safer;
    }

    private boolean isVisionRejected(String result) {
        if (result == null || result.isBlank()) return true;
        String lower = result.toLowerCase();
        return lower.contains("sorry") ||
                lower.contains("can't help") ||
                lower.contains("cannot") ||
                lower.contains("not able") ||
                lower.contains("unable") ||
                lower.contains("i can't") ||
                !result.trim().startsWith("{");
    }

    private String toAbstractedStyle(String style) {
        if (style == null) return null;

        return switch (style.toLowerCase()) {
            case "animal_crossing" -> "chibi_game";
            case "ghibli" -> "anime_film";
            case "pixar", "disney" -> "cgi_animation";
            default -> style;
        };
    }

    private static final String GPT_VISION_URL = "https://api.openai.com/v1/chat/completions";

    private String detectRequestedStyle(String prompt) {
        String lower = prompt.toLowerCase();

        if (lower.contains("동물의 숲") || lower.contains("animal crossing") ||
                lower.contains("모여봐요") || lower.contains("치비")) {
            return "animal_crossing";
        }

        if (lower.contains("픽사") || lower.contains("pixar")) {
            return "pixar";
        }

        if (lower.contains("지브리") || lower.contains("ghibli") ||
                lower.contains("센과 치히로") || lower.contains("토토로") ||
                lower.contains("하울의 움직이는 성")) {
            return "ghibli";
        }

        if (lower.contains("포켓몬") || lower.contains("pokemon") ||
                lower.contains("피카츄")) {
            return "pokemon";
        }

        if (lower.contains("젤다") || lower.contains("zelda") ||
                lower.contains("브레스 오브 더 와일드") || lower.contains("티어스")) {
            return "zelda";
        }

        if (lower.contains("원신") || lower.contains("genshin") ||
                lower.contains("미호요")) {
            return "genshin";
        }

        if (lower.contains("디즈니") || lower.contains("disney")) {
            return "disney";
        }

        if (lower.contains("마블") || lower.contains("marvel")) {
            return "marvel";
        }

        if (lower.contains("애니") || lower.contains("anime") ||
                lower.contains("만화")) {
            return "anime";
        }

        return null;
    }

    private boolean isKnownStyle(String style) {
        return style != null && KNOWN_STYLES.contains(style);
    }

    @Override
    public String upgradeText(String fullContext, String selectedText, String direction) {
        ChatClient chatClient = chatClientBuilder.build();

        PromptTemplate promptTemplate = new PromptTemplate(
                promptRegistry.get(PromptType.UPGRADE_USER)
        );
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "direction", direction
        ));

        String result = chatClient.prompt(prompt)
                .system(promptRegistry.get(PromptType.UPGRADE_SYSTEM))
                .call()
                .content();

        return result != null ? result.trim() : selectedText;
    }

    @Override
    public String upgradeTextWithContext(
            String fullContext,
            String selectedText,
            String direction,
            String ragContext
    ) {
        ChatClient chatClient = chatClientBuilder.build();

        PromptTemplate promptTemplate = new PromptTemplate(
                promptRegistry.get(PromptType.UPGRADE_USER_WITH_CONTEXT)
        );
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "direction", direction,
                "ragContext", ragContext != null ? ragContext : ""
        ));

        String result = chatClient.prompt(prompt)
                .system(promptRegistry.get(PromptType.UPGRADE_SYSTEM))
                .call()
                .content();

        return result != null ? result.trim() : selectedText;
    }

    @Override
    public String reupgradeText(
            String fullContext,
            String selectedText,
            String prevDirection,
            String prevResult,
            String direction
    ) {
        ChatClient chatClient = chatClientBuilder.build();

        PromptTemplate promptTemplate = new PromptTemplate(
                promptRegistry.get(PromptType.UPGRADE_REUPGRADE)
        );
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "prevDirection", prevDirection != null ? prevDirection : "",
                "prevResult", prevResult != null ? prevResult : "",
                "direction", direction
        ));

        String result = chatClient.prompt(prompt)
                .system(promptRegistry.get(PromptType.UPGRADE_SYSTEM))
                .call()
                .content();

        return result != null ? result.trim() : selectedText;
    }

    @Override
    public String reupgradeTextWithContext(
            String fullContext,
            String selectedText,
            String prevDirection,
            String prevResult,
            String direction,
            String ragContext
    ) {
        ChatClient chatClient = chatClientBuilder.build();

        PromptTemplate promptTemplate = new PromptTemplate(
                promptRegistry.get(PromptType.UPGRADE_REUPGRADE_WITH_CONTEXT)
        );
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "prevDirection", prevDirection != null ? prevDirection : "",
                "prevResult", prevResult != null ? prevResult : "",
                "direction", direction,
                "ragContext", ragContext != null ? ragContext : ""
        ));

        String result = chatClient.prompt(prompt)
                .system(promptRegistry.get(PromptType.UPGRADE_SYSTEM))
                .call()
                .content();

        return result != null ? result.trim() : selectedText;
    }

    @Override
    public GptRunResult runPrompt(String prompt) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            String jsonResponse = chatClient.prompt()
                    .system(promptRegistry.get(PromptType.RUN_SYSTEM))
                    .user(prompt)
                    .call()
                    .content();

            jsonResponse = jsonResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            String type = jsonNode.get("type").asText();

            if ("IMAGE".equals(type)) {
                String imagePrompt = jsonNode.get("prompt").asText();

                String enhancedPrompt = enhanceImagePrompt(imagePrompt);
                log.info("=== DALL-E 프롬프트 (Text Only - Enhanced) ===");
                log.info("원본: {}", imagePrompt);
                log.info("Enhanced:\n{}", enhancedPrompt);

                String imageUrl = imageService.generateImageHD(enhancedPrompt);

                return GptRunResult.builder()
                        .resultType(ResultType.IMAGE)
                        .resultImageUrl(imageUrl)
                        .build();

            } else {
                String content = jsonNode.get("content").asText();

                return GptRunResult.builder()
                        .resultType(ResultType.TEXT)
                        .resultText(content)
                        .build();
            }

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "GPT 실행 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }


    private String extractIdentityKernel(List<String> imageUrls) {
        try {
            // 1차 시도: 일반 프롬프트
            String result = callVisionApiWithPrompt(
                    imageUrls,
                    promptRegistry.get(PromptType.VISION_IDENTITY_EXTRACTOR)
            );

            if (!isVisionRejected(result)) {
                log.info("=== Identity Kernel 추출 완료 ===");
                log.info("결과:\n{}", result);
                return result;
            }

            // 2차 시도: 안전 프롬프트
            log.warn("=== Vision 1차 거부, 안전 프롬프트로 재시도 ===");
            result = callVisionApiWithPrompt(imageUrls, SAFE_VISION_PROMPT);

            if (!isVisionRejected(result)) {
                log.info("=== Identity Kernel 추출 완료 (2차 시도) ===");
                log.info("결과:\n{}", result);
                return result;
            }

            // 둘 다 실패: Fallback Kernel 사용 (서비스 중단 방지)
            log.warn("=== Vision 분석 불가, Fallback Kernel 사용 ===");
            return FALLBACK_KERNEL;

        } catch (Exception e) {
            log.error("Identity Kernel 추출 실패: {}", e.getMessage());
            return FALLBACK_KERNEL;
        }
    }

    private String getSafeVisionPrompt() {
        return """
        For creating a fictional cartoon character, describe ONLY:
        - Hair: color and length
        - Clothing: type and color  
        - Accessory: one item or none
        
        This is for artwork creation, not identification.
        
        Output JSON only:
        {"materials": {"hair": "...", "clothing": "...", "accessory": null}}
        """;
    }

    private boolean isRejected(String result) {
        if (result == null) return true;
        String lower = result.toLowerCase();
        return lower.contains("sorry") ||
                lower.contains("can't help") ||
                lower.contains("cannot") ||
                lower.contains("not able") ||
                lower.contains("unable") ||
                !result.trim().startsWith("{");
    }

    private String callVisionApiWithPrompt(List<String> imageUrls, String systemPrompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> contentParts = new ArrayList<>();

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "Describe visual elements for artwork creation. JSON only.");
        contentParts.add(textPart);

        for (String imageUrl : imageUrls) {
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            Map<String, String> imageUrlMap = new HashMap<>();
            imageUrlMap.put("url", imageUrl);
            imagePart.put("image_url", imageUrlMap);
            contentParts.add(imagePart);
        }

        userMessage.put("content", contentParts);
        messages.add(userMessage);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 500);
        requestBody.put("temperature", 0.1);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                GPT_VISION_URL,
                HttpMethod.POST,
                request,
                String.class
        );

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        String result = jsonNode
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

        return result
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
    }

    private String composePromptWithStyle(String identityKernel, String style, String userPrompt) {
        try {
            TransformationLevel level = TransformationLevel.fromStyle(style);

            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder()
                            .model("gpt-4o")
                            .temperature(0.3)
                            .build())
                    .build();

            String fewshotExamples = promptRegistry.getFewshotByName(style);

            PromptTemplate promptTemplate = new PromptTemplate(
                    promptRegistry.get(PromptType.VISION_FEWSHOT_TEMPLATE)
            );

            Prompt prompt = promptTemplate.create(Map.of(
                    "style", style.toUpperCase().replace("_", " "),
                    "transformationLevel", level.getLevel().toUpperCase(),
                    "fewshotExamples", fewshotExamples != null ? fewshotExamples : "",
                    "identityKernel", identityKernel,
                    "userPrompt", userPrompt
            ));

            String dallePrompt = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.PROMPT_COMPOSER_SYSTEM))
                    .call()
                    .content();

            log.info("=== DALL-E 프롬프트 (Known Style: {}) ===", style);
            log.info("Transformation Level: {}", level.getLevel());
            log.info("프롬프트 ({} words):\n{}", dallePrompt.split(" ").length, dallePrompt);

            return dallePrompt.trim();

        } catch (Exception e) {
            log.error("Known Style 프롬프트 생성 실패: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "프롬프트 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String composePromptWithUnknownStyle(String identityKernel, String styleName, String styleAnalysis, String userPrompt) {
        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder()
                            .model("gpt-4o")
                            .temperature(0.3)
                            .build())
                    .build();

            PromptTemplate promptTemplate = new PromptTemplate(
                    promptRegistry.get(PromptType.VISION_UNKNOWN_STYLE_TEMPLATE)
            );

            Prompt prompt = promptTemplate.create(Map.of(
                    "styleName", styleName,
                    "styleAnalysis", styleAnalysis,
                    "identityKernel", identityKernel,
                    "userPrompt", userPrompt
            ));

            String dallePrompt = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.PROMPT_COMPOSER_SYSTEM))
                    .call()
                    .content();

            log.info("=== DALL-E 프롬프트 (Unknown Style: {}) ===", styleName);
            log.info("프롬프트 ({} words):\n{}", dallePrompt.split(" ").length, dallePrompt);

            return dallePrompt.trim();

        } catch (Exception e) {
            log.error("Unknown Style 프롬프트 생성 실패: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "프롬프트 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String composePromptNoStyle(String identityKernel, String userPrompt) {
        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder()
                            .model("gpt-4o")
                            .temperature(0.3)
                            .build())
                    .build();

            PromptTemplate promptTemplate = new PromptTemplate(
                    promptRegistry.get(PromptType.VISION_NO_STYLE_TEMPLATE)
            );

            Prompt prompt = promptTemplate.create(Map.of(
                    "identityKernel", identityKernel,
                    "userPrompt", userPrompt
            ));

            String dallePrompt = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.PROMPT_COMPOSER_SYSTEM))
                    .call()
                    .content();

            log.info("=== DALL-E 프롬프트 (No Style) ===");
            log.info("프롬프트 ({} words):\n{}", dallePrompt.split(" ").length, dallePrompt);

            return dallePrompt.trim();

        } catch (Exception e) {
            log.error("No Style 프롬프트 생성 실패: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "프롬프트 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String analyzeStyleCharacteristics(String styleName) {
        try {
            log.info("=== 스타일 특성 분석 시작: {} ===", styleName);

            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder()
                            .model("gpt-4o")
                            .temperature(0.3)
                            .build())
                    .build();

            PromptTemplate promptTemplate = new PromptTemplate(
                    promptRegistry.get(PromptType.VISION_STYLE_ANALYZER_USER)
            );
            Prompt prompt = promptTemplate.create(Map.of(
                    "styleName", styleName
            ));

            String styleAnalysis = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.VISION_STYLE_ANALYZER_SYSTEM))
                    .call()
                    .content();

            log.info("스타일 분석 완료:\n{}", styleAnalysis);

            return styleAnalysis.trim();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "스타일 특성 분석 실패: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public GptRunResult runPromptWithImages(String prompt, List<String> imageUrls) {
        try {
            log.info("=== 입력 프롬프트: [{}] ===", prompt);

            // 이미지 생성 요청 체크
            boolean isImageRequest = prompt.contains("이미지") ||
                    prompt.contains("그림") ||
                    prompt.contains("사진") ||
                    prompt.contains("그려줘") ||
                    prompt.contains("만들어줘") ||
                    prompt.contains("생성해줘") ||
                    prompt.contains("바꿔줘") ||
                    prompt.contains("변환") ||
                    prompt.contains("스타일");

            if (!isImageRequest) {
                ChatClient chatClient = chatClientBuilder.build();
                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                return GptRunResult.builder()
                        .resultType(ResultType.TEXT)
                        .resultText(response)
                        .build();
            }

            // === POLICY-AWARE ARCHITECTURE ===

            // 1. 스타일 감지 → IP-free 버전으로 변환
            String detectedStyle = detectRequestedStyle(prompt);
            String abstractedStyle = toAbstractedStyle(detectedStyle);
            log.info("=== 감지된 스타일: {} → 추상화: {} ===", detectedStyle, abstractedStyle);

            // 2. Vision: Identity Kernel 추출
            String identityKernel = extractIdentityKernel(imageUrls);

            // 3. Composer: 스타일별 DALL-E 프롬프트 생성
            String dallePrompt;

            if (abstractedStyle != null && isKnownAbstractedStyle(abstractedStyle)) {
                dallePrompt = composePromptWithStyle(identityKernel, abstractedStyle, prompt);
            } else if (abstractedStyle != null) {
                String styleAnalysis = analyzeStyleCharacteristics(abstractedStyle);
                dallePrompt = composePromptWithUnknownStyle(identityKernel, abstractedStyle, styleAnalysis, prompt);
            } else {
                dallePrompt = composePromptNoStyle(identityKernel, prompt);
            }

            // 4. DALL-E 이미지 생성 (1차 시도)
            String resultImageUrl;
            try {
                TransformationLevel level = TransformationLevel.fromStyle(detectedStyle);
                if (level == TransformationLevel.HEAVY || level == TransformationLevel.LIGHT) {
                    resultImageUrl = imageService.generateImageHD(dallePrompt);
                } else {
                    resultImageUrl = imageService.generateImageRealistic(dallePrompt);
                }
            } catch (Exception e) {
                // DALL-E 실패 시: 더 안전한 프롬프트로 1회 재시도
                log.warn("=== DALL-E 1차 실패, Style Abstraction 재시도 ===");
                log.warn("에러: {}", e.getMessage());

                String saferPrompt = makeSaferPrompt(dallePrompt);
                try {
                    resultImageUrl = imageService.generateImageHD(saferPrompt);
                } catch (Exception e2) {
                    // 재시도도 실패: 사용자에게 에러 반환
                    log.error("=== DALL-E 재시도 실패 ===");
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "이미지 생성이 제한되었습니다. 다른 스타일이나 이미지로 시도해주세요."
                    );
                }
            }

            return GptRunResult.builder()
                    .resultType(ResultType.IMAGE)
                    .resultImageUrl(resultImageUrl)
                    .build();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("이미지 생성 실패: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public String generateHistoryTitle(String currentTitle, String currentContent,
                                       String previousTitle, String previousContent) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            String userMessage;

            if (previousTitle == null || previousContent == null) {
                PromptTemplate promptTemplate = new PromptTemplate(
                        promptRegistry.get(PromptType.HISTORY_FIRST)
                );
                Prompt prompt = promptTemplate.create(Map.of(
                        "title", currentTitle != null ? currentTitle : "",
                        "content", currentContent != null ? currentContent : ""
                ));
                userMessage = prompt.getContents();
            } else {
                PromptTemplate promptTemplate = new PromptTemplate(
                        promptRegistry.get(PromptType.HISTORY_DIFF)
                );
                Prompt prompt = promptTemplate.create(Map.of(
                        "previousTitle", previousTitle,
                        "previousContent", previousContent,
                        "currentTitle", currentTitle != null ? currentTitle : "",
                        "currentContent", currentContent != null ? currentContent : ""
                ));
                userMessage = prompt.getContents();
            }

            String result = chatClient.prompt()
                    .system(promptRegistry.get(PromptType.HISTORY_SYSTEM))
                    .user(userMessage)
                    .call()
                    .content();

            return result != null ? result.trim() : "프롬프트 실행";

        } catch (Exception e) {
            log.error("히스토리 제목 생성 실패: {}", e.getMessage());
            return "프롬프트 실행";
        }
    }

    @Override
    public String generatePromptFeedback(String content) {
        try {
            if (content == null || content.isBlank()) {
                return "아직 프롬프트가 비어있어요! 어떤 이미지를 만들고 싶은지 작성해보세요 ✨";
            }

            if (content.trim().length() < 10) {
                return "조금 더 구체적으로 작성하면 원하는 결과를 얻기 쉬워요!";
            }

            ChatClient chatClient = chatClientBuilder.build();

            PromptTemplate promptTemplate = new PromptTemplate(
                    promptRegistry.get(PromptType.FEEDBACK_USER)
            );
            Prompt prompt = promptTemplate.create(Map.of("content", content));

            String result = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.FEEDBACK_SYSTEM))
                    .call()
                    .content();

            return result != null ? result.trim() : "프롬프트를 분석하는 중 문제가 발생했어요.";

        } catch (Exception e) {
            log.error("프롬프트 피드백 생성 실패: {}", e.getMessage());
            return "피드백을 생성하는 중 오류가 발생했어요.";
        }
    }

    @Override
    public String generateSearchQuery(String fullText, String selectedText, String direction) {
        ChatClient chatClient = chatClientBuilder.build();

        PromptTemplate promptTemplate = new PromptTemplate(
                promptRegistry.get(PromptType.QUERY_USER)
        );
        Prompt prompt = promptTemplate.create(Map.of(
                "fullText", fullText != null ? fullText : "",
                "selectedText", selectedText,
                "direction", direction
        ));

        String result = chatClient.prompt(prompt)
                .system(promptRegistry.get(PromptType.QUERY_SYSTEM))
                .call()
                .content();

        return result != null ? result.trim() : selectedText;
    }

    @Override
    public List<Document> retrieve(String query, int topK, double threshold) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold);

        return vectorStore.similaritySearch(builder.build());
    }

    public String enhanceImagePrompt(String originalPrompt) {
        String promptLower = originalPrompt.toLowerCase();
        StringBuilder enhanced = new StringBuilder();

        String cleaned = originalPrompt
                .replaceAll("(?i)\\s*character\\s*", " ")
                .replaceAll("(?i)\\s*캐릭터\\s*", " ")
                .replaceAll("(?i)\\s*케릭터\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();

        enhanced.append("A single illustration of ");
        enhanced.append(cleaned);

        String styleDescription = detectAndGetStyle(promptLower);
        if (styleDescription != null) {
            enhanced.append(". ").append(styleDescription).append(". ");
        } else {
            enhanced.append(". High quality digital illustration, clean professional style. ");
        }

        enhanced.append("Centered composition, clean background. ");
        enhanced.append("Single subject only, no multiple views, no text, no watermarks.");

        return enhanced.toString();
    }

    private String detectAndGetStyle(String promptLower) {
        if (promptLower.contains("animal crossing") || promptLower.contains("동물의 숲") ||
                promptLower.contains("모여봐요")) {
            return "Animal Crossing New Horizons style, chibi proportions with oversized round head, small compact body, large sparkling oval eyes, soft pastel colors, flat cel-shading, kawaii toylike aesthetic";
        }

        if (promptLower.contains("pixar") || promptLower.contains("픽사")) {
            return "Pixar 3D animation style, stylized realistic proportions, smooth subsurface scattering skin, expressive large eyes with reflections, soft cinematic lighting, vibrant colors, high-end CGI quality";
        }

        if (promptLower.contains("ghibli") || promptLower.contains("지브리") ||
                promptLower.contains("totoro") || promptLower.contains("토토로")) {
            return "Studio Ghibli anime style, hand-painted watercolor aesthetic, soft warm lighting, gentle earth tone palette, 2D animation with natural proportions, dreamy atmospheric quality";
        }

        if (promptLower.contains("disney") || promptLower.contains("디즈니")) {
            return "Disney 2D animation style, expressive large eyes, smooth flowing lines, vibrant colors, magical whimsical aesthetic";
        }

        if (promptLower.contains("anime") || promptLower.contains("애니메") ||
                promptLower.contains("애니") || promptLower.contains("만화")) {
            return "Japanese anime style, large expressive eyes, clean line art, vibrant colors";
        }

        if (promptLower.contains("realistic") || promptLower.contains("실사") ||
                promptLower.contains("사실적")) {
            return "Photorealistic style, natural lighting, detailed textures, professional photography quality";
        }

        if (promptLower.contains("cartoon") || promptLower.contains("카툰")) {
            return "Appealing cartoon illustration style, friendly polished design, clean lines, vibrant colors";
        }

        return null;
    }
}
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

    private static final String GPT_VISION_URL = "https://api.openai.com/v1/chat/completions";

    private String detectRequestedStyle(String prompt) {
        String lower = prompt.toLowerCase();

        // Known 스타일들 (기존)
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

        // 포켓몬
        if (lower.contains("포켓몬") || lower.contains("pokemon") ||
                lower.contains("피카츄")) {
            return "pokemon";
        }

        // 젤다
        if (lower.contains("젤다") || lower.contains("zelda") ||
                lower.contains("브레스 오브 더 와일드") || lower.contains("티어스")) {
            return "zelda";
        }

        // 원신
        if (lower.contains("원신") || lower.contains("genshin") ||
                lower.contains("미호요")) {
            return "genshin";
        }

        // 디즈니
        if (lower.contains("디즈니") || lower.contains("disney")) {
            return "disney";
        }

        // 마블
        if (lower.contains("마블") || lower.contains("marvel")) {
            return "marvel";
        }

        // 일반 애니메이션
        if (lower.contains("애니") || lower.contains("anime") ||
                lower.contains("만화")) {
            return "anime";
        }

        // 스타일 명시 없음
        return null;
    }

    // 현재는 동물의 숲, 픽사, 지브리만 few shot
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
                log.info("Original prompt: {}", imagePrompt);
                log.info("Enhanced prompt: {}", enhancedPrompt);

                String imageUrl = imageService.generateImageHD(enhancedPrompt);  // HD로 변경

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

    private String analyzeImageWithExtremeDetail(List<String> imageUrls) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", promptRegistry.get(PromptType.VISION_ANALYSIS_SYSTEM));
            messages.add(systemMessage);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");

            List<Map<String, Object>> contentParts = new ArrayList<>();

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "Please provide a detailed visual description of the image for the purpose of creating similar artistic character representations. Describe the visual appearance, style, and characteristics that would help an artist recreate a similar character design. Focus on describing what you see without attempting to identify who this might be.");
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
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.3);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    GPT_VISION_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String analysis = jsonNode
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            return analysis.trim();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 분석 실패: " + e.getMessage(),
                    e
            );
        }
    }

    //스타일 지정 : 지브리, 동물의 숲, 픽사
    private String createPromptWithKnownStyle(String style, String userPrompt, String imageAnalysis) {
        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder()
                            .model("gpt-4o")
                            .temperature(0.3)
                            .build())
                    .build();

            // Few-shot 예시 로드
            String fewshotExamples = promptRegistry.getFewshotByName(style);

            // 템플릿에 few-shot 주입
            PromptTemplate promptTemplate = new PromptTemplate(
                    promptRegistry.get(PromptType.VISION_FEWSHOT_TEMPLATE)
            );
            Prompt prompt = promptTemplate.create(Map.of(
                    "fewshotExamples", fewshotExamples != null ? fewshotExamples : "",
                    "style", style.toUpperCase(),
                    "userPrompt", userPrompt,
                    "imageAnalysis", imageAnalysis
            ));

            String dallePrompt = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.VISION_DALLE_EXPERT_SYSTEM))
                    .call()
                    .content();

            return dallePrompt.trim();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DALL-E 프롬프트 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

    //스타일 미지정 일반 이미지 제작
    private String createPromptWithoutStyle(String userPrompt, String imageAnalysis) {
        try {
            System.out.println("=== 스타일 미지정: 원본 스타일 유지 ===");

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
                    "userPrompt", userPrompt,
                    "imageAnalysis", imageAnalysis
            ));

            String dallePrompt = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.VISION_DALLE_EXPERT_SYSTEM))
                    .call()
                    .content();

            System.out.println("프롬프트 생성 완료 (원본 스타일 유지)");
            System.out.println("길이: " + dallePrompt.split(" ").length + " 단어");
            System.out.println("================================");

            return dallePrompt.trim();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "원본 스타일 프롬프트 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String analyzeStyleCharacteristics(String styleName) {
        try {
            System.out.println("=== 스타일 특성 분석 시작: " + styleName + " ===");

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

            System.out.println("스타일 분석 완료:");
            System.out.println(styleAnalysis);
            System.out.println("================================");

            return styleAnalysis.trim();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "스타일 특성 분석 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String createPromptWithUnknownStyle(
            String styleName,
            String styleAnalysis,
            String userPrompt,
            String imageAnalysis) {
        try {
            System.out.println("=== Unknown 스타일 프롬프트 생성 시작 ===");

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
                    "imageAnalysis", imageAnalysis,
                    "userPrompt", userPrompt
            ));

            String dallePrompt = chatClient.prompt(prompt)
                    .system(promptRegistry.get(PromptType.VISION_DALLE_EXPERT_SYSTEM))
                    .call()
                    .content();

            System.out.println("프롬프트 생성 완료");
            System.out.println("길이: " + dallePrompt.split(" ").length + " 단어");
            System.out.println("================================");

            return dallePrompt.trim();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unknown 스타일 프롬프트 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public GptRunResult runPromptWithImages(String prompt, List<String> imageUrls) {
        try {
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

            // === 1. 스타일 감지 ===
            String detectedStyle = detectRequestedStyle(prompt);

            // === 2. Vision 분석 ===
            String imageAnalysis = analyzeImageWithExtremeDetail(imageUrls);

            // === 3. DALL-E 프롬프트 생성 ===
            String dallePrompt;

            if (detectedStyle != null && isKnownStyle(detectedStyle)) {
                // Known 스타일: Few-shot 방법론
                dallePrompt = createPromptWithKnownStyle(detectedStyle, prompt, imageAnalysis);
            } else if (detectedStyle != null) {
                // Unknown 스타일: 스타일 분석 후 적용
                String styleAnalysis = analyzeStyleCharacteristics(detectedStyle);
                dallePrompt = createPromptWithUnknownStyle(detectedStyle, styleAnalysis, prompt, imageAnalysis);
            } else {
                // 스타일 미지정: 원본 유지
                dallePrompt = createPromptWithoutStyle(prompt, imageAnalysis);
            }

            // === 4. DALL-E Generation 호출 ===
            String resultImageUrl;

            if (detectedStyle != null && (detectedStyle.equals("ghibli") ||
                    detectedStyle.equals("pixar") ||
                    detectedStyle.equals("animal_crossing"))) {
                // Known 스타일: HD + vivid
                resultImageUrl = imageService.generateImageHD(dallePrompt);
            } else {
                // 원본 유지 또는 Unknown 스타일: HD + natural
                resultImageUrl = imageService.generateImageRealistic(dallePrompt);
            }

            return GptRunResult.builder()
                    .resultType(ResultType.IMAGE)
                    .resultImageUrl(resultImageUrl)
                    .build();

        } catch (Exception e) {
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
                // 첫 번째 히스토리
                PromptTemplate promptTemplate = new PromptTemplate(
                        promptRegistry.get(PromptType.HISTORY_FIRST)
                );
                Prompt prompt = promptTemplate.create(Map.of(
                        "title", currentTitle != null ? currentTitle : "",
                        "content", currentContent != null ? currentContent : ""
                ));
                userMessage = prompt.getContents();
            } else {
                // 변경점 요약
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
            System.err.println("히스토리 제목 생성 실패: " + e.getMessage());
            return "프롬프트 실행";
        }
    }

    //피드백 생성
    @Override
    public String generatePromptFeedback(String content) {
        try {
            // 빈 프롬프트 처리
            if (content == null || content.isBlank()) {
                return "아직 프롬프트가 비어있어요! 어떤 이미지를 만들고 싶은지 작성해보세요 ✨";
            }

            // 너무 짧은 프롬프트 처리
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
            System.err.println("프롬프트 피드백 생성 실패: " + e.getMessage());
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

    private String enhanceImagePrompt(String originalPrompt) {
        String prompt = originalPrompt.toLowerCase();
        StringBuilder enhanced = new StringBuilder();

        // 1. "character/캐릭터/케릭터" 단어 제거
        String cleaned = originalPrompt
                .replaceAll("(?i)\\s*character\\s*", " ")
                .replaceAll("(?i)\\s*캐릭터\\s*", " ")
                .replaceAll("(?i)\\s*케릭터\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 2. 시작 문구
        enhanced.append("Single finished illustration, ONLY ONE IMAGE, of ");

        // 3. 스타일 감지 및 강화
        if (prompt.contains("animal crossing") || prompt.contains("동물의 숲")) {
            enhanced.append(cleaned);
            enhanced.append(". Animal Crossing New Horizons game style, chibi proportions with oversized round head and small compact body, ");
            enhanced.append("large sparkling oval eyes, simplified cute features, soft pastel colors, flat cel-shading, ");
            enhanced.append("kawaii toylike aesthetic, Nintendo game art quality. ");
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

        // 4. 구도 및 배경
        enhanced.append("Centered composition, simple clean solid color background. ");

        // 5. 강화된 네거티브 제약
        enhanced.append("ONLY ONE SINGLE IMAGE. ");
        enhanced.append("NO character sheet, NO reference sheet, NO multiple views, NO multiple angles, ");
        enhanced.append("NO color palette, NO color swatches, NO thumbnails, NO small icons, ");
        enhanced.append("NO concept art, NO sketches, NO design process, NO turnaround.");

        return enhanced.toString();
    }

}
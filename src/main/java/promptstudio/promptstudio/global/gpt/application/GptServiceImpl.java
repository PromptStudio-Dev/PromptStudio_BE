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

    private String extractStyleKeywords(String prompt) {
        String lower = prompt.toLowerCase();

        // 픽셀 아트
        if (lower.contains("픽셀") || lower.contains("pixel")) {
            return "pixel_art";
        }
        // 치비/SD
        if (lower.contains("치비") || lower.contains("chibi") || lower.contains("sd캐릭터") || lower.contains("sd 캐릭터")) {
            return "chibi";
        }
        // 애니메이션/만화
        if (lower.contains("애니") || lower.contains("anime") || lower.contains("만화") || lower.contains("일본")) {
            return "anime";
        }
        // 3D 카툰
        if (lower.contains("3d") || lower.contains("3D") || lower.contains("카툰") || lower.contains("cartoon")) {
            return "3d_cartoon";
        }
        // 수채화
        if (lower.contains("수채화") || lower.contains("watercolor") || lower.contains("워터컬러")) {
            return "watercolor";
        }
        // 유화
        if (lower.contains("유화") || lower.contains("oil painting") || lower.contains("오일")) {
            return "oil_painting";
        }
        // 일러스트
        if (lower.contains("일러스트") || lower.contains("illust")) {
            return "illustration";
        }
        // 미니멀
        if (lower.contains("미니멀") || lower.contains("minimal") || lower.contains("심플")) {
            return "minimal";
        }
        // 레트로
        if (lower.contains("레트로") || lower.contains("retro") || lower.contains("빈티지") || lower.contains("vintage")) {
            return "retro";
        }

        return "illustration";  // 기본값: 일러스트
    }

    private String composePromptForUnknownStyle(String identityKernel, String styleKeyword, String userPrompt) {
        try {
            // Identity Kernel에서 hair, clothing 추출
            JsonNode node = objectMapper.readTree(identityKernel);
            JsonNode materials = node.path("materials");

            String hair = materials.has("hair") ? materials.get("hair").asText() : "dark";
            String clothing = materials.has("clothing") ? materials.get("clothing").asText() : "casual clothes";

            // 템플릿에 값 주입
            String template = getStyleTemplate(styleKeyword);
            String dallePrompt = template
                    .replace("[HAIR]", hair)
                    .replace("[CLOTHING]", clothing)
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("=== DALL-E 프롬프트 (Unknown Style: {}) ===", styleKeyword);
            log.info("프롬프트 ({} words):\n{}", dallePrompt.split(" ").length, dallePrompt);

            return dallePrompt;

        } catch (Exception e) {
            log.error("Unknown Style 프롬프트 생성 실패: {}", e.getMessage());
            // 최소한의 안전 프롬프트
            return "A single finished character illustration. Clean appealing art style. Simple background. Not a character sheet.";
        }
    }


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

    private boolean isRefusal(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase();
        return lower.contains("sorry") ||
                lower.contains("i can't") ||
                lower.contains("i cannot") ||
                lower.contains("can't assist") ||
                lower.contains("cannot assist") ||
                lower.contains("not able to") ||
                lower.contains("unable to") ||
                lower.contains("i'm unable");
    }

    private String getStyleTemplate(String styleKeyword) {
        return switch (styleKeyword) {

            // ═══════════════════════════════════════
            // 🧸 동물의 숲 계열 (CHIBI_GAME+)
            // ═══════════════════════════════════════
            case "chibi_game", "animal_crossing" -> """
            A single finished, fully rendered chibi 3D game character illustration intended as final artwork.
            
            RENDER STYLE:
            Toy-like 3D game engine aesthetic. Head-to-body ratio approximately 1:1.
            Skin has smooth plastic finish with subtle subsurface scattering.
            Soft cel-shaded shadows with no hard edges.
            Camera angle: eye-level with slight top-down tilt, as if viewing a game screen.
            
            CHARACTER:
            Character with [HAIR] hair wearing [CLOTHING].
            Oversized spherical head, tiny dot eyes with subtle shine, minimal nose, small curved smile.
            Compact rounded body with stubby limbs.
            Relaxed cheerful pose with gentle head tilt and happy welcoming expression.
            
            SCENE:
            Standing in a cozy, warm environment with soft ambient occlusion.
            Bright cheerful pastel color palette. Warm soft daylight with gentle fill light.
            
            QUALITY:
            This style emphasizes charming toylike appeal, cohesive soft lighting, and game-screenshot authenticity.
            Transparent background with no environment, no shadows on ground, no backdrop.
            This is a complete final render, not a design reference.
            Not a character sheet. Not concept art. Not a turnaround.
            """;

            // ═══════════════════════════════════════
            // 🌿 지브리 계열 (ANIME_FILM+)
            // ═══════════════════════════════════════
            case "anime_film", "ghibli" -> """
            A single finished, fully rendered 2D anime film style character illustration intended as final artwork.
            
            RENDER STYLE:
            Hand-painted traditional animation aesthetic. Watercolor paper texture visible.
            Soft color bleeding at edges. Warm earth-toned palette with desaturated highlights.
            Atmospheric perspective creating depth between character and background.
            Gentle rim lighting from natural sun. Soft diffused shadows.
            
            CHARACTER:
            Character with [HAIR] hair wearing [CLOTHING].
            Soft oval face with gentle simplified features, warm expressive eyes.
            Natural graceful pose with subtle wind movement in hair and clothes.
            Serene peaceful expression with gentle hint of emotion.
            
            SCENE:
            Standing in a dreamy natural environment - sunlit meadow, quiet countryside, or peaceful village.
            Impressionistic background with soft bokeh-like depth.
            Golden hour or soft overcast lighting. Visible atmosphere and air.
            
            QUALITY:
            This style emphasizes cinematic composition, atmospheric depth, hand-painted warmth, and nostalgic beauty.
            The character exists within a living, breathing world - not isolated on empty background.
            This is a complete final render capturing a quiet cinematic moment.
            Not a character sheet. Not concept art. Not a turnaround.
            """;

            // ═══════════════════════════════════════
            // 🎬 픽사 계열 (CGI_ANIMATION+)
            // ═══════════════════════════════════════
            case "cgi_animation", "pixar" -> """
            A single finished, fully rendered 3D CGI animated film style character illustration intended as final artwork.
            
            RENDER STYLE:
            High-end theatrical animation quality. Global illumination with realistic light bounce.
            Three-point lighting setup: warm key light, cool fill light, subtle rim light for separation.
            Subsurface scattering on skin. Detailed eye reflections with dual catchlights.
            Camera: 35mm equivalent lens, shallow depth of field, cinematic composition.
            
            CHARACTER:
            Character with [HAIR] hair wearing [CLOTHING].
            Stylized appealing proportions with slightly enlarged head and eyes.
            Smooth rounded features with careful attention to appealing shapes.
            Natural dynamic pose with genuine body language and authentic emotion.
            Warm engaging expression - genuine smile reaching the eyes, or thoughtful contemplation.
            
            SCENE:
            Character in a warm, inviting environment with motivated lighting.
            Rich saturated color palette with intentional color harmony.
            Soft atmospheric depth in background. Professional cinematography feel.
            
            QUALITY:
            This style emphasizes theatrical animation excellence, intentional lighting design, emotional authenticity, and cohesive color harmony.
            This looks like a frame from a major animated feature film.
            This is a complete final render at theatrical release quality.
            Not a character sheet. Not concept art. Not a turnaround.
            """;

            // ═══════════════════════════════════════
            // ✨ 디즈니 계열 (CGI_ANIMATION_SOFT)
            // ═══════════════════════════════════════
            case "disney", "cgi_animation_soft" -> """
            A single finished, fully rendered 3D CGI animated musical film style character illustration intended as final artwork.
            
            RENDER STYLE:
            Soft theatrical animation quality with fairy-tale warmth. Gentle global illumination.
            Lower contrast than typical CGI - lifted shadows, soft highlights.
            Warm color temperature throughout. Subtle bloom and glow effects.
            Skin has porcelain-like smoothness with warm subsurface scattering.
            Camera: medium shot, eye-level, intimate character focus.
            
            CHARACTER:
            Character with [HAIR] hair wearing [CLOTHING].
            Graceful elegant proportions with emphasis on facial curves and expressiveness.
            Large sparkling eyes with detailed iris patterns and magical catchlights.
            Flowing hair with soft, almost magical movement.
            Pose suggests musical performance or emotional moment - expressive and theatrical.
            Expression shows clear readable emotion - joy, wonder, determination, or tenderness.
            
            SCENE:
            Character in a magical, storybook environment with dreamy lighting.
            Soft pastel and jewel-tone color palette. Fairy-tale atmosphere.
            Subtle sparkles or magical particles in the air.
            Background suggests a larger story and world.
            
            QUALITY:
            This style emphasizes fairy-tale magic, emotional storytelling, musical theater expressiveness, and princess-like elegance.
            The character looks like they're about to break into song.
            This is a complete final render from an animated musical film.
            Not a character sheet. Not concept art. Not a turnaround.
            """;

            // ═══════════════════════════════════════
            // 기타 스타일 (기본)
            // ═══════════════════════════════════════
            case "pixel_art" -> """
            A single finished, fully rendered pixel art character illustration intended as final artwork.
            Retro game aesthetic with clean pixel blocks, limited 16-color palette.
            Full body character with [HAIR] hair wearing [CLOTHING].
            Relaxed pose with subtle head tilt and gentle friendly expression.
            Classic 16-bit or 32-bit game sprite style with careful dithering.
            Transparent background with no environment, no shadows on ground, no backdrop.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            case "chibi" -> """
            A single finished, fully rendered chibi character illustration intended as final artwork.
            Cute stylized proportions with oversized head, small rounded body.
            Character with [HAIR] hair wearing [CLOTHING].
            Relaxed pose with subtle head tilt and adorable happy expression.
            Big sparkling expressive eyes, simplified cute features.
            Soft pastel colors, clean smooth lines, gentle shading.
            Transparent background with no environment, no shadows on ground, no backdrop.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            case "anime" -> """
            A single finished, fully rendered anime style character illustration intended as final artwork.
            Japanese animation aesthetic with clean lines, vibrant colors.
            Character with [HAIR] hair wearing [CLOTHING].
            Natural relaxed pose with subtle body tilt and gentle expression.
            Expressive detailed eyes, stylized appealing proportions.
            Dynamic lighting with soft shadows. Soft atmospheric background.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            case "3d_cartoon" -> """
            A single finished, fully rendered 3D cartoon character illustration intended as final artwork.
            Modern CGI animation style with smooth rounded features.
            Character with [HAIR] hair wearing [CLOTHING].
            Relaxed natural pose with subtle head angle and warm friendly expression.
            Stylized appealing proportions, expressive face.
            Cinematic lighting, vibrant saturated colors. Clean gradient background.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            case "watercolor" -> """
            A single finished, fully rendered watercolor style character illustration intended as final artwork.
            Soft painted aesthetic with flowing colors and gentle organic edges.
            Character with [HAIR] hair wearing [CLOTHING].
            Graceful relaxed pose with natural head tilt and serene expression.
            Dreamy atmospheric quality, beautiful organic textures.
            Warm natural lighting. Soft blended painterly background.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            case "oil_painting" -> """
            A single finished, fully rendered oil painting style character portrait intended as final artwork.
            Classical painted aesthetic with rich textures and depth.
            Character with [HAIR] hair wearing [CLOTHING].
            Elegant pose with natural head angle and refined expression.
            Dramatic lighting, rich luxurious color palette.
            Artistic brushwork visible, museum quality feel. Elegant background.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            case "minimal" -> """
            A single finished, fully rendered minimal style character illustration intended as final artwork.
            Clean simple design with limited colors and geometric shapes.
            Character with [HAIR] hair wearing [CLOTHING].
            Simple relaxed pose with subtle asymmetry and calm expression.
            Flat harmonious colors, elegant geometric simplification.
            Plain solid color background.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            case "retro" -> """
            A single finished, fully rendered retro style character illustration intended as final artwork.
            Vintage aesthetic with warm tones and nostalgic charm.
            Character with [HAIR] hair wearing [CLOTHING].
            Classic relaxed pose with natural head tilt and pleasant expression.
            Timeless illustration style, warm muted color palette.
            Vintage poster or classic magazine feel. Simple themed background.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
            default -> """
            A single finished, fully rendered character illustration intended as final artwork.
            Clean appealing art style with professional quality.
            Character with [HAIR] hair wearing [CLOTHING].
            Relaxed natural pose with subtle head tilt and pleasant friendly expression.
            Harmonious colors, balanced composition, polished finish.
            Clean simple background.
            This is a complete final render. Not a character sheet. Not concept art.
            """;
        };
    }

    private boolean isKnownAbstractedStyle(String style) {
        return style != null && Set.of(
                "chibi_game",
                "anime_film",
                "cgi_animation",
                "cgi_animation_soft"  // 디즈니 추가
        ).contains(style.toLowerCase());
    }
    private String toAbstractedStyle(String style) {
        if (style == null) return null;

        return switch (style.toLowerCase()) {
            case "animal_crossing" -> "chibi_game";
            case "ghibli" -> "anime_film";
            case "pixar" -> "cgi_animation";
            case "disney" -> "cgi_animation_soft";  // 디즈니 전용
            default -> style;
        };
    }

    private static final String GPT_VISION_URL = "https://api.openai.com/v1/chat/completions";

    private String detectRequestedStyle(String prompt) {
        if (prompt == null) return null;
        String lower = prompt.toLowerCase();

        // 동물의 숲
        if (lower.contains("동물의 숲") || lower.contains("animal crossing") ||
                lower.contains("동물의숲") || lower.contains("모동숲") || lower.contains("모여봐요")) {
            return "animal_crossing";
        }
        // 지브리
        if (lower.contains("지브리") || lower.contains("ghibli") ||
                lower.contains("미야자키") || lower.contains("miyazaki") ||
                lower.contains("토토로") || lower.contains("하울") || lower.contains("센과 치히로")) {
            return "ghibli";
        }
        // 픽사
        if (lower.contains("픽사") || lower.contains("pixar")) {
            return "pixar";
        }
        // 디즈니
        if (lower.contains("디즈니") || lower.contains("disney") ||
                lower.contains("겨울왕국") || lower.contains("frozen") ||
                lower.contains("라푼젤") || lower.contains("rapunzel") ||
                lower.contains("모아나") || lower.contains("moana") ||
                lower.contains("엔칸토") || lower.contains("encanto")) {
            return "disney";
        }

        return null;
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
            // 1차 시도
            String result = callVisionApiWithPrompt(
                    imageUrls,
                    promptRegistry.get(PromptType.VISION_IDENTITY_EXTRACTOR)
            );

            if (!isRefusal(result) && result.trim().startsWith("{")) {
                log.info("=== Identity Kernel 추출 완료 ===");
                log.info("결과:\n{}", result);
                return result;
            }

            // 2차 시도: 안전 프롬프트
            log.warn("=== Vision 1차 거부, 안전 프롬프트로 재시도 ===");
            result = callVisionApiWithPrompt(imageUrls, SAFE_VISION_PROMPT);

            if (!isRefusal(result) && result.trim().startsWith("{")) {
                log.info("=== Identity Kernel 추출 완료 (2차 시도) ===");
                log.info("결과:\n{}", result);
                return result;
            }

            // 둘 다 실패: 거부 메시지 그대로 반환 (상위에서 체크)
            log.warn("=== Vision 분석 불가 ===");
            return result;  // "I'm sorry..." 그대로 반환

        } catch (Exception e) {
            log.error("Identity Kernel 추출 실패: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
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

            boolean isImageRequest = prompt.contains("이미지") ||
                    prompt.contains("그림") ||
                    prompt.contains("사진") ||
                    prompt.contains("그려줘") ||
                    prompt.contains("만들어줘") ||
                    prompt.contains("생성해줘") ||
                    prompt.contains("바꿔줘") ||
                    prompt.contains("변환") ||
                    prompt.contains("스타일") ||
                    prompt.contains("일러스트");

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

            // 1. 스타일 감지
            String detectedStyle = detectRequestedStyle(prompt);
            String abstractedStyle = toAbstractedStyle(detectedStyle);
            log.info("=== 감지된 스타일: {} → 추상화: {} ===", detectedStyle, abstractedStyle);

            // 2. Known 스타일이 아니면 키워드 추출
            String styleKeyword = null;
            if (abstractedStyle == null) {
                styleKeyword = extractStyleKeywords(prompt);
                log.info("=== 스타일 키워드 추출: {} ===", styleKeyword);
            }

            // 3. Vision: Identity Kernel 추출
            String identityKernel = extractIdentityKernel(imageUrls);

            // === Vision 거부 체크 ===
            if (isRefusal(identityKernel)) {
                log.warn("=== Vision 거부됨, 즉시 종료 ===");
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "이미지 분석이 정책상 제한되었습니다. 다른 이미지로 시도해주세요."
                );
            }

            // 4. DALL-E 프롬프트 생성
            String dallePrompt;

            if (abstractedStyle != null && isKnownAbstractedStyle(abstractedStyle)) {
                // Known Style: 기존 Composer 사용
                dallePrompt = composePromptWithStyle(identityKernel, abstractedStyle, prompt);

                // Composer 거부 시 템플릿으로 대체
                if (isRefusal(dallePrompt)) {
                    log.warn("=== Composer 거부, 템플릿 사용 ===");
                    dallePrompt = composePromptForUnknownStyle(identityKernel, abstractedStyle, prompt);
                }
            } else {
                // Unknown Style: 직접 템플릿 사용 (Composer 우회)
                dallePrompt = composePromptForUnknownStyle(identityKernel, styleKeyword, prompt);
            }

            // === 최종 프롬프트 검증 ===
            if (isRefusal(dallePrompt)) {
                log.error("=== 프롬프트 생성 완전 실패 ===");
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "이미지 생성이 정책상 제한되었습니다. 다른 스타일이나 이미지로 시도해주세요."
                );
            }

            // 5. DALL-E 이미지 생성
            String resultImageUrl;
            TransformationLevel level = TransformationLevel.fromStyle(detectedStyle);

            try {
                if (level == TransformationLevel.HEAVY || level == TransformationLevel.LIGHT) {
                    resultImageUrl = imageService.generateImageHD(dallePrompt);
                } else {
                    resultImageUrl = imageService.generateImageHD(dallePrompt);  // Unknown도 HD
                }
            } catch (Exception e) {
                log.error("=== DALL-E 실패 ===");
                log.error("에러: {}", e.getMessage());
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "이미지 생성이 정책상 제한되었습니다. 다른 스타일이나 이미지로 시도해주세요."
                );
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
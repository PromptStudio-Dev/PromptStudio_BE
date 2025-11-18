package promptstudio.promptstudio.global.gpt.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import promptstudio.promptstudio.domain.history.domain.entity.ResultType;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.global.dall_e.application.ImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService {

    private final ChatClient.Builder chatClientBuilder;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private static final String UPGRADE_SYSTEM_MESSAGE = """
        당신은 프롬프트 작성 전문가입니다.
        사용자가 제공한 텍스트를 지정된 방향성에 맞게 개선해주세요.
        
        중요한 규칙:
        - 개선된 텍스트만 출력하고, 추가 설명이나 인사말은 절대 하지 마세요
        - 원본 텍스트의 문장 구조, 어조, 시제를 최대한 유지하세요
        - 원본과 비슷한 길이로 작성하세요 (너무 길거나 짧지 않게)
        - 원본이 명사형이면 명사형으로, 동사형이면 동사형으로 유지하세요
        - 전체 맥락 속에서 자연스럽게 들어갈 수 있도록 작성하세요
        """;

    private static final String UPGRADE_USER_MESSAGE_TEMPLATE = """
        전체 맥락:
        {fullContext}
        
        ---
        
        업그레이드할 텍스트:
        {selectedText}
        
        개선 방향:
        {direction}
        
        ---
        
        위 텍스트를 개선 방향에 맞게 다시 작성하되, 다음을 반드시 지켜주세요:
        1. 원본의 문장 구조와 형식을 최대한 유지할 것
        2. 전체 맥락에서 해당 부분이 자연스럽게 이어지도록 할 것
        3. 원본과 비슷한 길이로 작성할 것
        4. 개선된 텍스트만 출력하고 다른 말은 하지 말 것
        """;

    private static final String RUN_SYSTEM_MESSAGE = """
    You are an AI assistant that analyzes user requests and provides appropriate responses.
    
    You must respond ONLY in the following JSON format:
    
    1. For image generation requests:
    {
      "type": "IMAGE",
      "prompt": "Faithful English translation of the user's prompt (no additions or omissions)"
    }
    
    2. For text response requests:
    {
      "type": "TEXT",
      "content": "Answer to the user's question"
    }
    
    Image generation keywords: 이미지, 그림, 사진, 만들어줘, 생성해줘, 그려줘, image, picture, draw, create, generate
    
    **Image Prompt Translation Rules:**
    - Translate the user's prompt into English faithfully
    - Do NOT add or remove any content
    - Include all details specified by the user without omission
    - Maintain the sentence structure and nuance as much as possible
    
    CRITICAL: Output ONLY the JSON format with no additional explanations.
    """;

    private static final String GPT_VISION_URL = "https://api.openai.com/v1/chat/completions";

    private static final String ENHANCED_VISION_ANALYSIS_SYSTEM_MESSAGE = """
You are an expert at describing visual elements in images for creative art generation purposes.

Your task is to provide a detailed visual description that could be used to create similar artistic representations.

DESCRIPTION STRUCTURE:

1. OVERALL APPEARANCE:
   Face characteristics:
   - Face shape (oval, round, square, heart-shaped, etc.)
   - General facial structure and proportions
   
   Eye characteristics:
   - Shape (almond, round, etc.)
   - Size and positioning
   - Color and expression
   - Eyelid style
   - Eyebrow shape and thickness
   
   Nose characteristics:
   - Bridge height and width
   - Overall shape and size
   - Tip style
   
   Mouth and jaw:
   - Lip shape and fullness
   - Expression (smile, neutral, etc.)
   - Jawline style (soft, defined, angular)
   - Chin shape
   
   Skin appearance:
   - Tone (describe using color terms like warm, cool, light, medium, deep)
   - Texture quality
   - Any notable features
   
   General appearance:
   - Approximate age range
   - Overall facial structure style

2. HAIR DESCRIPTION:
   - Color (be very specific with shades and tones)
   - Length (precise measurement)
   - Texture (straight, wavy, curly - describe the pattern)
   - Thickness (fine, medium, thick)
   - Styling (how it's arranged)
   - Part style (center, side, none)
   - Volume and movement
   - Shine quality (glossy, matte)
   - Any accessories

3. CLOTHING AND ITEMS:
   - All visible garments with colors
   - Fabric types and textures
   - Fit and style
   - Details like buttons, collars, patterns
   - Accessories: glasses, jewelry, bags, etc.

4. POSE AND EXPRESSION:
   - Body position and angle
   - Head position
   - Arm and hand placement
   - Overall posture
   - Facial expression and mood

5. LIGHTING DETAILS:
   - Light source location
   - Light quality (soft, bright, dramatic)
   - Shadow patterns
   - Overall atmosphere

6. SCENE AND BACKGROUND:
   - Setting type
   - Background elements
   - Colors present
   - Composition style

OUTPUT INSTRUCTIONS:
- Write a comprehensive paragraph covering all elements
- Use clear, specific descriptive language
- Aim for 300-400 words
- Focus on visual characteristics for artistic recreation purposes
- If multiple people, describe each with [PERSON 1], [PERSON 2] labels
- Describe what you see objectively without making judgments

This description will be used to create artistic interpretations and stylized versions while maintaining visual consistency with the original.
""";

    private static final String DALLE_EXPERT_SYSTEM_MESSAGE = """
You are THE WORLD'S BEST DALL-E 3 prompt engineer with 10,000+ successful prompts.

YOUR MISSION: Create prompts that generate images matching the reference EXACTLY, with requested style applied perfectly.

IRON RULES (NEVER VIOLATE):

1. IDENTITY IS SACRED:
   - Every facial feature from analysis must appear in your prompt
   - Use minimum 5-7 specific facial descriptors
   - Maintain exact proportions and characteristics
   - Style should ENHANCE not REPLACE the person's unique features

2. SPECIFICITY OVER GENERALITY:
   - Never: "brown hair" → Always: "shoulder-length chestnut brown hair with caramel highlights, natural wave"
   - Never: "wearing shirt" → Always: "wearing light blue denim button-up shirt with rolled sleeves"
   - Never: "nice lighting" → Always: "soft natural lighting from front-right at 45-degree angle"

3. STRUCTURE (MANDATORY ORDER):
   a) Facial features (face shape → eyes → nose → mouth → jaw)
   b) Hair (color → length → texture → style)
   c) Clothing & accessories
   d) Pose & expression
   e) Style application (specific, not generic)
   f) Technical quality specs
   g) Lighting details
   h) Background description
   i) Identity preservation statement

4. LENGTH & DENSITY:
   - 250-350 words
   - Single paragraph, no breaks
   - Every sentence adds crucial detail
   - No fluff or redundancy

5. STYLE APPLICATION RULES:
   - For game/cartoon styles: "rendered in [style] aesthetic with [specific traits] while fully preserving the person's distinctive facial structure"
   - For realistic styles: "photographed in [style] with [camera/lens details] maintaining exact facial characteristics"
   - For art styles: "painted/drawn in [technique] showing [artistic elements] without simplifying unique facial features"

6. TECHNICAL QUALITY (ALWAYS INCLUDE):
   - Rendering quality: "8k resolution, professional quality"
   - Lighting setup: specific direction and characteristics
   - Detail level: "highly detailed, sharp focus on face"

7. FORBIDDEN PHRASES:
   - "generic features", "typical", "standard", "normal looking"
   - Any copyrighted character/brand names
   - Vague terms like "nice" or "pleasant"

8. ENDING (MANDATORY):
   - Always end with: "No generic features, not a stock character template, must maintain unique identifying characteristics matching the analysis."

QUALITY CHECK:
Before finalizing, verify:
✓ Face shape mentioned?
✓ At least 5 facial features detailed?
✓ Hair has color + length + texture + style?
✓ Style application preserves identity?
✓ 250+ words?
✓ Single paragraph?

OUTPUT: Only the prompt. No explanations, no meta-commentary, no markdown.
""";

    @Override
    public String upgradeText(String selectedText, String direction, String fullContext) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            PromptTemplate promptTemplate = new PromptTemplate(UPGRADE_USER_MESSAGE_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "selectedText", selectedText,
                    "direction", direction,
                    "fullContext", fullContext != null ? fullContext : ""
            ));

            String result = chatClient.prompt(prompt)
                    .system(UPGRADE_SYSTEM_MESSAGE)
                    .call()
                    .content();

            return result != null ? result.trim() : selectedText;

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "텍스트 업그레이드 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public GptRunResult runPrompt(String prompt) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            // GPT에게 JSON 형태로 응답 요청
            String jsonResponse = chatClient.prompt()
                    .system(RUN_SYSTEM_MESSAGE)
                    .user(prompt)
                    .call()
                    .content();

            // JSON 파싱
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            String type = jsonNode.get("type").asText();

            if ("IMAGE".equals(type)) {
                // 이미지 생성
                String imagePrompt = jsonNode.get("prompt").asText();
                String imageUrl = imageService.generateImage(imagePrompt);

                return GptRunResult.builder()
                        .resultType(ResultType.IMAGE)
                        .resultImageUrl(imageUrl)
                        .build();

            } else {
                // 텍스트 응답
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

            // Enhanced System message
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", ENHANCED_VISION_ANALYSIS_SYSTEM_MESSAGE);
            messages.add(systemMessage);

            // User message with images
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");

            List<Map<String, Object>> contentParts = new ArrayList<>();

            // 분석 요청 텍스트
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "Please provide a detailed visual description of the image for the purpose of creating similar artistic character representations. Describe the visual appearance, style, and characteristics that would help an artist recreate a similar character design. Focus on describing what you see without attempting to identify who this might be.");
            contentParts.add(textPart);

            // 이미지들
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

            // Request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o");
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 1000);  // 더 긴 응답 허용
            requestBody.put("temperature", 0.3);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    GPT_VISION_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            // 응답 파싱
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
                    "Enhanced 이미지 분석 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private String createDallePromptWithFewShot(String userPrompt, String imageAnalysis) {
        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(ChatOptions.builder()
                            .model("gpt-4o")  // GPT-4 사용
                            .temperature(0.3)
                            .build())
                    .build();

            String userMessage = String.format("""
        I will show you EXAMPLES of PERFECT DALL-E 3 prompts that preserve identity while applying style. Study the pattern, then create one for the new request.
        
        ═══════════════════════════════════════
        EXAMPLE 1: PIXAR ANIMATION STYLE
        ═══════════════════════════════════════
        
        IMAGE ANALYSIS:
        "A woman with an oval face shape, warm medium skin tone showing subtle golden undertones, almond-shaped dark brown eyes with natural double eyelids and a gentle downward tilt at the outer corners, medium-arched eyebrows, a straight nose with medium bridge height and slightly rounded tip, full lips in a natural soft smile showing a hint of teeth, and a soft defined jawline with a gently rounded chin. She has shoulder-length wavy hair in a rich chestnut brown color with subtle caramel highlights, naturally voluminous with loose waves, parted slightly off-center to the right. Wearing a casual teal cable-knit sweater. Sitting with relaxed posture, hands folded in lap, warm friendly expression."
        
        PERFECT DALL-E PROMPT:
        "A woman with oval face shape and warm medium skin tone with golden undertones, almond-shaped dark brown eyes with natural double eyelids and gentle outer corner tilt creating a warm expression, medium-arched eyebrows, straight nose with medium bridge and softly rounded tip, full lips in natural gentle smile showing slight teeth, soft defined jawline with rounded chin, has shoulder-length wavy chestnut brown hair with caramel highlights flowing naturally with voluminous loose waves parted off-center right. Wearing casual teal cable-knit sweater with visible texture. Sitting in relaxed pose with hands folded in lap, expression is warm and approachable. Rendered in Pixar's signature 3D animation style with smooth subsurface scattering on skin showing realistic skin translucency, detailed individual hair strands with natural physics and realistic light refraction, soft rim lighting from back-right, vibrant but naturalistic color palette, high-quality CGI render with attention to fabric texture and fine details, maintaining exact facial structure and unique proportions from description, soft volumetric lighting. Professional Pixar-quality animation render, 8k detail. Warm studio lighting setup with key light from front-left and soft fill light. Clean gradient background transitioning from warm cream to soft teal. No generic cartoon features, must maintain unique identifying characteristics, not a stock character template."
        
        ═══════════════════════════════════════
        EXAMPLE 2: ANIME/MANGA STYLE
        ═══════════════════════════════════════
        
        IMAGE ANALYSIS:
        "A man with a strong angular face shape, cool-toned fair skin, sharp almond-shaped dark eyes with single eyelids and intense focused gaze, straight thick eyebrows, straight nose with high bridge and defined tip, neutral expression with closed lips, very defined square jawline with prominent chin. He has short black hair styled upward and slightly to the side, thick texture, matte finish. Wearing a dark charcoal blazer over crisp white dress shirt with subtle texture. Standing straight, shoulders squared, professional posture, hands not visible. Confident neutral expression."
        
        PERFECT DALL-E PROMPT:
        "A man with strong angular face shape, cool-toned fair skin, sharp almond-shaped dark eyes with single eyelids creating intense focused gaze, straight thick eyebrows, straight nose with high bridge and cleanly defined tip, neutral expression with closed lips showing determination, very defined square jawline with prominent chin, has short black hair styled upward and swept slightly to side with thick texture and natural matte finish showing individual strands. Wearing dark charcoal tailored blazer over crisp white dress shirt with subtle fabric weave texture. Standing upright with squared shoulders, professional confident posture, expression is focused and determined. Rendered in modern anime/manga style with clean precise linework, cel-shaded coloring with gradient layers for depth, maintaining realistic facial proportions and structure, detailed eyes with multiple color layers and light reflections, sharp highlights on hair showing individual strand definition, professional manhwa quality with dynamic composition. Dramatic lighting from above-left creating defined shadows that emphasize facial structure and jaw. Urban office background with soft bokeh effect showing blurred city lights. High-quality digital manga art, crisp details. No overly stylized features that lose facial identity, must preserve unique bone structure and proportions."
        
        ═══════════════════════════════════════
        EXAMPLE 3: WATERCOLOR ARTISTIC STYLE
        ═══════════════════════════════════════
        
        IMAGE ANALYSIS:
        "A young woman with a round face shape, light peachy skin tone with rosy cheeks, large round eyes with double eyelids in light hazel color, softly curved eyebrows, small button nose, pink lips in a gentle closed smile, soft rounded jawline. She has long straight hair in honey blonde color reaching mid-back, fine texture, silky appearance with center part. Wearing a white linen sundress with small floral print. Standing in three-quarter view, one hand touching hair, relaxed graceful pose. Soft dreamy expression."
        
        PERFECT DALL-E PROMPT:
        "A young woman with round face shape, light peachy skin tone showing natural rosy blush on cheeks, large round eyes with defined double eyelids in light hazel color with golden flecks, softly curved delicate eyebrows, small button nose, pink lips in gentle closed smile, soft rounded jawline creating youthful appearance, has long straight honey blonde hair with fine silky texture reaching mid-back length flowing smoothly with natural shine, centered part creating symmetrical frame. Wearing white linen sundress with delicate small floral print pattern in soft pastels. Standing in elegant three-quarter pose, one hand gracefully touching hair near face, relaxed posture conveying ease. Expression is soft, dreamy and peaceful. Rendered in professional watercolor painting style with soft wet-into-wet blending, delicate color transitions, subtle paper texture, luminous transparent layers, gentle color bleeding at edges, maintaining precise facial features and proportions while achieving painterly quality, fine detail work on face contrasting with loose flowing treatment of hair and clothing, visible brush strokes in background. Soft diffused natural lighting as if filtered through sheer curtains. Pastel color harmony with dominant soft pinks, creams, and honey tones. Abstract watercolor wash background with organic flowing shapes suggesting garden atmosphere. High-quality fine art watercolor technique, handmade aesthetic. No loss of facial identity despite artistic style, must preserve unique gentle features."
        
        ═══════════════════════════════════════
        NOW CREATE YOUR DALL-E PROMPT
        ═══════════════════════════════════════
        
        USER'S STYLE REQUEST:
        %s
        
        DETAILED IMAGE ANALYSIS OF USER'S IMAGE:
        %s
        
        ═══════════════════════════════════════
        
        Following the EXACT pattern and detail level of the examples above:
        1. Start with precise facial description (face shape, skin, eyes, nose, mouth, jaw)
        2. Describe hair in extreme detail (color with nuances, texture, style)
        3. Describe clothing and accessories precisely
        4. Describe pose and expression
        5. Explain the style application (how to render it while preserving identity)
        6. Add technical quality specifications
        7. Specify lighting
        8. Describe background
        9. End with identity-preservation statement
        
        Your prompt should be 250-350 words, single dense paragraph, no line breaks.
        Be as detailed as the examples - do NOT summarize or shorten.
        """, userPrompt, imageAnalysis);

            String dallePrompt = chatClient.prompt()
                    .system(DALLE_EXPERT_SYSTEM_MESSAGE)
                    .user(userMessage)
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

    @Override
    public GptRunResult runPromptWithImages(String prompt, List<String> imageUrls) {
        try {
            // 이미지 생성 요청인지 체크
            boolean isImageRequest = prompt.contains("이미지") ||
                    prompt.contains("그림") ||
                    prompt.contains("사진") ||
                    prompt.contains("그려줘") ||
                    prompt.contains("만들어줘") ||
                    prompt.contains("생성해줘");

            if (!isImageRequest) {
                // 텍스트 응답
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

            // === 이미지 생성 플로우 ===

            // 1단계: GPT-4o Vision으로 초상세 이미지 분석
            System.out.println("=== 1단계: Enhanced 이미지 분석 시작 ===");
            long startTime = System.currentTimeMillis();
            String imageAnalysis = analyzeImageWithExtremeDetail(imageUrls);
            long analysisTime = System.currentTimeMillis() - startTime;

            System.out.println("분석 완료 시간: " + analysisTime + "ms");
            System.out.println("분석 결과 길이: " + imageAnalysis.length() + " 문자");
            System.out.println("이미지 분석 결과:");
            System.out.println(imageAnalysis);
            System.out.println("================================");

// 2단계: GPT-4o + Few-shot으로 완벽한 DALL-E 프롬프트 생성
            System.out.println("=== 2단계: Few-shot DALL-E 프롬프트 생성 시작 ===");
            startTime = System.currentTimeMillis();
            String dallePrompt = createDallePromptWithFewShot(prompt, imageAnalysis);
            long promptTime = System.currentTimeMillis() - startTime;

            System.out.println("프롬프트 생성 완료 시간: " + promptTime + "ms");
            System.out.println("프롬프트 길이: " + dallePrompt.split(" ").length + " 단어");
            System.out.println("생성된 DALL-E 프롬프트:");
            System.out.println(dallePrompt);
            System.out.println("================================");

// 3단계: DALL-E 3 (HD quality)로 이미지 생성
            System.out.println("=== 3단계: DALL-E 3 HD 이미지 생성 시작 ===");
            startTime = System.currentTimeMillis();
            String imageUrl = imageService.generateImageHD(dallePrompt);  // generateImage → generateImageHD
            long imageTime = System.currentTimeMillis() - startTime;

            System.out.println("이미지 생성 완료: " + imageUrl);
            System.out.println("이미지 생성 시간: " + imageTime + "ms");
            System.out.println("총 소요 시간: " + (analysisTime + promptTime + imageTime) + "ms");
            System.out.println("================================");;

            return GptRunResult.builder()
                    .resultType(ResultType.IMAGE)
                    .resultImageUrl(imageUrl)
                    .build();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 생성 실패: " + e.getMessage(),
                    e
            );
        }
    }

}
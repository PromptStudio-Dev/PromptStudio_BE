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
import org.springframework.http.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService {

    private final ChatClient.Builder chatClientBuilder;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    // Known 스타일 정의
    private static final Set<String> KNOWN_STYLES = Set.of(
            "animal_crossing",
            "pixar",
            "ghibli"
    );

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
You are an expert at analyzing human faces and appearance for artistic character recreation.

Your task is to provide EXTREMELY DETAILED visual analysis that captures UNIQUE identifying features.

CRITICAL REQUIREMENTS:

1. FACIAL STRUCTURE (MANDATORY DETAIL):
   Face shape:
   - Exact shape (oval, round, square, heart-shaped, diamond, oblong)
   - Face proportions (width-to-length ratio)
   - Bone structure (prominent or soft)
   
   Eyes (MOST IMPORTANT):
   - Exact shape (almond, round, hooded, monolid, double eyelid)
   - Size relative to face (small, medium, large)
   - Position and spacing (close-set, wide-set, average)
   - Eye color (be VERY specific: dark brown, light brown, hazel with gold flecks, etc.)
   - Eyelid type (single, double, partial double)
   - Eye angle (upturned, downturned, straight)
   - Eyebrow shape (straight, arched, angled)
   - Eyebrow thickness and color
   - Distance between eyes and eyebrows
   
   Nose:
   - Bridge height (high, medium, low, flat)
   - Bridge width (narrow, medium, wide)
   - Nose tip shape (rounded, pointed, bulbous, upturned, downturned)
   - Nostril size and shape
   - Overall nose length
   
   Mouth and Lips:
   - Upper lip shape and fullness (thin, medium, full, cupid's bow shape)
   - Lower lip shape and fullness
   - Lip color (natural pink, coral, mauve, etc.)
   - Mouth width relative to nose
   - Teeth visibility when smiling
   - Smile type (closed, showing teeth, gummy, etc.)
   
   Jaw and Chin:
   - Jawline definition (soft, moderately defined, very defined, angular)
   - Jaw shape (square, rounded, pointed, V-shaped)
   - Chin shape (rounded, pointed, flat, prominent, recessed)
   - Chin size and projection
   
   Cheeks:
   - Cheekbone prominence (flat, slightly visible, very prominent)
   - Cheek fullness (hollow, average, full, chubby)
   - Apple cheeks present or not

2. SKIN APPEARANCE:
   - Exact skin tone (use specific descriptors: porcelain, fair, light, medium, tan, deep, etc.)
   - Undertones (cool/pink, warm/golden, neutral, olive)
   - Skin texture (smooth, pores visible, matte, dewy)
   - Any distinctive marks (moles, beauty marks - location and size)

3. HAIR (CRITICAL DETAIL):
   Color:
   - Primary color with specific shade (jet black, dark brown, chestnut, honey blonde, etc.)
   - Any highlights, lowlights, or color variations
   - Natural or dyed appearance
   
   Length:
   - Exact length (chin-length, shoulder-length, mid-back, waist-length)
   - Measured in approximate inches/cm if possible
   
   Texture and Pattern:
   - Straight (bone straight, slightly straight)
   - Wavy (loose waves, beach waves, S-waves)
   - Curly (loose curls, tight curls, coils)
   - Frizz level
   
   Style Details:
   - Part location (center, side, off-center, no part)
   - Layers or one-length
   - Bangs/fringe (type and length)
   - How it frames the face
   - Volume (flat, medium, voluminous)
   - Movement and flow
   - Shine quality (glossy, matte, natural sheen)

4. CLOTHING AND ACCESSORIES:
   - Every visible garment with exact colors
   - Fabric types and textures
   - Fit and style details
   - Neckline, sleeves, buttons, patterns
   - Jewelry (type, material, placement)
   - Other accessories (watches, bags, glasses, etc.)

5. POSE AND EXPRESSION:
   - Exact head angle (straight-on, three-quarter, side)
   - Head tilt direction and degree
   - Eye gaze direction
   - Mouth expression (slight smile, big smile, neutral, etc.)
   - Hand position if visible
   - Shoulder position
   - Overall body language and mood

6. ETHNICITY INDICATORS (FOR ACCURACY):
   - East Asian features (if present): describe specific characteristics
   - Note distinctive regional features that help maintain accuracy

OUTPUT FORMAT:
- Write as detailed flowing paragraphs (NOT bullet points)
- Use 400-500 words
- Include EVERY detail mentioned above
- Be specific with measurements and comparisons
- Focus on UNIQUE features that distinguish this person
- Use precise descriptive language

This description will be used to recreate this EXACT person in different art styles.
The more specific you are, the better the artistic recreation will match the original.
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

    // Phase 2: Unknown 스타일 분석용 시스템 메시지
    private static final String STYLE_ANALYZER_SYSTEM_MESSAGE = """
You are a visual style analyzer for image generation.

Given a style name (e.g., "Pokemon", "Zelda", "Genshin Impact"), output CONCRETE, SPECIFIC visual characteristics.

OUTPUT FORMAT:

PROPORTIONS:
- Head-to-body ratio (e.g., 1:7 realistic, 2:1 chibi, 1:4 stylized)
- Body structure (realistic, simplified, exaggerated)
- Limb style (realistic joints, simplified, stubby)

RENDERING TYPE:
- Dimension (2D flat, 2.5D, 3D)
- Surface style (cel-shaded, realistic, painterly, flat color)
- Edge definition (bold outlines, soft edges, no outlines, colored lines)

LINE WORK:
- Outline presence and thickness (none, thin 1px, medium 2px, bold 3-4px)
- Outline color (black, colored, variable)
- Line quality (clean vector, hand-drawn, brush-like)

SHADING STYLE:
- Shading method (flat/no shading, 2-tone cel, gradient, realistic)
- Shadow hardness (hard edges, soft gradient, no shadows)
- Highlight style (sharp anime highlights, soft realistic, minimal)

COLOR PALETTE:
- Saturation level (muted/desaturated, moderate, highly saturated, neon)
- Brightness range (dark, medium, bright, pastel)
- Color temperature (warm, cool, neutral, mixed)
- Color harmony (limited palette, full spectrum, specific dominant colors)

DETAIL LEVEL:
- Overall complexity (minimalist, moderate, highly detailed)
- Texture presence (flat/none, subtle, prominent realistic textures)
- Fine details (simplified shapes, moderate detail, intricate details)

FACIAL FEATURES:
- Eye style (realistic proportion, enlarged anime eyes, stylized)
- Expression rendering (subtle realistic, exaggerated cartoon)
- Feature simplification level (realistic, slightly stylized, highly simplified)

LIGHTING:
- Lighting style (flat even, soft natural, dramatic, stylized)
- Light source clarity (diffused ambient, clear directional, multiple sources)
- Atmosphere (bright cheerful, moody, neutral, dramatic)

TECHNICAL SPECS:
- Typical resolution quality
- Common aspect ratios
- Signature visual elements unique to this style

Be SPECIFIC with measurements and technical terms. Describe what makes this style visually distinctive.

OUTPUT: Clear structured bullet points covering all categories above. 200-300 words total.
""";


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

            String jsonResponse = chatClient.prompt()
                    .system(RUN_SYSTEM_MESSAGE)
                    .user(prompt)
                    .call()
                    .content();

            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            String type = jsonNode.get("type").asText();

            if ("IMAGE".equals(type)) {
                String imagePrompt = jsonNode.get("prompt").asText();
                String imageUrl = imageService.generateImage(imagePrompt);

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
            systemMessage.put("content", ENHANCED_VISION_ANALYSIS_SYSTEM_MESSAGE);
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

            String userMessage = String.format("""
        I will show you EXAMPLES of PERFECT DALL-E 3 prompts that preserve identity while applying style. Study the pattern, then create one for the new request.
        
        ═══════════════════════════════════════
        EXAMPLE 1: ANIMAL CROSSING STYLE
        ═══════════════════════════════════════
        
        IMAGE ANALYSIS:
        "A woman with an oval face shape, warm medium skin tone, almond-shaped dark brown eyes with natural double eyelids, medium-arched eyebrows, straight nose with medium bridge, full lips in natural smile, soft defined jawline with rounded chin. She has shoulder-length wavy chestnut brown hair with caramel highlights, naturally voluminous. Wearing casual teal cable-knit sweater. Sitting with relaxed posture, warm friendly expression."
        
        PERFECT DALL-E PROMPT:
        "A character with oval face shape maintaining recognizable facial structure, warm medium skin tone with soft matte plastic-like surface finish, almond-shaped dark eyes (large and expressive but keeping natural eye shape), medium-arched eyebrows, small simplified nose maintaining overall nose shape, gentle smile with closed mouth, soft rounded jawline. CRITICAL CHIBI PROPORTIONS (MANDATORY): Head-to-body ratio EXACTLY 2.5:1, head occupies 65%% of total height, body is 20%% tiny compact torso, arms are short stubby cylinders with NO visible elbows ending in simple rounded mitt-like hands with 3 subtle finger segments, legs are 15%% stubby with NO visible knees, NO visible neck connecting head directly to shoulders. Has shoulder-length simplified wavy hair in chestnut brown color with subtle caramel highlights, simplified into chunky rounded sections, naturally voluminous. Wearing casual teal sweater simplified into basic geometric shapes with minimal folds. Sitting pose adapted to chibi proportions with short arms folded. Rendered in Nintendo Animal Crossing New Horizons life simulation game aesthetic: Low-polygon 3D model with GameCube/Wii era quality, soft matte plastic surface shader, rounded edges everywhere, pastel color palette, minimal polygon count creating simple geometric forms, no realistic textures, soft ambient lighting with no harsh shadows, clean simple silhouette. Clean solid color background. 8k resolution, professional quality, soft even lighting. Must maintain recognizable facial features and hair style from original despite chibi transformation, not a generic chibi template."
        
        ═══════════════════════════════════════
        EXAMPLE 2: PIXAR ANIMATION STYLE
        ═══════════════════════════════════════
        
        IMAGE ANALYSIS:
        "A woman with an oval face shape, warm medium skin tone with golden undertones, almond-shaped dark brown eyes with natural double eyelids and gentle outer corner tilt, medium-arched eyebrows, straight nose with medium bridge and rounded tip, full lips in natural gentle smile, soft defined jawline with rounded chin. She has shoulder-length wavy chestnut brown hair with caramel highlights, naturally voluminous. Wearing casual teal cable-knit sweater. Sitting in relaxed pose with warm approachable expression."
        
        PERFECT DALL-E PROMPT:
        "A woman with oval face shape and warm medium skin tone with golden undertones, almond-shaped dark brown eyes with natural double eyelids and gentle outer corner tilt creating warm expression, medium-arched eyebrows, straight nose with medium bridge and softly rounded tip, full lips in natural gentle smile showing slight teeth, soft defined jawline with rounded chin, has shoulder-length wavy chestnut brown hair with caramel highlights flowing naturally with voluminous loose waves. Wearing casual teal cable-knit sweater with visible texture. Sitting in relaxed pose, expression is warm and approachable. Rendered in Pixar's signature 3D animation style with smooth subsurface scattering on skin showing realistic skin translucency, detailed individual hair strands with natural physics and realistic light refraction, soft rim lighting from back-right, vibrant but naturalistic color palette, high-quality CGI render with attention to fabric texture and fine details, maintaining exact facial structure and unique proportions from description, soft volumetric lighting. Professional Pixar-quality animation render, 8k detail. Warm studio lighting setup with key light from front-left and soft fill light. Clean gradient background. No generic cartoon features, must maintain unique identifying characteristics."
        
        ═══════════════════════════════════════
        EXAMPLE 3: STUDIO GHIBLI 2D ANIMATION STYLE
        ═══════════════════════════════════════
        
        IMAGE ANALYSIS:
        "A young woman with oval face shape, cool-toned fair skin, almond-shaped dark brown eyes with double eyelids and gentle expression, straight medium-thickness eyebrows, small refined nose with medium bridge and softly rounded tip, natural pink lips in subtle closed smile, soft defined jawline with gently pointed chin. She has long straight black hair reaching mid-back with natural shine and slight layering, center-parted flowing smoothly. Wearing light blue casual button-up shirt. Head tilted slightly, one hand touching face near chin, relaxed contemplative pose with warm gentle expression."
        
        PERFECT DALL-E PROMPT:
        "A young woman with oval face shape maintaining exact facial structure, cool-toned fair skin with subtle pink undertones, almond-shaped dark brown eyes with natural double eyelids creating gentle warm gaze, straight medium-thickness eyebrows positioned naturally above eyes, small refined nose with medium bridge height and softly rounded tip, natural pink lips in subtle closed smile, soft defined jawline with gently pointed chin creating elegant profile, has long straight black hair reaching mid-back length with natural glossy shine and subtle layering flowing smoothly around face, center-parted creating symmetrical frame, individual strands visible with organic hand-drawn quality. Wearing light blue casual button-up shirt with collar and visible buttons, fabric has soft folds. Head tilted slightly to side, one hand gracefully touching face near chin area, relaxed contemplative pose conveying warmth and gentleness. Expression is soft, peaceful and introspective with hint of gentle smile.
        
        CRITICAL 2D ANIMATION STYLE REQUIREMENTS (ABSOLUTELY MANDATORY - DALL-E MUST FOLLOW):
        This is PURE 2D FLAT HAND-DRAWN TRADITIONAL ANIMATION. NOT 3D rendering, NOT CGI, NOT photorealistic.
        
        Style Characteristics:
        - Studio Ghibli / Hayao Miyazaki 2D hand-drawn animation aesthetic ONLY
        - Traditional cel animation technique with painted cels
        - Flat 2D composition with NO 3D depth rendering
        - Hand-painted watercolor texture throughout entire image
        - Organic hand-drawn linework with natural imperfections showing artist's hand
        - Soft delicate line quality, NOT bold outlines
        - Lines have slight variation in thickness appearing hand-drawn
        - Colors applied in flat layers with gentle gradient transitions
        - Visible soft paper grain texture throughout
        - Painterly quality, NOT digital smooth rendering
        
        Color Palette and Atmosphere:
        - Warm golden-hour lighting with soft amber and cream tones
        - Watercolor wash effects with colors bleeding slightly at edges
        - Nostalgic dreamy atmosphere with soft diffused lighting
        - Dominant warm yellows, soft teals, and cream colors
        - Gentle gradient sky suggesting sunset or golden hour
        - Soft rosy blush on cheeks rendered as transparent watercolor wash
        - Hair has soft highlights painted with translucent layers
        - Overall color harmony is warm, peaceful, and contemplative
        
        Rendering Technique:
        - Traditional animation frame quality as seen in Spirited Away, Howl's Moving Castle, Whisper of the Heart
        - Every element painted with watercolor transparency
        - Soft edges throughout, NO hard digital edges
        - Visible brush stroke texture in background
        - Paper texture visible creating handmade aesthetic
        - Film grain texture overlay suggesting traditional animation
        - Slightly rough texture showing traditional media
        - NO smooth 3D subsurface scattering
        - NO realistic skin rendering
        - NO 3D volumetric lighting
        - NO CGI or computer-generated appearance
        
        Background and Composition:
        - Soft bokeh effect suggesting European cityscape with domed buildings and towers in warm golden tones
        - Background painted with loose watercolor technique
        - Blurred atmospheric perspective with warm amber sky
        - Character elevated with aerial city view behind
        - Dreamy soft-focus background
        - Everything has 2D painted quality
        
        Lighting:
        - Soft diffused golden-hour sunlight from side
        - Gentle rim lighting on hair painted as warm highlights
        - NO harsh shadows, only soft gradient shadow areas
        - Warm color temperature throughout
        - Ethereal dreamy lighting quality
        - Light appears painted, not rendered
        
        ABSOLUTE REQUIREMENTS:
        ✓ 2D FLAT hand-drawn animation - NO 3D RENDERING
        ✓ Watercolor painted texture - NO photorealistic skin
        ✓ Hand-drawn organic lines - NO clean vector lines  
        ✓ Traditional cel animation - NO CGI appearance
        ✓ Ghibli/Miyazaki aesthetic - reference Spirited Away visual style
        ✓ Paper grain texture visible
        ✓ Painterly quality throughout
        
        This is a traditional 2D hand-painted animation frame, NOT a 3D model or CGI render.
        Must look like it was painted by Studio Ghibli artists using traditional animation techniques.
        No 3D depth rendering, no realistic 3D skin, no modern CGI effects.
        Maintain exact facial features and identity while rendering in pure 2D animated painting style."
        
        ═══════════════════════════════════════
        NOW CREATE YOUR DALL-E PROMPT
        ═══════════════════════════════════════
        
        REQUESTED STYLE: %s
        
        USER'S STYLE REQUEST:
        %s
        
        DETAILED IMAGE ANALYSIS:
        %s
        
        ═══════════════════════════════════════
        
        Following the EXACT pattern and detail level of the %s example above:
        1. Preserve all facial features from analysis
        2. Apply style-specific characteristics with EXTREME detail
        3. For Ghibli: EMPHASIZE 2D flat, hand-drawn, watercolor, NOT 3D
        4. Include all technical rendering requirements
        5. Maintain identity throughout transformation
        6. 300-400 words, single dense paragraph
        
        OUTPUT ONLY THE DALL-E PROMPT.
        """, style.toUpperCase(), userPrompt, imageAnalysis, style.toUpperCase());

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

            String userMessage = String.format("""
            Create a DALL-E 3 prompt that maintains the original photographic/realistic style.
            
            USER'S REQUEST:
            %s
            
            DETAILED IMAGE ANALYSIS:
            %s
            
            ═══════════════════════════════════════
            
            TASK:
            Create a single detailed DALL-E 3 prompt (250-350 words) that:
            
            1. PRESERVES EXACT CHARACTER APPEARANCE:
               - All facial features (face shape, eyes, nose, mouth, jaw)
               - Exact hair description (color, length, texture, style)
               - Clothing and accessories
               - Pose and expression
            
            2. MAINTAINS REALISTIC/PHOTOGRAPHIC STYLE:
               - Keep natural realistic proportions
               - Use photographic lighting and rendering
               - Realistic skin texture and details
               - Natural color palette
               - High-quality photography aesthetic
            
            3. FULFILL USER'S SPECIFIC REQUEST:
               - If user asks to "remove background" → describe with clean/simple background
               - If user asks to "change lighting" → adjust lighting description
               - If user asks for specific modifications → apply only those changes
               - Otherwise maintain everything as close to original as possible
            
            4. TECHNICAL QUALITY:
               - "Professional photography"
               - "High resolution, sharp focus"
               - "Natural lighting"
               - Specific camera/lens details if appropriate
            
            CRITICAL RULES:
            - NO cartoon, anime, or artistic stylization unless requested
            - Maintain photorealistic quality
            - Single dense paragraph, no line breaks
            - End with: "Photorealistic style maintaining exact identifying characteristics from analysis."
            
            OUTPUT ONLY THE DALL-E PROMPT.
            """, userPrompt, imageAnalysis);

            String dallePrompt = chatClient.prompt()
                    .system(DALLE_EXPERT_SYSTEM_MESSAGE)
                    .user(userMessage)
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

            String userMessage = String.format("""
            Analyze the visual characteristics of "%s" style for image generation purposes.
            
            Provide specific, concrete details about:
            - Proportions and body structure
            - Rendering method and surface treatment
            - Line work and outlines
            - Shading and lighting approach
            - Color palette characteristics
            - Level of detail and simplification
            - Distinctive visual signatures
            
            Be extremely specific with technical terms and measurements.
            This will be used to generate images in this style.
            """, styleName);

            String styleAnalysis = chatClient.prompt()
                    .system(STYLE_ANALYZER_SYSTEM_MESSAGE)
                    .user(userMessage)
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

            String userMessage = String.format("""
            Create a DALL-E 3 prompt that combines character identity with style characteristics.
            
            ═══════════════════════════════════════
            STYLE: %s
            ═══════════════════════════════════════
            
            STYLE VISUAL CHARACTERISTICS:
            %s
            
            ═══════════════════════════════════════
            CHARACTER DESCRIPTION (MUST PRESERVE):
            ═══════════════════════════════════════
            
            %s
            
            ═══════════════════════════════════════
            USER'S REQUEST:
            ═══════════════════════════════════════
            
            %s
            
            ═══════════════════════════════════════
            
            TASK:
            Create a single detailed DALL-E 3 prompt (250-350 words) that:
            
            1. PRESERVES CHARACTER IDENTITY:
               - Start with precise facial features (face shape, eyes, nose, mouth, jaw)
               - Include exact hair description (color, length, texture, style)
               - Describe clothing and accessories
               - Maintain pose and expression
            
            2. APPLIES STYLE CHARACTERISTICS:
               - Use the style analysis to determine rendering method
               - Apply appropriate proportions for this style
               - Describe line work and shading as specified
               - Match color palette characteristics
               - Include technical rendering details
            
            3. MAINTAIN QUALITY:
               - Single dense paragraph, no line breaks
               - Specific technical details throughout
               - End with: "Must maintain unique identifying characteristics from analysis, not a generic template."
            
            CRITICAL: The character's face, hair, and body features must be recognizable despite style transformation.
            The style should ENHANCE, not REPLACE the person's unique characteristics.
            
            OUTPUT ONLY THE DALL-E PROMPT.
            """, styleName, styleAnalysis, imageAnalysis, userPrompt);

            String dallePrompt = chatClient.prompt()
                    .system(DALLE_EXPERT_SYSTEM_MESSAGE)
                    .user(userMessage)
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
                    prompt.contains("생성해줘");

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

            // === Phase 1: 스타일 감지 ===
            System.out.println("=== Phase 1: 스타일 감지 시작 ===");
            String detectedStyle = detectRequestedStyle(prompt);
            System.out.println("감지된 스타일: " + (detectedStyle != null ? detectedStyle : "없음"));
            System.out.println("Known 스타일 여부: " + isKnownStyle(detectedStyle));
            System.out.println("================================");

            // === 이미지 분석 ===
            System.out.println("=== 이미지 분석 시작 ===");
            long startTime = System.currentTimeMillis();
            String imageAnalysis = analyzeImageWithExtremeDetail(imageUrls);
            long analysisTime = System.currentTimeMillis() - startTime;

            System.out.println("분석 완료 시간: " + analysisTime + "ms");
            System.out.println("분석 결과 길이: " + imageAnalysis.length() + " 문자");
            System.out.println("================================");

            // === Phase 1: Known 스타일 처리 ===
            String dallePrompt;

            if (isKnownStyle(detectedStyle)) {
                System.out.println("=== Known 스타일 프롬프트 생성 ===");
                System.out.println("스타일: " + detectedStyle);
                startTime = System.currentTimeMillis();

                dallePrompt = createPromptWithKnownStyle(detectedStyle, prompt, imageAnalysis);

                long promptTime = System.currentTimeMillis() - startTime;
                System.out.println("프롬프트 생성 시간: " + promptTime + "ms");
                System.out.println("생성된 프롬프트 길이: " + dallePrompt.split(" ").length + " 단어");
                System.out.println("================================");
            } else if (detectedStyle != null) {
                // Phase 2: Unknown 스타일 처리
                System.out.println("=== Phase 2: Unknown 스타일 처리 ===");
                System.out.println("스타일: " + detectedStyle);

                // 1. 스타일 특성 분석
                startTime = System.currentTimeMillis();
                String styleAnalysis = analyzeStyleCharacteristics(detectedStyle);
                long styleAnalysisTime = System.currentTimeMillis() - startTime;
                System.out.println("스타일 분석 시간: " + styleAnalysisTime + "ms");

                // 2. Unknown 스타일 프롬프트 생성
                startTime = System.currentTimeMillis();
                dallePrompt = createPromptWithUnknownStyle(
                        detectedStyle,
                        styleAnalysis,
                        prompt,
                        imageAnalysis
                );
                long promptTime = System.currentTimeMillis() - startTime;
                System.out.println("프롬프트 생성 시간: " + promptTime + "ms");
                System.out.println("생성된 프롬프트 길이: " + dallePrompt.split(" ").length + " 단어");
                System.out.println("================================");

            } else {
                // 스타일 미지정 - 원본 스타일 유지 (수정!)
                System.out.println("=== 스타일 미지정: 원본 스타일 유지 ===");
                startTime = System.currentTimeMillis();

                dallePrompt = createPromptWithoutStyle(prompt, imageAnalysis);

                long promptTime = System.currentTimeMillis() - startTime;
                System.out.println("프롬프트 생성 시간: " + promptTime + "ms");
                System.out.println("================================");
            }

            // === DALL-E 이미지 생성 ===
            System.out.println("=== DALL-E 3 이미지 생성 ===");
            startTime = System.currentTimeMillis();

            String imageUrl;
            if (detectedStyle == null) {
                // 스타일 미지정 - 사실적/원본 유지
                System.out.println("설정: HD + Natural (사실적)");
                imageUrl = imageService.generateImageRealistic(dallePrompt);
            } else {
                // 스타일 적용 - 애니메이션/게임
                System.out.println("설정: HD + Vivid (화려한)");
                imageUrl = imageService.generateImageHD(dallePrompt);
            }

            long imageTime = System.currentTimeMillis() - startTime;

            System.out.println("이미지 생성 완료: " + imageUrl);
            System.out.println("생성 시간: " + imageTime + "ms");
            System.out.println("================================");

            System.out.println("이미지 생성 완료: " + imageUrl);
            System.out.println("생성 시간: " + imageTime + "ms");
            System.out.println("================================");

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
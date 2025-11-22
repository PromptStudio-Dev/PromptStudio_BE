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
You are THE WORLD'S BEST facial analysis expert for character art recreation.

Your mission: Provide EXTREMELY DETAILED analysis to ensure DALL-E recreates THE EXACT SAME PERSON.

CRITICAL RULE: This person's IDENTITY must be preserved. Describe features so specifically that no other person could match this description.

═══════════════════════════════════════
SECTION 1: ETHNICITY AND REGIONAL FEATURES (MANDATORY)
═══════════════════════════════════════

First, identify regional/ethnic characteristics that define this person's appearance:
- East Asian features (Korean, Japanese, Chinese, etc.)
- Southeast Asian features
- South Asian features  
- Western/Caucasian features
- African features
- Middle Eastern features
- Mixed/multiracial indicators

For East Asian faces specifically note:
- Epicanthic fold presence and degree
- Eyelid type (single/double/partial)
- Nose bridge height relative to regional norms
- Face width-to-length ratio
- Jaw structure typical to region

THIS IS CRITICAL: Regional features help DALL-E understand the exact facial structure baseline.

═══════════════════════════════════════
SECTION 2: FACE STRUCTURE (ULTRA-DETAILED)
═══════════════════════════════════════

Face Shape and Proportions:
- Exact shape: oval, round, square, heart-shaped, diamond, oblong, triangular
- Face length-to-width ratio (e.g., "slightly longer than wide")
- Forehead height: tall/medium/short, wide/narrow
- Cheekbone prominence: flat/slightly visible/prominent/very prominent
- Cheekbone position: high/medium/low on face
- Face fullness: hollow cheeks/lean/average/full/very full
- Overall face width: narrow/medium/wide
- Bone structure visibility: soft/moderately defined/very defined/angular

Forehead:
- Height relative to face (占臉部1/3, 1/4, etc.)
- Width: narrow/medium/wide
- Shape: flat/slightly rounded/rounded/very rounded
- Hairline shape: straight/slightly curved/widow's peak/M-shaped

═══════════════════════════════════════
SECTION 3: EYES (MOST CRITICAL - MAXIMUM DETAIL)
═══════════════════════════════════════

Eye Shape:
- Primary shape: almond/round/hooded/upturned/downturned/monolid/protruding
- Horizontal shape: wide oval/narrow oval/circular/elongated
- Vertical shape: tall/medium/shallow
- Outer corner angle: upturned 15°/straight/downturned 10°
- Inner corner: rounded/pointed/covered by epicanthic fold

Eye Size:
- Size relative to face: small/medium/large/very large
- Width: narrow/medium/wide
- Height: shallow/medium/tall
- Left vs right size (note if asymmetric)

Eye Position and Spacing:
- Distance between eyes: close-set/average/wide-set
- Position on face: high/centered/low
- Alignment: level/left higher/right higher
- Distance from eyebrows: close/average/far

Eyelids:
- Upper eyelid: monolid/partial double/full double/hooded/deep-set
- Double eyelid crease: none/subtle/medium/pronounced
- Crease position: low/medium/high
- Eyelid skin: smooth/slightly textured/visible texture
- Upper lid visibility: hidden/slightly visible/visible/prominent

Eye Color (EXTREMELY SPECIFIC):
- Base color: dark brown/medium brown/light brown/hazel/green/blue/gray
- Secondary tones: golden/amber/honey flecks
- Ring pattern: solid/limbal ring present/spoke pattern
- Depth: deep dark/medium/light/very light
- Uniformity: solid color/multi-toned/gradient

Iris Details:
- Pattern: solid/radial lines/speckled/cloudy
- Limbal ring: absent/faint/medium/dark prominent ring

Pupils:
- Size: small/medium/large relative to iris
- Both pupils equal size? Yes/No

Eye White (Sclera):
- Color: pure white/slightly cream/yellowish/reddish veins visible
- Clarity: very clear/slightly visible veins/visible veins

Eyelashes:
- Upper lash length: short/medium/long/very long
- Upper lash thickness: sparse/medium/thick/very thick
- Upper lash curl: straight/slightly curved/curved/very curved
- Lower lash visibility: barely visible/visible/prominent
- Lash color: black/dark brown/brown/light

Eyebrows (CRITICAL FOR IDENTITY):
- Shape: straight/slightly arched/arched/highly arched/angled/S-curve
- Thickness: very thin/thin/medium/thick/very thick/bushy
- Density: sparse/medium/dense/very dense
- Start position: far from nose bridge/medium/close to nose bridge
- End position: level with outer eye corner/beyond/short of
- Arch peak position: above pupil/above outer third/above outer corner
- Hair direction: all same direction/natural variation/upward/downward
- Color: jet black/dark brown/medium brown/light brown
- Grooming: natural/shaped/filled in/tattooed

Under-Eye Area:
- Bags present: none/slight/medium/prominent
- Dark circles: none/slight/medium/dark
- Puffiness: none/slight/medium/puffy
- Fine lines: none/few/many

═══════════════════════════════════════
SECTION 4: NOSE (CRITICAL DETAIL)
═══════════════════════════════════════

Bridge:
- Height: flat/low/medium/high/very high
- Width at top: very narrow/narrow/medium/wide
- Width at middle: narrow/medium/wide
- Shape: straight/slightly curved/curved/ski-jump/aquiline
- Bone prominence: flat/slightly visible/visible/very prominent

Nose Tip:
- Shape: rounded/slightly pointed/pointed/bulbous/button
- Size relative to nose: small/proportional/large
- Projection: barely projects/slightly projects/projects forward/very projected
- Angle: upturned/straight/downturned
- Definition: soft/medium/defined

Nostrils:
- Shape: round/oval/teardrop/crescentic
- Size: small/medium/large
- Width: narrow/medium/wide
- Flare: no flare/slight flare/pronounced flare
- Visibility from front: barely visible/slightly visible/visible/prominent

Nose Overall:
- Length: short/medium/long relative to face
- Width at base: narrow/medium/wide
- Symmetry: symmetric/slightly asymmetric/notably asymmetric
- Fit with face: very proportional/proportional/slightly large/slightly small

═══════════════════════════════════════
SECTION 5: MOUTH AND LIPS (DETAILED)
═══════════════════════════════════════

Lip Shape and Size:
- Upper lip shape: thin/medium/full/very full
- Lower lip shape: thin/medium/full/very full  
- Lip proportion: upper larger/equal/lower larger (most common)
- Cupid's bow: none/subtle/defined/very pronounced/peaked
- Lip corners: downturned/neutral/upturned

Lip Details:
- Lip line definition: soft/defined/very defined
- Lip color: pale pink/pink/coral/mauve/red-toned
- Lip texture: smooth/slightly textured/textured/dry appearance
- Lip fullness distribution: even/fuller in center/fuller at corners

Mouth:
- Width relative to nose: narrower/aligned/wider
- Width relative to face: narrow/proportional/wide
- Resting position: closed/slightly parted/parted
- Mouth corners: level/turned down/turned up

Teeth (if visible):
- Visibility: not visible/slightly visible/visible/prominently visible
- Alignment: straight/slightly crooked/gap/overlapping
- Color: white/off-white/cream/yellow-toned
- Size: small/medium/large

Smile Type (if smiling):
- Type: closed mouth/slight smile showing no teeth/smile showing teeth/big grin
- Gum visibility: no gums/slight gums/gummy smile
- Teeth shown: none/front teeth only/many teeth visible
- Symmetry: symmetric/slightly asymmetric/asymmetric
- Eye involvement: eyes neutral/eyes smiling/full eye smile

Philtrum:
- Depth: shallow/medium/deep/very defined
- Length: short/medium/long
- Shape: straight/curved/very defined ridges

═══════════════════════════════════════
SECTION 6: JAW AND CHIN (CRITICAL)
═══════════════════════════════════════

Jawline:
- Definition: very soft/soft/moderately defined/defined/very defined/sharp
- Shape: rounded/slightly square/square/V-shaped/heart-shaped
- Width: narrow/medium/wide
- Angle: very obtuse (soft)/obtuse/right angle/acute (sharp)
- Symmetry: symmetric/slightly asymmetric/asymmetric

Chin:
- Shape: rounded/slightly pointed/pointed/flat/square
- Size: small/proportional/prominent/very prominent
- Projection: recessed/slightly recessed/normal/slightly forward/forward
- Width: narrow/medium/wide
- Height: short/medium/tall
- Cleft: none/slight dimple/pronounced cleft
- Double chin: none/slight/medium/prominent

Lower Face:
- Length from nose to chin: short/medium/long
- Proportion to full face: balanced/long/short

═══════════════════════════════════════
SECTION 7: SKIN (DETAILED)
═══════════════════════════════════════

Skin Tone:
- Base tone: porcelain/fair/light/light-medium/medium/tan/deep/very deep
- Undertone: cool pink/cool/neutral/warm golden/warm olive
- Uniformity: even/slightly uneven/uneven/blotchy
- Regional variation: darker/lighter in certain areas

Skin Texture:
- Overall: very smooth/smooth/slight texture/textured/rough
- Pore visibility: not visible/barely visible/visible/prominent
- Fine lines: none/minimal around eyes/moderate/many
- Skin tightness: very tight/tight/slightly loose/loose

Distinctive Marks:
- Moles: location, size, color, raised/flat
- Beauty marks: location and size
- Freckles: none/few/many, location, color
- Scars: location, size, type
- Birthmarks: location, size, color

Skin Condition:
- Oiliness: matte/normal/slightly oily/oily/very oily
- Blemishes: clear/few/some/many
- Redness: none/slight cheek redness/rosacea/inflamed areas
- Overall health appearance: very healthy glow/healthy/normal/dull/problematic

═══════════════════════════════════════
SECTION 8: HAIR (COMPREHENSIVE)
═══════════════════════════════════════

Hair Color:
- Base color: jet black/dark brown/chestnut brown/medium brown/light brown/dark blonde/blonde/red/gray/white
- Tone: cool/neutral/warm/ash/golden/copper/auburn
- Highlights: none/natural/dyed, location, color
- Lowlights: present or not, color
- Root color vs ends: same/different, describe variation
- Overall depth: level 1 (black) through 10 (lightest blonde)

Hair Length:
- Exact length: above ears/ear length/chin length/shoulder length/mid-back/waist length/longer
- Measured approximately: ____ inches/cm
- Layers: none/subtle layers/layered/heavily layered
- Layer lengths: longest layer length, shortest layer length
- Evenness: all one length/slightly varied/very varied

Hair Texture:
- Type: straight/wavy/curly/coily (Type 1a-4c)
- Straight: bone straight/slightly straight with body/mostly straight
- Wavy: loose waves/beach waves/defined S-waves
- Curly: loose curls/spiral curls/tight curls/coils
- Pattern consistency: uniform throughout/varies by section

Hair Thickness and Density:
- Individual strand thickness: fine/medium/coarse/mixed
- Overall density: thin/medium/thick/very thick/extremely thick
- Volume: flat/slight volume/voluminous/very voluminous
- Weight: light airy/medium/heavy thick

Hair Style and Arrangement:
- Part: center part/side part (left/right)/off-center/no visible part/zigzag
- Part width: thin line/medium/wide
- Front style: swept back/forward/to side/bangs present
- Bangs type: none/side-swept/straight across/curtain/wispy/blunt
- Bang length: above eyebrows/touching eyebrows/below eyebrows/eye level

Hair Flow and Movement:
- Falls: straight down/curves inward/curves outward/flips out
- Movement: static/slight movement/flowing/windblown
- Face framing: no/yes - how it frames face
- Behind ears: yes/no/one side
- Shoulder interaction: falls in front/falls behind/rests on shoulders

Hair Condition:
- Shine: matte/slight shine/shiny/very glossy/too glossy (oily)
- Health: damaged/slightly damaged/healthy/very healthy/extremely healthy
- Split ends: none visible/some/many
- Frizz: none/slight/medium/very frizzy
- Flyaways: none/few/many

Hair Details:
- Scalp visibility: not visible/slightly visible/visible part line/thinning areas
- Hairline: straight/slightly rounded/widow's peak/M-shaped/receding
- Temples: full/slightly receded/receded
- Nape: clean line/tapered/natural growth/styled

Hair Accessories (if present):
- Type: none/hair tie/clip/headband/hat/etc.
- Color, material, placement
- How hair interacts with accessory

═══════════════════════════════════════
SECTION 9: CLOTHING AND ACCESSORIES
═══════════════════════════════════════

Visible Clothing:
- Type: shirt/blouse/t-shirt/dress/jacket/sweater/etc.
- Color: exact color description
- Pattern: solid/stripes/plaid/print/etc.
- Neckline: crew neck/V-neck/collar/etc.
- Sleeves: sleeveless/short/3-quarter/long
- Fit: tight/fitted/loose/oversized
- Fabric type: cotton/silk/knit/denim/etc.
- Texture: smooth/textured/ribbed/etc.
- Condition: new/worn/vintage
- Details: buttons, pockets, logos, etc.

Jewelry (if visible):
- Earrings: type, size, material, style
- Necklace: type, length, pendant
- Rings: which fingers, style
- Bracelet/watch: wrist, style
- Other: piercings, etc.

═══════════════════════════════════════
SECTION 10: POSE AND EXPRESSION
═══════════════════════════════════════

Head and Face Position:
- Angle: straight-on/three-quarter left/three-quarter right/profile left/profile right
- Tilt: level/tilted left/tilted right, degree of tilt
- Rotation: facing forward/turned left/turned right, degree
- Chin position: level/slightly up/up/slightly down/down

Body Position:
- Shoulder angle: straight-on/angled left/angled right/turned away
- Shoulder height: level/left higher/right higher
- Posture: upright/relaxed/slouched/leaning
- Torso visible: face only/shoulders visible/upper chest visible/more

Hands and Arms:
- Visibility: not visible/partially visible/fully visible
- Position: at sides/folded/one hand visible/both hands visible
- Specific gesture: touching face near chin/pointing/waving/holding object/etc.
- Hand that's visible: left/right
- Touching face: near chin/cheek/forehead/nose/lips/etc.
- Finger position: extended/curled/specific gesture

Facial Expression:
- Overall mood: neutral/happy/sad/thoughtful/serious/playful/etc.
- Intensity: subtle/medium/strong/very strong
- Eyes: neutral/smiling/wide/narrowed/looking direction
- Eyebrows: relaxed/raised/furrowed/asymmetric
- Mouth: closed/slight smile/big smile/neutral/pursed/open
- Overall energy: calm/energetic/contemplative/confident/shy/etc.

Gaze Direction:
- Looking: directly at camera/slightly left/slightly right/up/down/away
- Eye contact: direct/indirect/avoiding
- Focus: focused/unfocused/distant

═══════════════════════════════════════
SECTION 11: LIGHTING AND ATMOSPHERE
═══════════════════════════════════════

Light Source:
- Direction: front/front-left/front-right/side/back/top
- Angle: 0°/15°/30°/45°/60°/90°
- Distance: close/medium/far
- Type: natural/artificial/mixed

Light Quality:
- Hardness: very soft/soft/medium/hard/very hard
- Diffusion: highly diffused/diffused/medium/direct
- Color temperature: cool/neutral/warm/very warm
- Intensity: dim/medium/bright/very bright

Shadow Characteristics:
- Presence: no shadows/soft shadows/medium shadows/hard shadows
- Direction: matching light direction
- Density: transparent/light/medium/dark/very dark
- Edge quality: very soft/soft/defined/hard/very hard
- Key shadows: under nose, under chin, under cheekbones, etc.

Highlights:
- Location: forehead/nose bridge/cheekbones/chin/etc.
- Intensity: subtle/medium/bright/very bright/blown out
- Size: small/medium/large
- Shape: round/elongated/irregular

Skin Interaction with Light:
- Subsurface scattering: none/slight/visible/prominent
- Glow: no glow/slight glow/glowing/too much glow
- Reflectivity: matte/slight sheen/shiny/very shiny/glossy

═══════════════════════════════════════
CRITICAL IDENTITY PRESERVATION STATEMENT
═══════════════════════════════════════

Write this section MANDATORY at end:

"IDENTITY PRESERVATION REQUIREMENTS:
This analysis describes a SPECIFIC UNIQUE PERSON who must be recognizable in any art style.
The combination of [list 5-7 most distinctive features] creates this person's unmistakable identity.
When rendering in any artistic style, these identifying characteristics MUST remain intact.
This is NOT a generic template but a specific individual's appearance documentation."

═══════════════════════════════════════
OUTPUT REQUIREMENTS
═══════════════════════════════════════

1. Write in detailed flowing paragraphs organized by section
2. Total length: 600-800 words minimum
3. Include EVERY detail from all sections above
4. Use precise, specific language - no vague terms
5. Prioritize features that make this person unique
6. Note asymmetries and individual quirks
7. End with Identity Preservation statement

This analysis will be used to recreate this EXACT person in different art styles.
Maximum detail ensures maximum accuracy.
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
I'm teaching you HOW to convert ANY person's photo into %s style.

This is a METHODOLOGY, not a specific example. Learn the PATTERN.

═══════════════════════════════════════
METHODOLOGY PATTERN
═══════════════════════════════════════

STEP 1: Extract Identity Features (from Vision Analysis)
- Face shape, skin tone, eyes, eyebrows, nose, mouth, jaw
- Hair: color, length, texture, style
- Clothing, pose, expression

STEP 2: Write Identity Section (EXACT preservation)
Start prompt with: "A person with [face shape] face shape, [skin tone] skin tone, [eye details], [nose details], [mouth details], [jaw details], has [complete hair description], wearing [clothing], [pose and expression]."

Use EXACT + MUST + IDENTICAL keywords for every feature.

STEP 3: Apply Style Characteristics

FOR GHIBLI STYLE:
Add these EXACT words after identity section:

"Rendered in Studio Ghibli 2D hand-drawn animation style. CRITICAL: This is PURE 2D FLAT traditional animation, NOT 3D rendering. Hand-painted watercolor texture throughout, organic hand-drawn linework with natural imperfections, soft delicate lines with slight thickness variation, flat 2D composition with NO 3D depth, colors applied in flat layers with gentle gradients, visible soft paper grain texture, painterly quality NOT digital smooth. Warm golden-hour lighting with soft amber tones, watercolor wash effects with bleeding edges, nostalgic dreamy atmosphere, dominant warm yellows and soft teals. Traditional cel animation technique as seen in Spirited Away and Howl's Moving Castle. Soft rosy blush as transparent watercolor wash. Background painted with loose watercolor bokeh effect in warm golden tones. Every element has 2D painted quality with visible brush strokes. NO smooth 3D subsurface scattering, NO realistic skin rendering, NO CGI appearance. Film grain overlay suggesting traditional animation."

FOR PIXAR STYLE:
Add these EXACT words after identity section:

"Rendered in Pixar's signature 3D animation style with smooth subsurface scattering on skin showing realistic translucency, individual hair strands with natural physics and light refraction, soft rim lighting from back-right, vibrant but naturalistic color palette, high-quality CGI render with fabric texture detail, soft volumetric lighting with cinematic quality. Professional Pixar-quality animation render matching Toy Story and Inside Out quality level. 8k resolution with studio lighting setup: warm key light from front-left and soft fill light. Clean gradient background. EXACT facial structure and proportions from description must be maintained."

FOR ANIMAL CROSSING STYLE:
Add these EXACT words after identity section:

"Transformed into Nintendo Animal Crossing New Horizons chibi style. CRITICAL CHIBI PROPORTIONS (MANDATORY): Head-to-body ratio EXACTLY 2.5:1 where head occupies 65%% of total height, body is 20%% as tiny compact torso, arms are short stubby cylinders with NO visible elbows ending in simple rounded mitt-like hands with 3 subtle finger segments, legs are 15%% stubby with NO visible knees, NO visible neck with head connecting directly to shoulders. Despite chibi transformation, person's UNIQUE facial features must remain RECOGNIZABLE in simplified form - NOT a generic chibi template. Rendered as low-polygon 3D model with GameCube/Wii era quality, soft matte plastic surface shader, rounded edges everywhere, pastel color palette, minimal polygon count creating simple geometric forms, no realistic textures, soft ambient lighting with no harsh shadows, clean simple silhouette. Hair simplified into chunky rounded sections maintaining original color and general style."

STEP 4: Add Verification Checklist

Always end with:

"IDENTITY VERIFICATION CHECKLIST:
✓ Face shape EXACTLY matches? NOT different person?
✓ Eye shape and color PRESERVED? NOT generic eyes?
✓ Hair color, length, texture MAINTAINED? NOT different hair?
✓ Expression and pose IDENTICAL? NOT different mood?
✓ Style applied WITHOUT losing identity? NOT a template?

PROHIBITIONS (CRITICAL):
DO NOT create a different person.
DO NOT create a different person.
DO NOT create a different person.
DO NOT use generic features.
DO NOT make a template.

FINAL STATEMENT:
This is a SPECIFIC UNIQUE PERSON rendered in %s style.
Creating a different person = complete failure.
All identifying characteristics from analysis MUST be preserved."

═══════════════════════════════════════
NOW APPLY THIS METHODOLOGY
═══════════════════════════════════════

TARGET STYLE: %s

USER'S ORIGINAL REQUEST:
%s

VISION ANALYSIS TO TRANSFORM:
%s

═══════════════════════════════════════

YOUR TASK:
1. Extract identity features from Vision Analysis (STEP 1)
2. Write identity section with EXACT/MUST keywords (STEP 2)  
3. Apply %s style characteristics from methodology (STEP 3)
4. Add verification checklist (STEP 4)
5. Output ONE dense paragraph, 300-400 words
6. NO line breaks, NO markdown, just flowing text

This is a METHODOLOGY that works for ANY photo being transformed into %s style.

OUTPUT ONLY THE DALL-E PROMPT. NO EXPLANATIONS.
""",
                    style.toUpperCase(),
                    style.toLowerCase(),
                    style.toUpperCase(),
                    userPrompt,
                    imageAnalysis,
                    style.toLowerCase(),
                    style.toLowerCase());

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

}
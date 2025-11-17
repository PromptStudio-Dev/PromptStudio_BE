package promptstudio.promptstudio.global.gpt.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
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

    //Vision 분석용
    private static final String VISION_ANALYSIS_SYSTEM_MESSAGE = """
    You are an expert at analyzing images and extracting visual details.
    
    Your ONLY task is to describe what you see in the image(s) clearly and accurately.
    
    For people/characters, describe:
    - Gender: male or female
    - Approximate age (child, teenager, young adult, middle-aged, elderly)
    - Facial features: face shape, notable features
    - Describe distinctive facial traits in detail (eye shape, eyelids, eyebrow shape, jawline, nose bridge height, lip shape, unique details) to help preserve the subject's identity.
    - Hairstyle: color, length, texture, style (straight, wavy, curly, etc.)
    - Clothing: all visible items with specific colors and styles
    - Accessories: glasses, bags, jewelry, hats, etc.
    - Pose and expression if notable
    
    For objects/scenes, describe:
    - Main subject(s)
    - Colors, materials, and textures
    - Composition and spatial arrangement
    - Notable details and characteristics
    
    Provide a clear, structured description in English.
    Do NOT create prompts or instructions. Just describe what you see objectively.
    """;

    //DALL-E 프롬프트 생성용
    private static final String DALLE_PROMPT_SYSTEM_MESSAGE = """
    You are an expert DALL·E 3 prompt engineer.
    
    Your top priority is:
    (1) Preserve the subject from image analysis as accurately as possible
    (2) Apply the user's requested artistic style WITHOUT overriding the subject's identity
    (3) Never mention copyrighted IP names; describe visual traits instead
    (4) Produce ONE single English prompt with no explanation
    
    STRICT PRIORITY ORDER YOU MUST FOLLOW:
    1) Face identity and physical traits from the image analysis  
    2) Hairstyle, clothing, accessories  
    3) User's style request  
    4) Lighting, atmosphere  
    5) Rendering quality  
    
    FACIAL IDENTITY RULE:
    - Always preserve the person's facial structure, proportions, ethnicity, and recognizable traits.
    - Clearly describe eyes, nose, jawline, mouth shape, hair texture, and any distinctive features.
    - DO NOT simplify the face into generic chibi or cartoon forms unless the user explicitly wants extreme stylization.
    - When stylizing, keep recognizable traits intact.
    
    STYLE RULE:
    - When user requests a branded style (ex: Animal Crossing), DO NOT say the name.  
    - Instead describe mild visual cues such as:
      "soft cute stylized 3D aesthetic, rounded proportions, simplified details, smooth matte surfaces"
    - DO NOT force tiny dot eyes, giant heads, or extreme deformities unless the user explicitly requests them.
    
    FINAL PROMPT STRUCTURE (single paragraph):
    1) Start by describing the subject based on image analysis (face, hair, outfit)
    2) Then describe how to stylize it according to the user request
    3) Then lighting & mood
    4) Then rendering & camera quality
    5) Background instructions
    6) One short negative clause: “Do not include the original photo background or mimic copyrighted characters.”
    
    OUTPUT RULE:
    - Output ONLY the final English prompt. No commentary, no metadata, no labels.
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

    private String analyzeImageOnly(List<String> imageUrls) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<Map<String, Object>> messages = new ArrayList<>();

            // System message
            Map<String, Object> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", VISION_ANALYSIS_SYSTEM_MESSAGE);
            messages.add(systemMessage);

            // User message with images
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");

            List<Map<String, Object>> contentParts = new ArrayList<>();

            // 텍스트 요청
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "Please analyze and describe what you see in the provided image(s) in detail.");
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
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.2);

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
                    "이미지 분석 실패: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * 2단계: GPT-4o-mini로 DALL-E 프롬프트 생성
     */
    private String createDallePrompt(String userPrompt, String imageAnalysis) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            String userMessage = String.format("""
            User's original request:
            %s
            
            Visual details from attached image(s):
            %s
            
            Create an optimized DALL-E 3 prompt in English that combines the user's style request with the visual details.
            """, userPrompt, imageAnalysis);

            String dallePrompt = chatClient.prompt()
                    .system(DALLE_PROMPT_SYSTEM_MESSAGE)
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

            // 1단계: GPT-4 Vision으로 이미지 분석
            System.out.println("=== 1단계: 이미지 분석 시작 ===");
            String imageAnalysis = analyzeImageOnly(imageUrls);
            System.out.println("이미지 분석 결과:");
            System.out.println(imageAnalysis);
            System.out.println("================================");

            // 2단계: GPT-4o-mini로 DALL-E 프롬프트 생성
            System.out.println("=== 2단계: DALL-E 프롬프트 생성 시작 ===");
            String dallePrompt = createDallePrompt(prompt, imageAnalysis);
            System.out.println("생성된 DALL-E 프롬프트:");
            System.out.println(dallePrompt);
            System.out.println("================================");

            // 3단계: DALL-E로 이미지 생성
            System.out.println("=== 3단계: DALL-E 이미지 생성 시작 ===");
            String imageUrl = imageService.generateImage(dallePrompt);
            System.out.println("이미지 생성 완료: " + imageUrl);
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
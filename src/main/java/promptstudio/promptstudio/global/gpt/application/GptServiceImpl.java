package promptstudio.promptstudio.global.gpt.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import promptstudio.promptstudio.domain.history.domain.entity.ResultType;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;
import promptstudio.promptstudio.global.dall_e.application.ImageService;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService {

    private final ChatClient.Builder chatClientBuilder;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;

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
        당신은 사용자의 요청을 분석하여 적절한 응답을 제공하는 AI 어시스턴트입니다.
        
        응답은 반드시 아래 JSON 형식으로만 해주세요:
        
        1. 이미지 생성 요청인 경우:
        {
          "type": "IMAGE",
          "prompt": "영어로 번역된 이미지 생성 프롬프트"
        }
        
        2. 텍스트 응답 요청인 경우:
        {
          "type": "TEXT",
          "content": "사용자 질문에 대한 답변"
        }
        
        이미지 생성 키워드: 이미지, 그림, 사진, 만들어줘, 생성해줘, 그려줘, image, picture, draw, create, generate
        
        중요: JSON 형식만 출력하고 다른 설명은 절대 추가하지 마세요.
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
}
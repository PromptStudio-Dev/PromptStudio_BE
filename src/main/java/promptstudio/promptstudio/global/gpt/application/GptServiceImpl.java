package promptstudio.promptstudio.global.gpt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService {

    private final ChatClient.Builder chatClientBuilder;

    private static final String SYSTEM_MESSAGE = """
        당신은 프롬프트 작성 전문가입니다.
        사용자가 제공한 텍스트를 지정된 방향성에 맞게 개선해주세요.
        
        중요한 규칙:
        - 개선된 텍스트만 출력하고, 추가 설명이나 인사말은 절대 하지 마세요
        - 원본 텍스트의 문장 구조, 어조, 시제를 최대한 유지하세요
        - 원본과 비슷한 길이로 작성하세요 (너무 길거나 짧지 않게)
        - 원본이 명사형이면 명사형으로, 동사형이면 동사형으로 유지하세요
        - 전체 맥락 속에서 자연스럽게 들어갈 수 있도록 작성하세요
        """;

    private static final String USER_MESSAGE_TEMPLATE = """
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

    @Override
    public String upgradeText(String selectedText, String direction, String fullContext) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            PromptTemplate promptTemplate = new PromptTemplate(USER_MESSAGE_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "selectedText", selectedText,
                    "direction", direction,
                    "fullContext", fullContext != null ? fullContext : ""
            ));

            String result = chatClient.prompt(prompt)
                    .system(SYSTEM_MESSAGE)
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
}
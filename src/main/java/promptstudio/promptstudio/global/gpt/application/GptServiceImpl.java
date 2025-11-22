package promptstudio.promptstudio.global.gpt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;

    private static final String SYSTEM_MESSAGE = """
        당신은 경력 30년을 가진 프롬프트 엔지니어링 기술자 및 문장 개선 전문가입니다.
        사용자가 제공한 ‘선택 텍스트’를 ‘개선 방향’에 맞게 자연스럽게 업그레이드하세요.
        
        규칙:
        - 출력은 개선된 텍스트만 반환합니다. 설명/인사/부연/메타 발화 금지.
        - 원본 문장의 말투, 어조, 시제, 문체를 유지하세요.
        - 전체 텍스트에서 선택 텍스트가 포함된 위치를 고려해, 앞문장과 뒷문장 모두와 자연스럽게 이어지도록 다듬습니다.
        """;


    private static final String USER_MESSAGE_TEMPLATE = """
        [전체 텍스트]
        {fullContext}
        
        [선택 텍스트]
        {selectedText}
        
        [개선 방향]
        {direction}
        
        선택 텍스트를 개선 방향에 맞게 업그레이드해서 출력하세요.
        """;


    private static final String USER_MESSAGE_TEMPLATE_WITH_CONTEXT = """
        [전체 텍스트]
        {fullContext}
        
        [선택 텍스트]
        {selectedText}
        
        [개선 방향]
        {direction}
        
        [참고 프롬프트]
        {ragContext}

        참고 프롬프트는 스타일/구성/표현을 참고하되,
        내용을 그대로 복사하지 말고 사용자 문맥에 맞게 재작성하세요.
        
        선택 텍스트를 현재 개선 방향에 맞게 업그레이드하여 출력하세요.
        """;

    private static final String REUPGRADE_USER_TEMPLATE = """
        [전체 텍스트]
        {fullContext}
        
        [선택 텍스트]
        {selectedText}
        
        [이전 개선 방향]
        {prevDirection}
        
        [이전 결과]
        {prevResult}
        
        [현재 개선 방향]
        {direction}
        
        이전 개선 방향과 현 개선 방향이 상충한다면, 현재 개선 방향을 우선해 참고하여 더 정교한 개선을 수행하세요.
        
        선택 텍스트를 현재 개선 방향에 맞게 업그레이드하여 출력하세요.
        """;
    private static final String REUPGRADE_USER_TEMPLATE_WITH_CONTEXT = """
        [전체 텍스트]
        {fullContext}
        
        [선택 텍스트]
        {selectedText}
        
        [이전 개선 방향]
        {prevDirection}
        
        [이전 결과]
        {prevResult}
        
        [현재 개선 방향]
        {direction}
        
        [참고 프롬프트]
        {ragContext}
    
        참고 프롬프트는 스타일/구성/표현을 참고하되,
        내용을 그대로 복사하지 말고 사용자 문맥에 맞게 재작성하세요.
    
        아래의 이전 요청 정보가 존재한다면,
        이번 개선 방향보다 우선해 참고하여 더 정교한 개선을 수행하세요.
        
        선택 텍스트를 현재 개선 방향에 맞게 업그레이드하여 출력하세요.
        """;


    private static final String QUERY_SYSTEM_MESSAGE = """
        당신은 프롬프트 검색을 위한 "검색 쿼리 생성기"입니다.
        
        중요: 검색 대상 문서는 아래 형식으로 임베딩되어 있습니다.
        - [TITLE] 프롬프트 제목
        - [INTRO] 프롬프트 소개/요약
        - [CONTENT] 프롬프트 본문
        
        따라서 검색 쿼리는 위 필드들에 실제로 들어갈 법한
        "주제/의도/작업형태/스타일" 키워드를 중심으로 작성해야 합니다.
        사용자 입력을 바탕으로 벡터 검색에 적합한 짧은 검색 쿼리를 만드세요.
        
        규칙:
        - 1~2문장 또는 핵심 키워드 나열로 작성
        - 사용자의 의도/방향성을 반영
        - 불필요한 수식/서론 금지
        - 따옴표/번호 없이 평문으로만 출력
        """;

    private static final String QUERY_USER_TEMPLATE = """
        [선택 텍스트]
        {selectedText}
        
        [수정 방향]
        {direction}
        
        [전체 문맥]
        {fullText}
        
        위 정보를 참고해서 벡터 검색용 쿼리를 생성해 출력하세요.
        """;


    @Override
    public String upgradeText(String fullContext, String selectedText, String direction) {

        ChatClient chatClient = chatClientBuilder.build();

        PromptTemplate promptTemplate = new PromptTemplate(USER_MESSAGE_TEMPLATE);
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "direction", direction

        ));

        String result = chatClient.prompt(prompt)
                .system(SYSTEM_MESSAGE)
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

        PromptTemplate promptTemplate = new PromptTemplate(USER_MESSAGE_TEMPLATE_WITH_CONTEXT);
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "direction", direction,
                "ragContext", ragContext != null ? ragContext : ""
        ));

        String result = chatClient.prompt(prompt)
                .system(SYSTEM_MESSAGE)
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

        PromptTemplate promptTemplate = new PromptTemplate(REUPGRADE_USER_TEMPLATE);
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "prevDirection", prevDirection != null ? prevDirection : "",
                "prevResult", prevResult != null ? prevResult : "",
                "direction", direction
        ));

        String result = chatClient.prompt(prompt)
                .system(SYSTEM_MESSAGE)
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

        PromptTemplate promptTemplate = new PromptTemplate(REUPGRADE_USER_TEMPLATE_WITH_CONTEXT);
        Prompt prompt = promptTemplate.create(Map.of(
                "fullContext", fullContext != null ? fullContext : "",
                "selectedText", selectedText,
                "prevDirection", prevDirection != null ? prevDirection : "",
                "prevResult", prevResult != null ? prevResult : "",
                "direction", direction,
                "ragContext", ragContext != null ? ragContext : ""
        ));

        String result = chatClient.prompt(prompt)
                .system(SYSTEM_MESSAGE)
                .call()
                .content();

        return result != null ? result.trim() : selectedText;
    }

    @Override
    public String generateSearchQuery(String fullText, String selectedText, String direction) {

        ChatClient chatClient = chatClientBuilder.build();

        PromptTemplate promptTemplate = new PromptTemplate(QUERY_USER_TEMPLATE);
        Prompt prompt = promptTemplate.create(Map.of(
                "fullText", fullText != null ? fullText : "",
                "selectedText", selectedText,
                "direction", direction
        ));

        String result = chatClient.prompt(prompt)
                .system(QUERY_SYSTEM_MESSAGE)
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

}
package promptstudio.promptstudio.global.gpt.application;

import org.springframework.ai.document.Document;

import java.util.List;

public interface GptService {
    /**
     * 텍스트를 업그레이드합니다.
     * @param selectedText 업그레이드할 텍스트
     * @param direction 업그레이드 방향성
     * @param fullContext 전체 프롬프트 내용 (맥락 파악용)
     * @return 업그레이드된 텍스트
     */
    String upgradeText(String selectedText, String direction, String fullContext);
    String upgradeTextWithContext(String selectedText, String direction, String fullContext, String ragContext);
    String generateSearchQuery(String selectedText, String direction, String fullText);
    List<Document> retrieve(String query, int topK, double threshold);
}
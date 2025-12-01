package promptstudio.promptstudio.global.gpt.prompt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PromptType {

    // 텍스트 업그레이드
    UPGRADE_SYSTEM("prompts/upgrade/system.txt"),
    UPGRADE_USER("prompts/upgrade/user-template.txt"),
    UPGRADE_USER_WITH_CONTEXT("prompts/upgrade/user-with-context-template.txt"),
    UPGRADE_REUPGRADE("prompts/upgrade/reupgrade-template.txt"),
    UPGRADE_REUPGRADE_WITH_CONTEXT("prompts/upgrade/reupgrade-with-context-template.txt"),

    // 검색 쿼리 생성
    QUERY_SYSTEM("prompts/query/system.txt"),
    QUERY_USER("prompts/query/user-template.txt"),

    // 프롬프트 실행
    RUN_SYSTEM("prompts/run/system.txt"),

    // Vision & DALL-E
    VISION_ANALYSIS_SYSTEM("prompts/vision/analysis-system.txt"),
    VISION_DALLE_EXPERT_SYSTEM("prompts/vision/dalle-expert-system.txt"),
    VISION_STYLE_ANALYZER_SYSTEM("prompts/vision/style-analyzer-system.txt"),
    VISION_FEWSHOT_TEMPLATE("prompts/vision/fewshot-template.txt"),

    // 히스토리
    HISTORY_SYSTEM("prompts/history/system.txt"),
    HISTORY_FIRST("prompts/history/first-template.txt"),
    HISTORY_DIFF("prompts/history/diff-template.txt"),

    // 피드백
    FEEDBACK_SYSTEM("prompts/feedback/system.txt"),
    FEEDBACK_USER("prompts/feedback/user-template.txt");

    private final String path;
}
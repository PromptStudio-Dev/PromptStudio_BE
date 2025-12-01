package promptstudio.promptstudio.global.gpt.prompt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Component
public class PromptRegistry {

    private final Map<PromptType, String> promptCache = new EnumMap<>(PromptType.class);
    private final Map<FewshotStyle, String> fewshotCache = new EnumMap<>(FewshotStyle.class);

    @PostConstruct
    public void init() {
        loadAllPrompts();
        loadAllFewshots();
        log.info("프롬프트 로딩 완료: {} prompts, {} fewshots",
                promptCache.size(), fewshotCache.size());
    }

    private void loadAllPrompts() {
        for (PromptType type : PromptType.values()) {
            String content = loadFile(type.getPath());
            if (content == null) {
                throw new IllegalStateException("프롬프트 파일 누락: " + type.getPath());
            }
            promptCache.put(type, content);
            log.debug("프롬프트 로드 완료: {}", type.name());
        }
    }

    private void loadAllFewshots() {
        for (FewshotStyle style : FewshotStyle.values()) {
            String content = loadFile(style.getPath());
            if (content == null) {
                throw new IllegalStateException("Fewshot 파일 누락: " + style.getPath());
            }
            fewshotCache.put(style, content);
            log.debug("Fewshot 로드 완료: {}", style.name());
        }
    }

    private String loadFile(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream is = resource.getInputStream()) {
                return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("파일 로드 실패: {}", path, e);
            return null;
        }
    }

    //프롬프트 원문 반환
    public String get(PromptType type) {
        String content = promptCache.get(type);
        if (content == null) {
            throw new IllegalArgumentException("프롬프트를 찾을 수 없음: " + type.name());
        }
        return content;
    }
    
    public String getFewshot(FewshotStyle style) {
        String content = fewshotCache.get(style);
        if (content == null) {
            throw new IllegalArgumentException("Fewshot을 찾을 수 없음: " + style.name());
        }
        return content;
    }

    public String getFewshotByName(String styleName) {
        FewshotStyle style = FewshotStyle.fromStyleName(styleName);
        if (style == null) {
            return null;
        }
        return fewshotCache.get(style);
    }
}
package promptstudio.promptstudio.global.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import promptstudio.promptstudio.domain.chat.domain.ChatSession;

import java.util.concurrent.TimeUnit;

@Component
public class ChatSessionCache {

    private final Cache<String, ChatSession> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)  // 30분 미사용 시 삭제
            .maximumSize(10000)                        // 최대 10,000개 세션
            .build();

    public void put(String sessionId, ChatSession session) {
        sessionCache.put(sessionId, session);
    }

    public ChatSession get(String sessionId) {
        return sessionCache.getIfPresent(sessionId);
    }

    public void remove(String sessionId) {
        sessionCache.invalidate(sessionId);
    }
}
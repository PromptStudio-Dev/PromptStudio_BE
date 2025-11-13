package promptstudio.promptstudio.global.gpt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

//DALL-E 3 TODO:영어 프롬프트 필요시 수정, 테스트 많이 하기!
public record ImageGenerationRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("영어로 작성된 이미지 생성 프롬프트")
        String prompt
) {}
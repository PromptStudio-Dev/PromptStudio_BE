package promptstudio.promptstudio.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PromptStudio API")
                        .description("PromptStudio 프로젝트 API 명세서")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("PromptStudio Team")
                                .email("contact@promptstudio.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://www.promptstudio.kr")
                                .description("운영 서버")
                ));
    }
}

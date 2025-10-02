package cn.com.vortexa.gopher;


import cn.com.vortexa.bot_template.BotTemplateAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author helei
 * @since 2025-10-02
 */
@SpringBootApplication
@ImportAutoConfiguration(BotTemplateAutoConfig.class)
public class GopherApp {
    public static void main(String[] args) {
        SpringApplication.run(GopherApp.class, args);
    }
}

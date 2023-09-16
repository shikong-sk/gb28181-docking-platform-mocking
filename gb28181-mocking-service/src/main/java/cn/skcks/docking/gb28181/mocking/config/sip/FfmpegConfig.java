package cn.skcks.docking.gb28181.mocking.config.sip;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "ffmpeg-support")
@Configuration
@Data
public class FfmpegConfig {
    private String ffmpeg;
    private String ffprobe;

    private Rtp rtp;

    @Data
    public static class Rtp {
        private String input;
        private String output;
        private String logLevel = "fatal";
    }
}

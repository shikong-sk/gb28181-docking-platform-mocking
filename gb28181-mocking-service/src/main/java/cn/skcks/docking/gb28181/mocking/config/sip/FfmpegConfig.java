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

    private Boolean useZlmFfmpeg = false;

    private Rtp rtp;

    @Data
    public static class Rtp {
        private double downloadSpeed = 4.0;
        private String download = "-i";
        private String input = "-re -i";
        private String output = "-vcodec h264 -acodec aac -f rtp_mpegts";
        private String logLevel = "error";
        private Boolean useRtpToDownload = false;
    }


    private Debug debug;

    @Data
    public static class Debug {
        private Boolean download = false;
        private Boolean input = false;
        private Boolean output = false;
    }

    private Task task;

    @Data
    public static class Task {
        private Integer max = 4;
    }
}

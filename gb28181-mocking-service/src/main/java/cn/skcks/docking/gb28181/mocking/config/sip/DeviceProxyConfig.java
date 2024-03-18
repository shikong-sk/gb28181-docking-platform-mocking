package cn.skcks.docking.gb28181.mocking.config.sip;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "proxy.device")
public class DeviceProxyConfig {
    /**
     * 通过其他 代理 agent 拉取历史视频 的地址
     */
    private String url;
    /**
     * 是否使用下载的方式 拉取历史回放
     */
    private Boolean useDownloadToPlayback = true;
    /**
     * 是否只通过代理拉取指定时间范围内的视频查询请求
     */
    private Boolean proxyVideoInTimeRange = true;

    /**
     * 实时视频单次允许最大播放时长
     */
    private Duration realTimeVideoMaxPlayTime  = Duration.ofMinutes(15);

    /**
     * 代理该时间段内的历史视频查询请求
     */
    private Duration proxyTimeRange = Duration.ofMinutes(5);

    /**
     * 预下载历史视频的配置
     */
    private PreDownloadForRecordInfo preDownloadForRecordInfo = new PreDownloadForRecordInfo();

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PreDownloadForRecordInfo {
        private Boolean enable = true;
        private Duration timeRange = Duration.ofMinutes(5);
        /**
         * 分片时长, 当请求时间超过该时长时，将分片下载
         */
        private Duration timeSplit = Duration.ofSeconds(30);
        private String cachePath = "./record";
    }
}

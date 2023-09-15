package cn.skcks.docking.gb28181.mocking.service.device;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.URLUtil;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProxyService {
    private final DeviceService deviceService;

    private final DeviceProxyConfig proxyConfig;

    public void proxyVideo2Rtp(MockingDevice device, Date startTime, Date endTime, String rtpAddr, int rtpPort){
        String fromUrl = URLUtil.completeUrl(proxyConfig.getUrl(), "/video");
        HashMap<String, String> map = new HashMap<>(3);
        String deviceCode = device.getDeviceCode();
        map.put("device_id", deviceCode);
        map.put("begin_time", DateUtil.format(startTime, DatePattern.PURE_DATETIME_FORMAT));
        map.put("end_time", DateUtil.format(endTime, DatePattern.PURE_DATETIME_FORMAT));
        String query = URLUtil.buildQuery(map, StandardCharsets.UTF_8);
        fromUrl = StringUtils.joinWith("?", fromUrl, query);
        log.info("设备: {} 视频 url: {}", deviceCode, fromUrl);
        String toUrl = StringUtils.joinWith("", "rtp://", rtpAddr, ":", rtpPort);
        long time = DateUtil.between(startTime, endTime, DateUnit.SECOND);
        pushRtp(fromUrl, toUrl, time);
    }

    @SneakyThrows
    public void pushRtp(String fromUrl, String toUrl, long time) {
        log.info("创建推流任务 fromUrl {}, toUrl {}, time: {}", fromUrl, toUrl, time);
        // FFmpeg 调试日志
        //        FFmpegLogCallback.set();
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(fromUrl);
        grabber.setOption("re","");
        grabber.start();

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(toUrl, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
        recorder.setInterleaved(true);
        recorder.setVideoOption("preset", "ultrafast");
        recorder.setVideoOption("tune", "zerolatency");
        recorder.setVideoOption("crf", "25");
        recorder.setFrameRate(grabber.getFrameRate());
        recorder.setSampleRate(grabber.getSampleRate());
        recorder.setOption("flvflags", "no_duration_filesize");
        recorder.setOption("movflags","frag_keyframe+empty_moov");
        if (grabber.getAudioChannels() > 0) {
            recorder.setAudioChannels(grabber.getAudioChannels());
            recorder.setAudioBitrate(grabber.getAudioBitrate());
            recorder.setAudioCodec(grabber.getAudioCodec());
        }
        recorder.setFrameRate(grabber.getVideoFrameRate());
        recorder.setVideoBitrate(grabber.getVideoBitrate());
        //        recorder.setVideoCodec(grabber.getVideoCodec());
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // 视频源数据yuv
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC); // 设置音频压缩方式
        recorder.setFormat("rtp_mpegts");
        recorder.setVideoOption("threads", String.valueOf(Runtime.getRuntime().availableProcessors())); // 解码线程数
        recorder.start(grabber.getFormatContext());

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean record = new AtomicBoolean(true);
        scheduledExecutorService.schedule(() -> {
            log.info("到达结束时间, 结束推送 fromUrl: {}, toUrl: {}", fromUrl, toUrl);
            record.set(false);
        }, time, TimeUnit.SECONDS);
        try {
            AVPacket k;
            while (record.get() && (k = grabber.grabPacket()) != null) {
                recorder.recordPacket(k);
                avcodec.av_packet_unref(k);
            }
            grabber.close();
            recorder.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("结束推送 fromUrl: {}, toUrl: {}", fromUrl, toUrl);
    }
}

package cn.skcks.docking.gb28181.mocking.service.device;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.URLUtil;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import gov.nist.javax.sip.message.SIPRequest;
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

import javax.sip.message.Request;
import javax.sip.message.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProxyService {
    private final MockingExecutor mockingExecutor;

    private final DeviceProxyConfig proxyConfig;

    private final SipSubscribe subscribe;

    private final ConcurrentHashMap<String, CompletableFuture<Void>> task = new ConcurrentHashMap<>();

    private final SipSender sender;

    public synchronized void proxyVideo2Rtp(String callId, MockingDevice device, Date startTime, Date endTime, String rtpAddr, int rtpPort){
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

        String key = GenericSubscribe.Helper.getKey(Request.BYE, callId);
        subscribe.getByeSubscribe().addPublisher(key);
        Flow.Subscriber<SIPRequest> subscriber = new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                log.info("订阅 bye {}", key);
                subscription.request(1);
            }

            @Override
            public void onNext(SIPRequest item) {
                String ip = item.getLocalAddress().getHostAddress();
                String transPort = item.getTopmostViaHeader().getTransport();
                sender.sendResponse(ip, transPort,((provider, ip1, port) ->
                        SipResponseBuilder.response(item, Response.OK, "OK")));
                onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                onComplete();
            }

            @Override
            public void onComplete() {
                log.info("bye 订阅结束 {}", key);
                subscribe.getByeSubscribe().delPublisher(key);
                Optional.ofNullable(task.get(device.getDeviceCode())).ifPresent(task->{
                    task.cancel(true);
                });
                task.remove(device.getDeviceCode());
            }
        };
        final String finalFromUrl = fromUrl;
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            pushRtp(finalFromUrl, toUrl, time);
            // 推送结束后 60 秒内未收到 bye 则结束订阅 释放内存
            scheduledExecutorService.schedule(subscriber::onComplete, time + 60 , TimeUnit.SECONDS);
        }, mockingExecutor.sipTaskExecutor());
        task.put(device.getDeviceCode(), future);
        subscribe.getByeSubscribe().addSubscribe(key, subscriber);
    }

    @SneakyThrows
    public void pushRtp(String fromUrl, String toUrl, long time) {
        log.info("创建推流任务 fromUrl {}, toUrl {}, time: {}", fromUrl, toUrl, time);
        // FFmpeg 调试日志
//      FFmpegLogCallback.set();
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(fromUrl);
        // 30秒超时
        grabber.setOption("stimeout", "30000000");
        grabber.start();

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(toUrl, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
        recorder.setInterleaved(true);
        recorder.setVideoOption("preset", "ultrafast");
        recorder.setVideoOption("tune", "zerolatency");
        recorder.setVideoOption("crf", "25");
//        recorder.setMaxDelay(500);
        recorder.setGopSize((int) (grabber.getFrameRate() * 2));
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
        grabber.flush();

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean record = new AtomicBoolean(true);
        scheduledExecutorService.schedule(() -> {
            log.info("到达结束时间, 结束推送 fromUrl: {}, toUrl: {}", fromUrl, toUrl);
            record.set(false);
        }, time, TimeUnit.SECONDS);
        try {
            long begin = System.currentTimeMillis();
            AVPacket k;
            long dts = 0;
            long pts = 0;
            int no_frame_index = 0;
            while (record.get() && no_frame_index < 10 ) {
                k = grabber.grabPacket();
                if(k == null || k.size() <= 0 || k.data() == null) {
                    //空包记录次数跳过
                    no_frame_index++;
                    continue;
                }
                // 获取到的pkt的dts，pts异常，将此包丢弃掉。
                if (k.dts() == avutil.AV_NOPTS_VALUE && k.pts() == avutil.AV_NOPTS_VALUE || k.pts() < dts) {
                    avcodec.av_packet_unref(k);
                    continue;
                }
                // 记录上一pkt的dts，pts
                dts = k.dts();
                pts = k.pts();
                recorder.recordPacket(k);
                avcodec.av_packet_unref(k);
                long end = System.currentTimeMillis();
                long sleep_real = (long) ((1000 / grabber.getFrameRate()) - (end - begin));
                begin = end;
                if (sleep_real > 0) {
                    Thread.sleep(sleep_real);
                }
            }
            grabber.close();
            recorder.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("结束推送 fromUrl: {}, toUrl: {}", fromUrl, toUrl);
    }
}

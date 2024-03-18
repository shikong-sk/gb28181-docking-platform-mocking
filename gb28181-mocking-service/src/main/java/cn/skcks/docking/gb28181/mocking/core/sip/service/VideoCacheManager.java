package cn.skcks.docking.gb28181.mocking.core.sip.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
import cn.skcks.docking.gb28181.mocking.service.ffmpeg.FfmpegSupportService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCacheManager {
    private final DeviceProxyConfig deviceProxyConfig;

    @Qualifier(MockingExecutor.EXECUTOR_BEAN_NAME)
    private final Executor executor;

    private final FfmpegSupportService ffmpegSupportService;

    private final TimedCache<String, CompletableFuture<JsonResponse<String>>> tasks =
            CacheUtil.newTimedCache(TimeUnit.MINUTES.toMillis(30));

    private final PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();

    @PostConstruct
    private void init(){
        manager.setMaxTotal(100);
        manager.setDefaultMaxPerRoute(100);
        manager.setDefaultConnectionConfig(
                ConnectionConfig.custom()
                        .setConnectTimeout(5, TimeUnit.MINUTES)
                        .build());
    }

    public String dateFormat(Date date){
        return DateUtil.format(date, DatePattern.PURE_DATETIME_PATTERN);
    }

    public String fileName(String deviceCode, Date startTime, Date endTime){
        return StringUtils.joinWith("-", deviceCode, dateFormat(startTime), dateFormat(endTime));
    }

    public void addTask(String deviceCode, Date startTime, Date endTime){
        String name = fileName(deviceCode, startTime, endTime);
        if(tasks.get(name, false) != null){
            return;
        }

        tasks.put(name, downloadVideo(deviceCode,startTime,endTime));
    }

    public CompletableFuture<JsonResponse<String>> get(String deviceCode, Date startTime, Date endTime){
        String name = fileName(deviceCode, startTime, endTime);
        CompletableFuture<JsonResponse<String>> future = tasks.get(name, false);
        if(future == null){
            File realFile = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(),fileName(deviceCode, startTime, endTime) + ".mp4").toFile();
            if(realFile.exists()){
                log.info("文件 {} 已缓存, 直接返回", realFile.getAbsolutePath());
                return CompletableFuture.completedFuture(JsonResponse.success(realFile.getAbsolutePath()));
            }
        }
        return future;
    }

    @SneakyThrows
    protected CompletableFuture<JsonResponse<String>> downloadVideo(String deviceCode, Date startTime, Date endTime) {
        String fileName = fileName(deviceCode, startTime, endTime);
        File realFile = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(),fileName + ".mp4").toFile();
        if(realFile.exists()){
            log.info("文件 {} 已缓存, 直接返回", realFile.getAbsolutePath());
            return CompletableFuture.completedFuture(JsonResponse.success(realFile.getAbsolutePath()));
        }

        return CompletableFuture.supplyAsync(()->{
            long between = DateUtil.between(startTime, endTime, DateUnit.SECOND);
            long splitTime = deviceProxyConfig.getPreDownloadForRecordInfo().getTimeSplit().getSeconds();
            if(between > splitTime){
                log.info("时间间隔超过 {} 秒, 将分片下载", splitTime);
                DateTime splitStartTime = DateUtil.date(startTime);
                DateTime splitEndTime = DateUtil.offsetSecond(startTime, (int) splitTime);
                List<CompletableFuture<JsonResponse<String>>> completableFutures = new ArrayList<>();

                while(splitEndTime.getTime() < endTime.getTime()){
                    String splitFileName = fileName(deviceCode, splitStartTime, splitEndTime);
                    File tmpFile = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(),splitFileName + ".mp4.tmp").toFile();
                    if(tmpFile.exists()){
                        tmpFile.delete();
                        log.info("删除已存在但未完成下载的临时文件 => {}", tmpFile.getAbsolutePath());
                    }
                    // 添加分片任务
                    addTask(deviceCode, splitStartTime, splitEndTime);
                    completableFutures.add(get(deviceCode, splitStartTime, splitEndTime));
                    // 更新起止时间
                    splitStartTime = DateUtil.offsetSecond(splitStartTime, (int) splitTime);
                    splitEndTime = DateUtil.offsetSecond(splitEndTime, (int) splitTime);
                    if(splitEndTime.getTime() >= endTime.getTime()){
                        splitEndTime = DateUtil.date(endTime);
                        addTask(deviceCode, splitStartTime, splitEndTime);
                        completableFutures.add(get(deviceCode, splitStartTime, splitEndTime));
                        break;
                    }
                }

                CompletableFuture.allOf(completableFutures.toArray(CompletableFuture[]::new));
                String concatFileName = fileName + ".mp4.concat";
                File concatFile = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(), concatFileName).toFile();
                if(concatFile.exists()){
                    concatFile.delete();
                    log.info("删除已存在但未完成合并的临时合并配置文件 => {}", concatFile.getAbsolutePath());
                }

                try {
                    concatFile.createNewFile();
                    try(FileWriter fileWriter = new FileWriter(concatFile)){
                        for (CompletableFuture<JsonResponse<String>> result : completableFutures) {
                            String splitFilePath = result.get().getData();
                            fileWriter.write(String.format("file \"%s\"\n", splitFilePath));
                        }
                    }
                    log.info("生成临时合并配置文件 {}", concatFile.getAbsolutePath());

                    log.info("开始合并视频 => {}", realFile.getAbsolutePath());
                    DefaultExecuteResultHandler executeResultHandler = new DefaultExecuteResultHandler();
                    ffmpegSupportService.ffmpegConcatExecutor(concatFile.getAbsolutePath(), realFile.getAbsolutePath(), executeResultHandler);
                    executeResultHandler.waitFor();

                    if(realFile.exists()){
                        log.info("视频合并成功 => {}", realFile.getAbsolutePath());
                        return JsonResponse.success(realFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.error("合并分片视频异常 => {}", e.getMessage());
                    return JsonResponse.error(e.getMessage());
                } finally {
                    System.gc();
                    log.info("删除临时合并配置文件 {} => {}", concatFile.getAbsolutePath(), concatFile.delete());
                }
            }
            final String url = UrlBuilder.of(deviceProxyConfig.getUrl())
                    .addPath("video")
                    .addQuery("device_id", deviceCode)
                    .addQuery("begin_time", dateFormat(startTime))
                    .addQuery("end_time", dateFormat(endTime))
                    .addQuery("useDownload", true).build();
            File file = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(),fileName(deviceCode, startTime, endTime) + ".mp4.tmp").toFile();
            log.info("文件存储路径 => {}", file.getAbsolutePath());
            log.info("临时文件 {}, 是否存在: {}", file.getAbsolutePath(), file.exists());

            if(file.exists()){
                file.delete();
                log.info("删除已存但未完成下载的临时文件 => {}", file.getAbsolutePath());
            }

            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                HttpClient client = HttpClients.custom()
                        .setConnectionManager(manager)
                        .setConnectionManagerShared(true)
                        .build();

                HttpGet httpGet = new HttpGet(url);
                InputStream execute = client.execute(httpGet, response -> {
                    InputStream stream = response.getEntity().getContent();
                    IoUtil.copy(stream, outputStream);
                    return stream;
                });
                execute.close();
                log.info("临时文件下载完成 => {}", file.getAbsolutePath());
                log.info("临时文件 {}, 是否存在: {}", file.getAbsolutePath(), file.exists());
                file.renameTo(realFile);
                log.info("保存视频文件 => {}", realFile.getAbsolutePath());
                return JsonResponse.success(realFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("视频下载失败 => {}", e.getMessage());
                System.gc();
                file.delete();
                return JsonResponse.error(e.getMessage());
            }
        },executor);
    }
}

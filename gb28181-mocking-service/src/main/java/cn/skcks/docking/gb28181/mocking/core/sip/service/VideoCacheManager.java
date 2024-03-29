package cn.skcks.docking.gb28181.mocking.core.sip.service;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCacheManager {
    private final DeviceProxyConfig deviceProxyConfig;

    @Qualifier(MockingExecutor.EXECUTOR_BEAN_NAME)
    private final Executor executor;

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
        File realFile = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(),fileName(deviceCode, startTime, endTime) + ".mp4").toFile();
        if(realFile.exists()){
            log.info("文件 {} 已缓存, 直接返回", realFile.getAbsolutePath());
            return CompletableFuture.completedFuture(JsonResponse.success(realFile.getAbsolutePath()));
        }

        return CompletableFuture.supplyAsync(()->{
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
                file.delete();
                return JsonResponse.error(e.getMessage());
            }
        },executor);
    }
}

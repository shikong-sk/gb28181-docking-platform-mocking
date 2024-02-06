package cn.skcks.docking.gb28181.mocking.core.sip.service;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.skcks.docking.gb28181.common.json.JsonException;
import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCacheManager {
    private final DeviceProxyConfig deviceProxyConfig;

    @Qualifier(MockingExecutor.EXECUTOR_BEAN_NAME)
    private final Executor executor;

    private final ConcurrentMap<String, CompletableFuture<JsonResponse<String>>> tasks = new ConcurrentHashMap<>();

    public String dateFormat(Date date){
        return DateUtil.format(date, DatePattern.PURE_DATETIME_PATTERN);
    }

    public String fileName(String deviceCode, Date startTime, Date endTime){
        return StringUtils.joinWith("-", deviceCode, dateFormat(startTime), dateFormat(endTime));
    }

    @Async(MockingExecutor.EXECUTOR_BEAN_NAME)
    public void addTask(String deviceCode, Date startTime, Date endTime){
        String name = fileName(deviceCode, startTime, endTime);
        if(tasks.get(name) != null){
            return;
        }

        CompletableFuture<JsonResponse<String>> future = new CompletableFuture<>();
        tasks.put(name, future);
        downloadVideo(deviceCode,startTime,endTime, future);
    }

    public CompletableFuture<JsonResponse<String>> get(String deviceCode, Date startTime, Date endTime){
        String name = fileName(deviceCode, startTime, endTime);
        return tasks.get(name);
    }

    @SneakyThrows
    @Async(MockingExecutor.EXECUTOR_BEAN_NAME)
    protected void downloadVideo(String deviceCode, Date startTime, Date endTime, CompletableFuture<JsonResponse<String>> future) {
        final String url = UrlBuilder.of(deviceProxyConfig.getUrl())
                .addPath("video")
                .addQuery("device_id", deviceCode)
                .addQuery("begin_time", dateFormat(startTime))
                .addQuery("end_time", dateFormat(endTime))
                .addQuery("useDownload", true).build();
        File file = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(),fileName(deviceCode, startTime, endTime) + ".mp4.tmp").toFile();
        log.info("文件存储路径 => {}", file.getAbsolutePath());
        log.info("文件 {}, 是否存在: {}", file.getAbsolutePath(), file.exists());

        if(file.exists()){
            file.delete();
            log.info("删除已存但未完成下载的文件 => {}", file.getAbsolutePath());
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            FileChannel channel = outputStream.getChannel();
            FileLock lock = channel.lock();

            try (CloseableHttpAsyncClient asyncClient = HttpAsyncClients.custom().build()) {
                asyncClient.start();
                SimpleHttpRequest request = SimpleRequestBuilder.get(url).build();
                asyncClient.execute(request, new FutureCallback<>() {
                    @SneakyThrows
                    @Override
                    public void completed(SimpleHttpResponse response) {
                        InputStream inputStream = new ByteArrayInputStream(response.getBodyBytes());
                        IoUtil.copy(inputStream, outputStream);
                        log.info("视频下载完成 => {}", file.getAbsolutePath());
                        log.info("文件 {}, 是否存在: {}", file.getAbsolutePath(), file.exists());
                        File realFile = Paths.get(deviceProxyConfig.getPreDownloadForRecordInfo().getCachePath(),fileName(deviceCode, startTime, endTime) + ".mp4").toFile();
                        file.renameTo(realFile);
                        lock.release();
                        future.complete(JsonResponse.success(file.getAbsolutePath()));
                    }

                    @SneakyThrows
                    @Override
                    public void failed(Exception ex) {
                        log.info("视频下载失败 => {}, {}", file.getAbsolutePath(), url);
                        lock.release();
                        future.completeExceptionally(ex);
                    }

                    @SneakyThrows
                    @Override
                    public void cancelled() {
                        lock.release();
                        future.completeExceptionally(new JsonException("视频下载失败"));
                    }
                });
            }
        }
    }
}

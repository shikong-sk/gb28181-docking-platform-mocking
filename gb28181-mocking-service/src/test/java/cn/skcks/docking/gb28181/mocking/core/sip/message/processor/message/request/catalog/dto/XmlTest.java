package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto;


import cn.hutool.core.io.IoUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.util.IdUtil;
import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class XmlTest {
    @Test
    void test(){
        CatalogResponseDTO catalogResponseDTO = new CatalogResponseDTO();

        CatalogDeviceListDTO catalogDeviceListDTO = new CatalogDeviceListDTO();
        List<CatalogItemDTO> itemDTOList = new ArrayList<>();
        itemDTOList.add(CatalogItemDTO.builder().build());
        itemDTOList.add(CatalogItemDTO.builder().build());
        catalogDeviceListDTO.setDeviceList(itemDTOList);
        catalogDeviceListDTO.setNum(itemDTOList.size());

        catalogResponseDTO.setDeviceList(catalogDeviceListDTO);
        String xml = XmlUtils.toXml(catalogResponseDTO);
        log.info("{}", xml);

        log.info("{}", XmlUtils.toXml(catalogDeviceListDTO));
    }

    @Test
    void restTemplate() {
        final String url = UrlBuilder.of("http://192.168.2.3:18183")
                .addPath("video")
                .addQuery("device_id", "72439149X18C04DE739F3")
                .addQuery("begin_time", "20240206000500")
                .addQuery("end_time", "20240206001000")
                .addQuery("useDownload", true).build();
        log.info("请求地址 => {}", url);
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), IdUtil.fastSimpleUUID() + ".mp4");
        log.info("文件存储路径 => {}", path.toAbsolutePath());

        // 定义请求头的接收类型
        RequestCallback requestCallback = request -> request.getHeaders()
                .setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));

        RestTemplate restTemplate = new RestTemplate();
        // 对响应进行流式处理而不是将其全部加载到内存中
        restTemplate.execute(url, HttpMethod.GET, requestCallback, clientHttpResponse -> {
            Files.copy(clientHttpResponse.getBody(), path);
            return null;
        });
    }

    @Test
    void httpClient() throws IOException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final String url = "http://192.168.2.3:18183/video?end_time=20240206001000&begin_time=20240206000500&device_id=72439149X18C04DE739F3&useDownload=true";
        File file = Paths.get(System.getProperty("java.io.tmpdir"), IdUtil.fastSimpleUUID() + ".mp4").toFile();
        log.info("文件存储路径 => {}", file.getAbsolutePath());
        log.info("文件 {}, 是否存在: {}", file.getAbsolutePath(), file.exists());
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
                        lock.release();
                        countDownLatch.countDown();
                    }

                    @Override
                    public void failed(Exception ex) {
                        log.info("视频下载失败 => {}, {}", file.getAbsolutePath(), url);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        countDownLatch.countDown();
                    }
                });
            }
        }
        countDownLatch.await();
    }
}

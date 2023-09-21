package cn.skcks.docking.gb28181.mocking.service.gb28181.register;


import cn.hutool.core.collection.ListUtil;
import cn.skcks.docking.gb28181.common.json.JsonResponse;
import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.core.sip.utils.SipUtil;
import cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe.SipSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import cn.skcks.docking.gb28181.mocking.service.gb28181.keepalive.KeepaliveService;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import javax.sip.header.CallIdHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class RegisterService {
    private final DeviceService deviceService;

    private final SipSender sender;

    private final SipSubscribe subscribe;

    private final KeepaliveService keepaliveService;

    private static final int TIMEOUT = 60;

    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public DeferredResult<JsonResponse<Boolean>> register() {
        DeferredResult<JsonResponse<Boolean>> result = new DeferredResult<>(TimeUnit.SECONDS.toMillis(TIMEOUT));

        List<MockingDevice> allDevice = deviceService.getAllDevice();

        List<CompletableFuture<JsonResponse<Void>>[]> completableFutures = ListUtil.split(allDevice, 10).stream().map(items -> {
            CompletableFuture<JsonResponse<Void>>[] array = allDevice.stream().map(this::register).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(array);
            return array;
        }).toList();

        List<CompletableFuture<JsonResponse<Void>>> reduce = completableFutures.stream().map(item -> Arrays.stream(item).toList())
                .reduce(new ArrayList<>(), (prev, cur) -> {
                    prev.addAll(cur);
                    return prev;
                });

        Optional<JsonResponse<Void>> first = reduce.stream().map(item -> {
            try {
                return item.get();
            } catch (InterruptedException | ExecutionException e) {
                return null;
            }
        }).filter(item -> item == null || item.getCode() != 200).findFirst();
        first.ifPresentOrElse(item -> {
            log.info("执行失败 {}", item);
            result.setResult(JsonResponse.error(item.getMsg()));
        }, () -> result.setResult(JsonResponse.success(true)));
        return result;
    }

    @SneakyThrows
    public CompletableFuture<JsonResponse<Void>> register(MockingDevice device) {
        keepaliveService.unKeepalive(device);
        CompletableFuture<JsonResponse<Boolean>> result = new CompletableFuture<>();
        if(!device.getEnable()){
            result.complete(JsonResponse.success(null));
        }
        sender.sendRequest((provider, ip, port) -> {
            CallIdHeader callIdHeader = provider.getNewCallId();
            String callId = callIdHeader.getCallId();
            Request request = SipRequestBuilder.createRegisterRequest(device, ip, port, 1, SipUtil.generateFromTag(), null, callIdHeader);
            String key = GenericSubscribe.Helper.getKey(Request.REGISTER, device.getGbDeviceId(), callId);
            subscribe.getRegisterSubscribe().addPublisher(key);
            final ScheduledFuture<?>[] schedule = new ScheduledFuture<?>[1];
            Flow.Subscriber<SIPResponse> subscriber = new Flow.Subscriber<>() {
                Flow.Subscription subscription;
                private boolean usedAuthorization = false;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    log.debug("建立订阅 => {}", key);
                    subscription.request(1);
                }

                @SneakyThrows
                @Override
                public void onNext(SIPResponse response) {
                    int statusCode = response.getStatusCode();

                    if (statusCode == Response.UNAUTHORIZED && !usedAuthorization) {
                        usedAuthorization = true;
                        WWWAuthenticateHeader authorizationHeader = (WWWAuthenticateHeader) response.getHeader(WWWAuthenticateHeader.NAME);
                        provider.sendRequest(SipRequestBuilder.createRegisterRequestWithAuthorization(
                                device,
                                ip,
                                port,
                                response.getCSeq().getSeqNumber() + 1,
                                response.getFromTag(),
                                null,
                                callIdHeader,
                                authorizationHeader
                        ));
                        subscription.request(1);
                        return;
                    }

                    if (statusCode == Response.UNAUTHORIZED || statusCode == Response.FORBIDDEN) {
                        this.onComplete();
                        String reason = MessageFormat.format("设备: {0}({1}), 注册失败: 认证失败", device.getDeviceCode(), device.getGbDeviceId());
                        log.error(reason);
                        result.complete(JsonResponse.error(reason));
                        return;
                    }

                    if (statusCode == Response.OK) {
                        log.info("设备: {}({}), 注册成功", device.getDeviceCode(), device.getGbDeviceId());
                        result.complete(JsonResponse.success(null));
                        keepaliveService.keepalive(device);
                        this.onComplete();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("设备: {}({}), 注册失败 处理响应时出现异常: {}", device.getDeviceCode(), device.getGbDeviceId(), throwable.getMessage());
                    this.onComplete();
                }

                @Override
                public void onComplete() {
                    subscribe.getRegisterSubscribe().delPublisher(key);
                    schedule[0].cancel(false);
                    log.debug("结束订阅 => {}", key);
                }
            };
            subscribe.getRegisterSubscribe().addSubscribe(key, subscriber);
            schedule[0] = scheduledExecutorService.schedule(subscriber::onComplete, TIMEOUT / 2, TimeUnit.SECONDS);
            return request;
        });
        return result;
    }
}

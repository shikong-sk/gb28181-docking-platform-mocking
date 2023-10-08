package cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe;

import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import cn.skcks.docking.gb28181.mocking.core.sip.executor.MockingExecutor;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Slf4j
@Data
@RequiredArgsConstructor
@Service
public class SipSubscribe {
    @Qualifier(MockingExecutor.EXECUTOR_BEAN_NAME)
    private final Executor executor;
    private GenericSubscribe<SIPResponse> registerSubscribe;
    private GenericSubscribe<SIPRequest> ackSubscribe;
    private GenericSubscribe<SIPRequest> byeSubscribe;

    @PostConstruct
    private void init() {
        registerSubscribe = new RegisterSubscribe(executor);
        ackSubscribe = new AckSubscribe(executor);
        byeSubscribe = new ByeSubscribe(executor);
    }

    @PreDestroy
    private void destroy() {
        registerSubscribe.close();
        ackSubscribe.close();
        byeSubscribe.close();
    }
}

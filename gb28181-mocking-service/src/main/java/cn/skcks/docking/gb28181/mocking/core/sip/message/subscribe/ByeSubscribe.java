package cn.skcks.docking.gb28181.mocking.core.sip.message.subscribe;

import cn.skcks.docking.gb28181.core.sip.message.subscribe.GenericSubscribe;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

@RequiredArgsConstructor
public class ByeSubscribe implements GenericSubscribe<SIPRequest> {
    private final Executor executor;

    private static final Map<String, SubmissionPublisher<SIPRequest>> publishers = new ConcurrentHashMap<>();

    public void close() {
        Helper.close(publishers);
    }

    public void addPublisher(String key) {
        Helper.addPublisher(executor, publishers, key);
    }

    public SubmissionPublisher<SIPRequest> getPublisher(String key) {
        return Helper.getPublisher(publishers, key);
    }

    public void addSubscribe(String key, Flow.Subscriber<SIPRequest> subscribe) {
        Helper.addSubscribe(publishers, key, subscribe);
    }

    @Override
    public void delPublisher(String key) {
        Helper.delPublisher(publishers, key);
    }
}

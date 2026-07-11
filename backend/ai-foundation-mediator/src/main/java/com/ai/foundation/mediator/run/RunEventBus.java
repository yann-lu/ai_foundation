package com.ai.foundation.mediator.run;

import com.ai.foundation.com.stream.RunStreamEnvelope;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RunEventBus {

    private static final int MAX_REPLAY_SIZE = 200;

    private final ConcurrentMap<String, Sinks.Many<RunStreamEnvelope>> sinks = new ConcurrentHashMap<>();

    public void publish(String runCode, RunStreamEnvelope envelope) {
        if (runCode == null || envelope == null) {
            return;
        }
        Sinks.Many<RunStreamEnvelope> sink = sinks.computeIfAbsent(
                runCode, key -> Sinks.many().replay().limit(MAX_REPLAY_SIZE));
        sink.tryEmitNext(envelope);
        if (isTerminal(envelope.getEventType())) {
            sink.tryEmitComplete();
        }
    }

    public Flux<RunStreamEnvelope> subscribe(String runCode) {
        Sinks.Many<RunStreamEnvelope> sink = sinks.computeIfAbsent(
                runCode, key -> Sinks.many().replay().limit(MAX_REPLAY_SIZE));
        return sink.asFlux();
    }

    public void clear(String runCode) {
        sinks.remove(runCode);
    }

    private boolean isTerminal(String eventType) {
        return RunStreamEventTypeEnum.RUN_COMPLETE.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_ERROR.getCode().equals(eventType)
                || RunStreamEventTypeEnum.RUN_CANCELLED.getCode().equals(eventType);
    }
}

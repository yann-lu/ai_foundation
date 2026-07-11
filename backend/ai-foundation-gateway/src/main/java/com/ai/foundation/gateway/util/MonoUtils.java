package com.ai.foundation.gateway.util;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

public final class MonoUtils {

    private MonoUtils() {
    }

    public static <T> Mono<T> fromBlocking(Supplier<T> supplier) {
        return Mono.fromSupplier(supplier).subscribeOn(Schedulers.boundedElastic());
    }
}

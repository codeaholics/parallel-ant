package org.codeaholics.tools.build.pant;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorServiceFactoryImpl implements ExecutorServiceFactory {
    @Override
    public ExecutorService create(final int threads) {
        return Executors.newFixedThreadPool(threads);
    }
}

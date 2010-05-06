package org.codeaholics.tools.build.pant;

import java.util.concurrent.ExecutorService;

public interface ExecutorServiceFactory {
    public ExecutorService create(int threads);
}

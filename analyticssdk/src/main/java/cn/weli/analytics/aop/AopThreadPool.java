package cn.weli.analytics.aop;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AopThreadPool {
    private static AopThreadPool singleton;
    private static Executor mExecutor;

    public static AopThreadPool getInstance() {
        if (singleton == null) {
            synchronized (AopThreadPool.class) {
                if (singleton == null) {
                    singleton = new AopThreadPool();
                    mExecutor = Executors.newFixedThreadPool(5);
                }
            }
        }
        return singleton;
    }

    public void execute(Runnable runnable) {
        try {
            if (runnable != null) {
                mExecutor.execute(runnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

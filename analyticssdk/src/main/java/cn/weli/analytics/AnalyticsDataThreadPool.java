package cn.weli.analytics;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AnalyticsDataThreadPool {
    private static AnalyticsDataThreadPool singleton;
    private static Executor mExecutor;

    public static AnalyticsDataThreadPool getInstance() {
        if (singleton == null) {
            synchronized (AnalyticsDataThreadPool.class) {
                if (singleton == null) {
                    singleton = new AnalyticsDataThreadPool();
                    mExecutor = Executors.newFixedThreadPool(1);
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

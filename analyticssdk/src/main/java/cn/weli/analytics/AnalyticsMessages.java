package cn.weli.analytics;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import cn.weli.analytics.exceptions.ConnectErrorException;
import cn.weli.analytics.exceptions.DebugModeException;
import cn.weli.analytics.exceptions.InvalidDataException;
import cn.weli.analytics.exceptions.ResponseErrorException;
import cn.weli.analytics.utils.AnalyticsDataUtils;
import cn.weli.analytics.utils.DES;
import cn.weli.analytics.utils.JSONUtils;
import cn.weli.analytics.utils.LogUtils;
import cn.weli.analytics.utils.StringCompress;

/**
 * Manage communication of events with the internal database and the AnalyticsData servers.
 * <p/>
 * <p>This class straddles the thread boundary between user threads and
 * a logical AnalyticsData thread.
 */
class AnalyticsMessages {

    /**
     * Do not call directly. You should call AnalyticsMessages.getInstance()
     */
    /* package */ AnalyticsMessages(final Context context, final String packageName) {
        mContext = context;
        mDbAdapter = new DbAdapter(mContext, packageName/*dbName*/);
        mWorker = new Worker();
    }

    /**
     * Use this to get an instance of AnalyticsMessages instead of creating one directly
     * for yourself.
     *
     * @param messageContext should be the Main Activity of the application
     *                       associated with these messages.
     */
    public static AnalyticsMessages getInstance(final Context messageContext, final String
            packageName) {
        synchronized (sInstances) {
            final Context appContext = messageContext.getApplicationContext();
            final AnalyticsMessages ret;
            if (!sInstances.containsKey(appContext)) {
                ret = new AnalyticsMessages(appContext, packageName);
                sInstances.put(appContext, ret);
            } else {
                ret = sInstances.get(appContext);
            }
            return ret;
        }
    }

    public void enqueueEventMessage(final String type, final JSONObject eventJson) {
        try {
            synchronized (mDbAdapter) {
                //先存到数据库中
                int ret = mDbAdapter.addJSON(eventJson, DbAdapter.Table.EVENTS);
                if (ret < 0) {
                    String error = "Failed to enqueue the event: " + eventJson;
                    if (AnalyticsDataAPI.sharedInstance(mContext).isDebugMode()) {
                        throw new DebugModeException(error);
                    } else {
                        LogUtils.i(TAG, error);
                    }
                }

                final Message m = Message.obtain();
                m.what = FLUSH_QUEUE;

                if (AnalyticsDataAPI.sharedInstance(mContext).isDebugMode() || ret ==
                        DbAdapter.DB_OUT_OF_MEMORY_ERROR) {
                    mWorker.runMessage(m);
                } else {
                    // track_signup 立即发送
                    if (type.equals("track_signup") || ret > AnalyticsDataAPI.sharedInstance(mContext)
                            .getFlushBulkSize()) {
                        mWorker.runMessage(m);
                    } else {
                        final int interval = AnalyticsDataAPI.sharedInstance(mContext).getFlushInterval();
                        mWorker.runMessageOnce(m, interval);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.i(TAG, "enqueueEventMessage error:" + e);
        }
    }

    public void flush() {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;

        mWorker.runMessage(m);
    }

    public static byte[] slurp(final InputStream inputStream)
            throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[8192];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    public void sendData() {
        try {
            if (TextUtils.isEmpty(AnalyticsDataAPI.sharedInstance(mContext).getServerUrl())) {
                return;
            }
            //不是主进程
            if (!AnalyticsDataUtils.isMainProcess(mContext, AnalyticsDataAPI.sharedInstance(mContext).getMainProcessName())) {
                return;
            }

            //无网络
            if (!AnalyticsDataUtils.isNetworkAvailable(mContext)) {
                return;
            }

            //不符合同步数据的网络策略
            String networkType = AnalyticsDataUtils.networkType(mContext);
            if (!AnalyticsDataAPI.sharedInstance(mContext).isShouldFlush(networkType)) {
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        int count = 100;
        Toast toast = null;
        while (count > 0) {
            boolean deleteEvents = true;
            InputStream in = null;
            OutputStream out = null;
            BufferedOutputStream bout = null;
            HttpURLConnection connection = null;
            String[] eventsData;
            synchronized (mDbAdapter) {
                if (AnalyticsDataAPI.sharedInstance(mContext).isDebugMode()) {
                    //debug模式1条上传
                    eventsData = mDbAdapter.generateDataString(DbAdapter.Table.EVENTS, 1);
                } else {
                    //取50条上传
                    eventsData = mDbAdapter.generateDataString(DbAdapter.Table.EVENTS, 50);
                }
            }
            if (eventsData == null) {
                return;
            }

            final String lastId = eventsData[0];
            final String rawMessage = eventsData[1];
            String errorMessage = null;

            try {

                String data;
                try {
                    data = encodeData(rawMessage);
                } catch (IOException e) {
                    // 格式错误，直接将数据删除
                    throw new InvalidDataException(e);
                }catch (Exception e){
                    throw new InvalidDataException(e);
                }

                try {
                    final URL url = new URL(AnalyticsDataAPI.sharedInstance(mContext).getServerUrl());
                    LogUtils.d(TAG,"server url:"+url);
                    connection = (HttpURLConnection) url.openConnection();
                    try {
                        String ua = AnalyticsDataUtils.getUserAgent(mContext);
                        if (TextUtils.isEmpty(ua)) {
                            ua = "WeliAnalytics Android SDK";
                        }
                        connection.addRequestProperty("User-Agent", ua);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
//                    if (AnalyticsDataAPI.sharedInstance(mContext).isDebugMode() && !AnalyticsDataAPI.sharedInstance
//                            (mContext).isDebugWriteData()) {
//                        connection.addRequestProperty("Dry-Run", "true");
//                    }
                    Uri.Builder builder = new Uri.Builder();
                    builder.appendQueryParameter("x", data);
//                    builder.appendQueryParameter("gzip", "1");
                    builder.appendQueryParameter("cps", "gzip");
//                    if (!TextUtils.isEmpty(data)) {
//                        builder.appendQueryParameter("crc", String.valueOf(data.hashCode()));
//                    }

                    String query = builder.build().getEncodedQuery();

                    connection.setFixedLengthStreamingMode(query.getBytes().length);
                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    out = connection.getOutputStream();
                    bout = new BufferedOutputStream(out);
                    bout.write(query.getBytes("UTF-8"));
                    bout.flush();
                    bout.close();
                    bout = null;
                    out.close();
                    out = null;

                    int responseCode = connection.getResponseCode();
                    try {
                        in = connection.getInputStream();
                    } catch (FileNotFoundException e) {
                        in = connection.getErrorStream();
                    }
                    byte[] responseBody = slurp(in);
                    in.close();
                    in = null;

                    String response = new String(responseBody, "UTF-8");

                    //if (AnalyticsDataAPI.sharedInstance(mContext).isDebugMode()) {
                        if (responseCode == 200) {
                            LogUtils.i(TAG, String.format("valid message: \n%s", JSONUtils.formatJson(rawMessage)));
                        } else {
                            LogUtils.i(TAG, String.format("invalid message: \n%s", JSONUtils.formatJson(rawMessage)));
                            LogUtils.i(TAG, String.format(Locale.CHINA, "ret_code: %d", responseCode));
                            LogUtils.i(TAG, String.format(Locale.CHINA, "ret_content: %s", response));
                        }
                    //}

                    if (responseCode < 200 || responseCode >= 300) {
                        // 校验错误，直接将数据删除
                        throw new ResponseErrorException(String.format("flush failure with response '%s'",
                                response));
                    }
                } catch (IOException e) {
                    throw new ConnectErrorException(e);
                }
            } catch (ConnectErrorException e) {
                deleteEvents = false;
                errorMessage = "Connection error: " + e.getMessage();
            } catch (InvalidDataException e) {
                deleteEvents = true;
                errorMessage = "Invalid data: " + e.getMessage();
            } catch (ResponseErrorException e) {
                deleteEvents = true;
                errorMessage = "ResponseErrorException: " + e.getMessage();
            } catch (Exception e) {
                deleteEvents = false;
                errorMessage = "Exception: " + e.getMessage();
            } finally {
                boolean isDebugMode = AnalyticsDataAPI.sharedInstance(mContext).isDebugMode();
                if (!TextUtils.isEmpty(errorMessage)) {
                    if (isDebugMode || AnalyticsDataAPI.ENABLE_LOG) {
                        LogUtils.i(TAG, errorMessage);
                        if (isDebugMode) {
                            try {
                                /**
                                 * 问题：https://www.jianshu.com/p/1445e330114b
                                 * 目前没有比较好的解决方案，暂时规避，只对开启 debug 模式下有影响
                                 */
                                if (Build.VERSION.SDK_INT != 25) {
                                    if (toast != null) {
                                        toast.cancel();
                                    }
                                    toast = Toast.makeText(mContext, errorMessage, Toast.LENGTH_SHORT);
                                    toast.show();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (deleteEvents) {
                    count = mDbAdapter.cleanupEvents(lastId, DbAdapter.Table.EVENTS);
                    LogUtils.i(TAG, String.format(Locale.CHINA, "Events flushed. [left = %d]", count));
                } else {
                    count = 0;
                }
                if (null != bout)
                    try {
                        bout.close();
                    } catch (final IOException e) {
                    }
                if (null != out)
                    try {
                        out.close();
                    } catch (final IOException e) {
                    }
                if (null != in)
                    try {
                        in.close();
                    } catch (final IOException e) {
                    }
                if (null != connection)
                    connection.disconnect();
            }
        }
    }

    private String encodeData(final String rawMessage) throws IOException, BadPaddingException, IllegalBlockSizeException {
        DES d = new DES();
        String data = d.encrypt(rawMessage, false);
        LogUtils.i(TAG, "encodeData DES message: " + data);
        LogUtils.i(TAG, "encodeData DES message size: " + data.length());
        String compressData = StringCompress.compressUTF8(data);
        LogUtils.i(TAG, "encodeData compress message: " + compressData);
        LogUtils.i(TAG, "encodeData compress message: " + compressData.length());
        return compressData;
    }

    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
    // XXX: Worker class is unnecessary, should be just a subclass of HandlerThread
    private class Worker {

        public Worker() {
            final HandlerThread thread =
                    new HandlerThread("cn.weli.analytics.Worker",
                            Thread.MIN_PRIORITY);
            thread.start();
            mHandler = new AnalyticsMessageHandler(thread.getLooper());
        }

        public void runMessage(Message msg) {
            synchronized (mHandlerLock) {
                if (mHandler == null) {
                    // We died under suspicious circumstances. Don't try to send any more events.
                    LogUtils.i(TAG, "Dead worker dropping a message: " + msg.what);
                } else {
                    mHandler.sendMessage(msg);
                }
            }
        }

        public void runMessageOnce(Message msg, long delay) {
            synchronized (mHandlerLock) {
                if (mHandler == null) {
                    // We died under suspicious circumstances. Don't try to send any more events.
                    LogUtils.i(TAG, "Dead worker dropping a message: " + msg.what);
                } else {
                    if (!mHandler.hasMessages(msg.what)) {
                        mHandler.sendMessageDelayed(msg, delay);
                    }
                }
            }
        }

        private class AnalyticsMessageHandler extends Handler {

            public AnalyticsMessageHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    if (msg.what == FLUSH_QUEUE) {
                        sendData();
                    }
                } catch (final RuntimeException e) {
                    LogUtils.i(TAG, "Worker threw an unhandled exception", e);
                }
            }
        }

        private final Object mHandlerLock = new Object();
        private Handler mHandler;
    }

    // Used across thread boundaries
    private final Worker mWorker;
    private final Context mContext;
    private final DbAdapter mDbAdapter;

    // Messages for our thread
    private static final int FLUSH_QUEUE = 3;

    private static final String TAG = "WELI.AnalyticsMessages";

    private static final Map<Context, AnalyticsMessages> sInstances =
            new HashMap<Context, AnalyticsMessages>();

}

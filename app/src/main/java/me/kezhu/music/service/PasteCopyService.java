package me.kezhu.music.service;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import me.kezhu.music.utils.ToastUtils;


/**
 * 音乐播放后台服务
 * Created by wcy on 2015/11/27.
 */
public class PasteCopyService extends Service {
    private static final String TAG = "Service";

    ClipboardManager clipboardManager;

    private String mPreviousText = "";
    public class PlayBinder extends Binder {
        public PasteCopyService getService() {
            return PasteCopyService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: " + getClass().getSimpleName());
        clipboardManager =(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);

        clipboardManager.addPrimaryClipChangedListener(new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                ClipData clipData = clipboardManager.getPrimaryClip();
                ClipData.Item item = clipData.getItemAt(0);
                if(null == item || null == item.getText()){
                    return;
                }
                if(mPreviousText.equals(item.getText().toString())){ return;}
                else{
                    mPreviousText = item.getText().toString();
                    ToastUtils.show("您有新的剪贴了！");
                }
            }
        });
    }



    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new PlayBinder();
    }

    public static void startCommand(Context context) {
        Intent intent = new Intent(context, PasteCopyService.class);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {

            }
        }
        return START_NOT_STICKY;
    }
}

package me.kezhu.music.webview;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.WebView;
import android.webkit.WebViewClient;
/**
 * Created by hzwangchenyan on 2017/2/8.
 */
public class MyWebViewClient extends WebViewClient {

    private WebView mWebView;
    private ProgressDialog progressDialog;//加载界面的菊花
    private Context context;//加载界面的菊花
    private String LAST_OPEN_URL;
    public MyWebViewClient(Context context,WebView mWebView,ProgressDialog progressDialog,
                           String LAST_OPEN_URL){
        this.context = context;
        this.mWebView = mWebView;
        this.progressDialog = progressDialog;
        this.LAST_OPEN_URL = LAST_OPEN_URL;
    }
    /**
     * 当打开超链接的时候，回调的方法
     * WebView：自己本身mWebView
     * url：即将打开的url
     */
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        //自己处理新的url
        LAST_OPEN_URL = url;
        mWebView.loadUrl(url);
        return true;//true就是自己处理
    }
    //重写页面打开和结束的监听。添加友好，弹出菊花
    /**
     * 界面打开的回调
     */
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        System.out.println("onPageStarted");
        if(progressDialog!=null&&progressDialog.isShowing()){
            progressDialog.dismiss();
        }
        //弹出菊花
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("提示");
        progressDialog.setMessage("正在努力加载……");
        progressDialog.show();

    }
    /**
     * 界面打开完毕的回调
     */
    @Override
    public void onPageFinished(WebView view, String url) {
        System.out.println("onPageFinished");
        //隐藏菊花:不为空，正在显示。才隐藏
        if(progressDialog!=null&&progressDialog.isShowing()){
            progressDialog.dismiss();
        }

    }
}

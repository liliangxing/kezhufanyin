package me.kezhu.music.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import me.kezhu.music.adapter.FragmentAdapter;
import me.kezhu.music.service.AudioPlayer;
import me.kezhu.music.service.QuitTimer;
import me.kezhu.music.utils.PermissionReq;
import me.kezhu.music.utils.SystemUtils;
import me.kezhu.music.utils.ToastUtils;
import me.kezhu.music.utils.binding.Bind;
import me.kezhu.music.R;
import me.kezhu.music.constants.Extras;
import me.kezhu.music.constants.Keys;
import me.kezhu.music.executor.ControlPanel;
import me.kezhu.music.executor.NaviMenuExecutor;
import me.kezhu.music.executor.WeatherExecutor;
import me.kezhu.music.fragment.LocalMusicFragment;
import me.kezhu.music.fragment.PlayFragment;
import me.kezhu.music.fragment.WebviewFragment;
import me.kezhu.music.webview.MyWebChromeClient;

public class MusicActivity extends BaseActivity implements View.OnClickListener, QuitTimer.OnTimerListener,
        NavigationView.OnNavigationItemSelectedListener, ViewPager.OnPageChangeListener {
    @Bind(R.id.drawer_layout)
    private DrawerLayout drawerLayout;
    @Bind(R.id.navigation_view)
    private NavigationView navigationView;
    @Bind(R.id.iv_menu)
    private ImageView ivMenu;
    @Bind(R.id.iv_share)
    private ImageView iv_share;
    @Bind(R.id.tv_local_music)
    private TextView tvLocalMusic;
    @Bind(R.id.tv_online_music)
    private TextView tvOnlineMusic;
    @Bind(R.id.viewpager)
    private ViewPager mViewPager;
    @Bind(R.id.fl_play_bar)
    private FrameLayout flPlayBar;

    private ClipboardManager cm;

    private View vNavigationHeader;
    private LocalMusicFragment mLocalMusicFragment;
    private WebviewFragment mSheetListFragment;
    private PlayFragment mPlayFragment;
    private ControlPanel controlPanel;
    public NaviMenuExecutor naviMenuExecutor;
    private MenuItem timerItem;
    private boolean isPlayFragmentShow;
    private WebView mWebView;
    @Bind(R.id.video_view)
    public FrameLayout videoview;
    private ProgressDialog progressDialog;//加载界面的菊花
    private View xCustomView;
    public static MusicActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);
        instance = this;
    }

    /**
     * 当横竖屏切换时会调用该方法
     * @author
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i("testwebview", "=====<<<  onConfigurationChanged  >>>=====");
        super.onConfigurationChanged(newConfig);

        if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE){
            Log.i("webview", "   现在是横屏1");
        }else if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Log.i("webview", "   现在是竖屏1");
        }
    }

    public void showHideAddress() {
        mSheetListFragment.showHideAddress();
    }

    public void setHomePage() {
        mSheetListFragment.setHomePage();
    }

    @Override
    protected void onServiceBound() {
        setupView();
        //updateWeather();
        controlPanel = new ControlPanel(flPlayBar);
        naviMenuExecutor = new NaviMenuExecutor(this);
        AudioPlayer.get().addOnPlayEventListener(controlPanel);
        QuitTimer.get().setOnTimerListener(this);
        parseIntent();
        //mViewPager.setCurrentItem(1); 放在这里不行
        mWebView = mSheetListFragment.mWebView;
        mWebView.setHorizontalFadingEdgeEnabled(true);
        mWebView.setScrollbarFadingEnabled(true);
        //mViewPager.setCurrentItem(1);
        mWebView.setWebChromeClient(new MyWebChromeClient(this,this,progressDialog){

            @Override
            //播放网络视频时全屏会被调用的方法
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback)
            {
                //进入全屏
                xCustomView = view;
                videoview.setVisibility(View.VISIBLE);
                videoview.addView(xCustomView);
                videoview.bringToFront();

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);//设置横屏
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//设置全屏

            }

            @Override
            //视频播放退出全屏会被调用的
            public void onHideCustomView() {

                if (xCustomView == null)//不是全屏播放状态
                    return;
                xCustomView.setVisibility(View.GONE);

                // Remove the custom view from its container.
                videoview.removeView(xCustomView);
                xCustomView = null;
                videoview.setVisibility(View.GONE);

                mWebView.setVisibility(View.VISIBLE);
                // Hide the custom view.
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);//清除全屏

                //Log.i(LOGTAG, "set it to webVew");
            }


        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        parseIntent();
    }

    private void setupView() {
        // add navigation header
        vNavigationHeader = LayoutInflater.from(this).inflate(R.layout.navigation_header, navigationView, false);
        navigationView.addHeaderView(vNavigationHeader);

        // setup view pager
        mLocalMusicFragment = new LocalMusicFragment();
        mSheetListFragment = new WebviewFragment();
        FragmentAdapter adapter = new FragmentAdapter(getSupportFragmentManager());
        adapter.addFragment(mLocalMusicFragment);
        adapter.addFragment(mSheetListFragment);
        mViewPager.setAdapter(adapter);
        tvLocalMusic.setSelected(true);

        ivMenu.setOnClickListener(this);
        iv_share.setOnClickListener(this);
        tvLocalMusic.setOnClickListener(this);
        tvOnlineMusic.setOnClickListener(this);
        flPlayBar.setOnClickListener(this);
        mViewPager.addOnPageChangeListener(this);
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void updateWeather() {
        PermissionReq.with(this)
                .permissions(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                .result(new PermissionReq.Result() {
                    @Override
                    public void onGranted() {
                        new WeatherExecutor(MusicActivity.this, vNavigationHeader).execute();
                    }

                    @Override
                    public void onDenied() {
                        ToastUtils.show(R.string.no_permission_location);
                    }
                })
                .request();
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if (intent.hasExtra(Extras.EXTRA_NOTIFICATION)) {
            showPlayingFragment();
            setIntent(new Intent());
        }
    }

    @Override
    public void onTimer(long remain) {
        if (timerItem == null) {
            timerItem = navigationView.getMenu().findItem(R.id.action_timer);
        }
        String title = getString(R.string.menu_timer);
        timerItem.setTitle(remain == 0 ? title : SystemUtils.formatTime(title + "(mm:ss)", remain));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_menu:
                drawerLayout.openDrawer(GravityCompat.START);
                break;
            case R.id.iv_share:
                shareWeixin();
               /* Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app, getString(R.string.app_name)));
                startActivity(Intent.createChooser(intent, getString(R.string.share)));*/
                break;
            case R.id.tv_local_music:
                mViewPager.setCurrentItem(0);
                break;
            case R.id.tv_online_music:
                mViewPager.setCurrentItem(1);
                break;
            case R.id.fl_play_bar:
                showPlayingFragment();
                break;
        }
    }

    private void shareWeixin(){
        Intent intent = new Intent(this, SubscribeMessageActivity.class);
        intent.putExtra("title", mWebView.getTitle());
        intent.putExtra("url", mWebView.getUrl());

        //获取剪贴板管理器：
        cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData cmData = cm.getPrimaryClip();
        String content = null;
        if(null != cmData) {
            ClipData.Item item = cmData.getItemAt(0);
            content = item.getText().toString();
        }
        intent.putExtra("content", content);
        startActivity(intent);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawerLayout.closeDrawers();
        handler.postDelayed(() -> item.setChecked(false), 500);
        return naviMenuExecutor.onNavigationItemSelected(item);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) mWebView.getLayoutParams();
        if (position == 0) {
            tvLocalMusic.setSelected(true);
            tvOnlineMusic.setSelected(false);
            iv_share.setVisibility(View.GONE);
            flPlayBar.setVisibility(View.VISIBLE);
            layoutParams.bottomMargin =getResources().getDimensionPixelOffset(R.dimen.play_bar_height);
            mWebView.setLayoutParams(layoutParams);
        } else {
            tvLocalMusic.setSelected(false);
            tvOnlineMusic.setSelected(true);
            iv_share.setVisibility(View.VISIBLE);
            flPlayBar.setVisibility(View.GONE);
            layoutParams.bottomMargin=0;
            mWebView.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public void showPlayingFragment() {
        if (isPlayFragmentShow) {
            return;
        }

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.fragment_slide_up, 0);
        if (mPlayFragment == null) {
            mPlayFragment = new PlayFragment();
            ft.replace(android.R.id.content, mPlayFragment);
        } else {
            ft.show(mPlayFragment);
        }
        ft.commitAllowingStateLoss();
        isPlayFragmentShow = true;
    }

    private void hidePlayingFragment() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(0, R.anim.fragment_slide_down);
        ft.hide(mPlayFragment);
        ft.commitAllowingStateLoss();
        isPlayFragmentShow = false;
    }

    @Override
    public void onBackPressed() {
        if (mPlayFragment != null && isPlayFragmentShow) {
            hidePlayingFragment();
            return;
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers();
            return;
        }

        super.onBackPressed();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(Keys.VIEW_PAGER_INDEX, mViewPager.getCurrentItem());
        mLocalMusicFragment.onSaveInstanceState(outState);
        mSheetListFragment.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        mViewPager.post(() -> {
            mViewPager.setCurrentItem(savedInstanceState.getInt(Keys.VIEW_PAGER_INDEX), false);
            mLocalMusicFragment.onRestoreInstanceState(savedInstanceState);
            mSheetListFragment.onRestoreInstanceState(savedInstanceState);
        });
    }

    @Override
    protected void onDestroy() {
        AudioPlayer.get().removeOnPlayEventListener(controlPanel);
        QuitTimer.get().setOnTimerListener(null);
        if (mWebView != null) {
            mWebView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK://返回键
                if( mWebView.canGoBack() && mViewPager.getCurrentItem()==1
                && !isPlayFragmentShow){
                    mWebView.goBack();
                    return true;
                }
                if(!isPlayFragmentShow)// 右键处理
                {
                    moveTaskToBack(true);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);//返回键的super处理的就是退出应用
    }
}

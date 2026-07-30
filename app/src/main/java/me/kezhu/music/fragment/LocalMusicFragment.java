package me.kezhu.music.fragment;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import me.kezhu.music.activity.MusicActivity;
import me.kezhu.music.activity.MusicInfoActivity;
import me.kezhu.music.adapter.OnMoreClickListener;
import me.kezhu.music.adapter.PlaylistAdapter;
import me.kezhu.music.enums.PlayModeEnum;
import me.kezhu.music.loader.MusicLoaderCallback;
import me.kezhu.music.model.Music;
import me.kezhu.music.service.AudioPlayer;
import me.kezhu.music.service.OnPlayerEventListener;
import me.kezhu.music.service.QuitTimer;
import me.kezhu.music.storage.preference.Preferences;
import me.kezhu.music.utils.PermissionReq;
import me.kezhu.music.utils.ToastUtils;
import me.kezhu.music.utils.binding.Bind;
import me.kezhu.music.R;
import me.kezhu.music.application.AppCache;
import me.kezhu.music.constants.Keys;
import me.kezhu.music.constants.RequestCode;
import me.kezhu.music.constants.RxBusTags;

public class LocalMusicFragment extends BaseFragment implements AdapterView.OnItemClickListener, OnMoreClickListener,
        AdapterView.OnItemLongClickListener {
    @Bind(R.id.lv_local_music)
    private ListView lvLocalMusic;
    @Bind(R.id.v_searching)
    private TextView vSearching;

    private Loader<Cursor> loader;
    private PlaylistAdapter adapter;

    private static boolean sAutoPlayDone;

    private Music targetMusic;
    private Music amitabhaMusic;
    private Music vows48Music;

    private enum SeqState { NONE, AMITABHA, VOWS }
    private SeqState seqState = SeqState.NONE;
    private int seqPlayCount;

    private AlertDialog cancelTimerDialog;

    private OnPlayerEventListener playEventListener = new OnPlayerEventListener() {
        @Override
        public void onChange(Music music) {
            if (seqState == SeqState.NONE) return;

            Music expected = (seqState == SeqState.AMITABHA) ? amitabhaMusic : vows48Music;
            if (expected == null || !expected.equals(music)) return;

            seqPlayCount++;
            if (seqPlayCount >= 3) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        advanceSequence();
                    }
                });
            }
        }

        @Override
        public void onPlayerStart() {
        }

        @Override
        public void onPlayerPause() {
        }

        @Override
        public void onPublish(int progress) {
        }

        @Override
        public void onBufferingUpdate(int percent) {
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_local_music, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new PlaylistAdapter(AppCache.get().getLocalMusicList());
        adapter.setOnMoreClickListener(this);
        lvLocalMusic.setAdapter(adapter);
        AudioPlayer.get().addOnPlayEventListener(playEventListener);
        loadMusic();
    }

    private void loadMusic() {
        lvLocalMusic.setVisibility(View.GONE);
        vSearching.setVisibility(View.VISIBLE);
        PermissionReq.with(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .result(new PermissionReq.Result() {
                    @Override
                    public void onGranted() {
                        initLoader();
                    }

                    @Override
                    public void onDenied() {
                        ToastUtils.show(R.string.no_permission_storage);
                        lvLocalMusic.setVisibility(View.VISIBLE);
                        vSearching.setVisibility(View.GONE);
                    }
                })
                .request();
    }

    private void initLoader() {
        loader = getActivity().getLoaderManager().initLoader(0, null, new MusicLoaderCallback(getContext(), value -> {
            AppCache.get().getLocalMusicList().clear();
            AppCache.get().getLocalMusicList().addAll(value);
            lvLocalMusic.setVisibility(View.VISIBLE);
            vSearching.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();

            findSpecialFiles(value);
            if (!sAutoPlayDone) {
                sAutoPlayDone = true;
                checkAndAutoPlay();
            }
        }));
    }

    private void findSpecialFiles(List<Music> list) {
        targetMusic = null;
        amitabhaMusic = null;
        vows48Music = null;
        for (Music m : list) {
            String fn = m.getFileName();
            if (fn == null) fn = m.getTitle();
            if (fn == null) continue;

            if (targetMusic == null && fn.contains("61-126-0001")) {
                targetMusic = m;
            }
            if (amitabhaMusic == null && fn.contains("佛说阿弥陀经")) {
                amitabhaMusic = m;
            }
            if (vows48Music == null && fn.contains("四十八大愿")) {
                vows48Music = m;
            }
        }
    }

    private void checkAndAutoPlay() {
        if (targetMusic == null) return;
        if (AudioPlayer.get().isPlaying() || AudioPlayer.get().isPreparing()) return;

        if (Preferences.getPlayMode() != PlayModeEnum.SINGLE.value()) {
            Preferences.savePlayMode(PlayModeEnum.SINGLE.value());
        }

        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int targetVolume = (hour >= 23) ? 16 : 20;
        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

        QuitTimer.get().start(30 * 60 * 1000);

        AudioPlayer.get().addAndPlay(targetMusic);

        showCancelTimerDialog();
    }

    private void showCancelTimerDialog() {
        if (getContext() == null) return;

        cancelTimerDialog = new AlertDialog.Builder(getContext())
                .setMessage("取消定时播放")
                .setNegativeButton("取消", (dialog, which) -> {
                    QuitTimer.get().stop();
                    ToastUtils.show("定时播放已取消");
                })
                .setOnCancelListener(dialog -> {
                    QuitTimer.get().stop();
                    ToastUtils.show("定时播放已取消");
                })
                .create();
        cancelTimerDialog.setCanceledOnTouchOutside(false);
        cancelTimerDialog.show();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (cancelTimerDialog != null && cancelTimerDialog.isShowing()) {
                    cancelTimerDialog.dismiss();
                }
            }
        }, 6000);
    }

    @Subscribe(tags = { @Tag(RxBusTags.SCAN_MUSIC) })
    public void scanMusic(Object object) {
        if (loader != null) {
            loader.forceLoad();
        }
    }

    @Override
    protected void setListener() {
        lvLocalMusic.setOnItemClickListener(this);
        lvLocalMusic.setOnItemLongClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Music music = AppCache.get().getLocalMusicList().get(position);
        String fn = music.getFileName();
        if (fn == null) fn = music.getTitle();
        final String fileName = fn;

        if (fileName != null && (fileName.contains("佛说阿弥陀经") || fileName.contains("四十八大愿"))) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(getContext());
            String title = music.getTitle();
            String msg = getString(R.string.play_music, title);
            dialog.setMessage(msg);
            dialog.setPositiveButton(R.string.cancel, null);
            dialog.setNegativeButton(R.string.play, (dialog1, which) -> {
                SeqState state = fileName.contains("佛说阿弥陀经") ? SeqState.AMITABHA : SeqState.VOWS;
                startSequence(music, state);
                ToastUtils.show("已添加到播放列表");
            });
            dialog.show();
            return;
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext());
        String title = music.getTitle();
        String msg = getString(R.string.play_music, title);
        dialog.setMessage(msg);
        dialog.setPositiveButton(R.string.cancel, null);
        dialog.setNegativeButton(R.string.play, (dialog1, which) -> {
            AudioPlayer.get().addAndPlay(music);
            ToastUtils.show("已添加到播放列表");
        });
        dialog.show();
    }

    private void startSequence(Music music, SeqState state) {
        if (Preferences.getPlayMode() != PlayModeEnum.SINGLE.value()) {
            Preferences.savePlayMode(PlayModeEnum.SINGLE.value());
        }

        seqState = state;
        seqPlayCount = 0;
        AudioPlayer.get().addAndPlay(music);
    }

    private void advanceSequence() {
        seqPlayCount = 0;

        switch (seqState) {
            case AMITABHA:
                if (vows48Music != null) {
                    seqState = SeqState.VOWS;
                    AudioPlayer.get().addAndPlay(vows48Music);
                } else {
                    fallbackToTarget();
                }
                break;
            case VOWS:
                fallbackToTarget();
                break;
        }
    }

    private void fallbackToTarget() {
        seqState = SeqState.NONE;
        if (targetMusic != null) {
            AudioPlayer.get().addAndPlay(targetMusic);
        } else {
            AudioPlayer.get().stopPlayer();
        }
    }

    private int minute = 20;

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Music music = AppCache.get().getLocalMusicList().get(position);
        AudioPlayer.get().addAndPlay(music);
        MusicActivity.instance.naviMenuExecutor.startTimer(minute);
        minute = minute + 10;
        return true;
    }

    @Override
    public void onMoreClick(final int position) {
        Music music = AppCache.get().getLocalMusicList().get(position);
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext());
        dialog.setTitle(music.getTitle());
        dialog.setItems(R.array.local_music_dialog, (dialog1, which) -> {
            switch (which) {
                case 0:
                    shareMusic(music);
                    break;
                case 1:
                    MusicInfoActivity.start(getContext(), music);
                    break;
                case 2:
                    requestSetRingtone(music);
                    break;
                case 3:
                    deleteMusic(music);
                    break;
            }
        });
        dialog.show();
    }

    private void shareMusic(Music music) {
        File file = new File(music.getPath());
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("audio/*");
        Uri data;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            data = FileProvider.getUriForFile(getContext(), "me.kezhu.music.fileProvider", file);
        } else {
            data = Uri.fromFile(file);
        }
        intent.putExtra(Intent.EXTRA_STREAM, data);
        startActivity(Intent.createChooser(intent, getString(R.string.share)));
    }

    private void requestSetRingtone(final Music music) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(getContext())) {
            ToastUtils.show(R.string.no_permission_setting);
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            startActivityForResult(intent, RequestCode.REQUEST_WRITE_SETTINGS);
        } else {
            setRingtone(music);
        }
    }

    private void setRingtone(Music music) {
        Uri uri = MediaStore.Audio.Media.getContentUriForPath(music.getPath());
        Cursor cursor = getContext().getContentResolver()
                .query(uri, null, MediaStore.MediaColumns.DATA + "=?", new String[] { music.getPath() }, null);
        if (cursor == null) {
            return;
        }
        if (cursor.moveToFirst() && cursor.getCount() > 0) {
            String _id = cursor.getString(0);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.IS_MUSIC, true);
            values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
            values.put(MediaStore.Audio.Media.IS_ALARM, false);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
            values.put(MediaStore.Audio.Media.IS_PODCAST, false);

            getContext().getContentResolver()
                    .update(uri, values, MediaStore.MediaColumns.DATA + "=?", new String[] { music.getPath() });
            Uri newUri = ContentUris.withAppendedId(uri, Long.valueOf(_id));
            RingtoneManager.setActualDefaultRingtoneUri(getContext(), RingtoneManager.TYPE_RINGTONE, newUri);
            ToastUtils.show(R.string.setting_ringtone_success);
        }
        cursor.close();
    }

    private void deleteMusic(final Music music) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext());
        String title = music.getTitle();
        String msg = getString(R.string.delete_music, title);
        dialog.setMessage(msg);
        dialog.setPositiveButton(R.string.delete, (dialog1, which) -> {
            File file = new File(music.getPath());
            if (file.delete()) {
                Intent intent =
                        new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://".concat(music.getPath())));
                getContext().sendBroadcast(intent);
            }
        });
        dialog.setNegativeButton(R.string.cancel, null);
        dialog.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RequestCode.REQUEST_WRITE_SETTINGS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(getContext())) {
                ToastUtils.show(R.string.grant_permission_setting);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        int position = lvLocalMusic.getFirstVisiblePosition();
        int offset = (lvLocalMusic.getChildAt(0) == null) ? 0 : lvLocalMusic.getChildAt(0).getTop();
        outState.putInt(Keys.LOCAL_MUSIC_POSITION, position);
        outState.putInt(Keys.LOCAL_MUSIC_OFFSET, offset);
    }

    public void onRestoreInstanceState(final Bundle savedInstanceState) {
        lvLocalMusic.post(() -> {
            int position = savedInstanceState.getInt(Keys.LOCAL_MUSIC_POSITION);
            int offset = savedInstanceState.getInt(Keys.LOCAL_MUSIC_OFFSET);
            lvLocalMusic.setSelectionFromTop(position, offset);
        });
    }

    @Override
    public void onDestroy() {
        AudioPlayer.get().removeOnPlayEventListener(playEventListener);
        if (cancelTimerDialog != null && cancelTimerDialog.isShowing()) {
            cancelTimerDialog.dismiss();
        }
        super.onDestroy();
    }
}

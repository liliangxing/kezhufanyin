package me.kezhu.music.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import me.kezhu.music.R;


/**
 * Created by willenwu on 2018/5/10
 */

public class SubscribeMessageActivity extends BaseActivity {

    private ClipboardManager cm;
    private ClipData mClipData;
    private CheckBox miniProgramAppUrlEt;
    private TextView miniProgramUrlEt;
    private Intent data;
    private String contentText;
    private EditText miniProgramContentEt;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content_clip);


        miniProgramUrlEt = (TextView)findViewById(R.id.mini_program_url_et);
        final TextView miniProgramTitleEt = (TextView)findViewById(R.id.mini_program_title_et);
        miniProgramContentEt = (EditText)findViewById(R.id.mini_program_content_et);
        miniProgramAppUrlEt = (CheckBox)findViewById(R.id.mini_program_app_url_et);

        data = getIntent();

        final String title = getString(R.string.share_title_text)+data.getStringExtra("title");
        String content = data.getStringExtra("content");
        miniProgramTitleEt.setText(title);
        miniProgramContentEt.setText(content);

        generateUrl();

        Button shareAllMsgBtn = (Button)findViewById(R.id.share_all_message_btn);
        shareAllMsgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareWeixin(contentText,title);
            }
        });

        miniProgramContentEt.addTextChangedListener(new PhoneNumberFormattingTextWatcher() {
            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                 generateUrl();
            }
        });

        Button sharePartMsgBtn = (Button)findViewById(R.id.share_part_message_btn);
        sharePartMsgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareWeixin(null,title);
            }
        });
    }

    private void generateUrl(){
        String baseUrl = data.getStringExtra("url");
        contentText = miniProgramContentEt.getText().toString();
        /*if(miniProgramAppUrlEt.isChecked()){
            int index = baseUrl.indexOf("?");
            String subContent=null;
            try {
                subContent = URLEncoder.encode(
                        contentText.substring(0,contentText.length()>5?5:contentText.length()), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            subContent = "scrollTo="+subContent;
            if (index>0){
                baseUrl = baseUrl.split("\\?")[0];
            }else {
                baseUrl = baseUrl+"?"+subContent;
            }
        }*/
        baseUrl = baseUrl.split("\\?")[0];
        final  String url = getString(R.string.share_url_text)+baseUrl;
        miniProgramUrlEt.setText(url);
    }

    private void shareWeixin(String content,String title){
        generateUrl();
        String url;
        String appDownloadUrl;
        if(miniProgramAppUrlEt.isChecked())
        { 
            appDownloadUrl = "\n"+"大经科注app下载：http://www.time24.cn/html/download.html";
        }else {
            appDownloadUrl ="";
        }
        url = miniProgramUrlEt.getText().toString();
        cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // 创建普通字符型ClipData
        content= content==null?"":content+"\n";
        content= content.equals("\n")?"":content;
        String copyText = content+
                title+"\n"+url+appDownloadUrl;
        mClipData = ClipData.newPlainText("Label",
                copyText);
        // 将ClipData内容放到系统剪贴板里。
        cm.setPrimaryClip(mClipData);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, copyText);
        startActivity(Intent.createChooser(intent, getString(R.string.share)));
    }

    private void gotoWXApp(){
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName cmp = new ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(cmp);
        startActivity(intent);
    }
}

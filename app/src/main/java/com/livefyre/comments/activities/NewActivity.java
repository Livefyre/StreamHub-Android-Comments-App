package com.livefyre.comments.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.filepicker.sdk.FilePicker;
import com.filepicker.sdk.FilePickerAPI;
import com.livefyre.comments.BaseActivity;
import com.livefyre.comments.LFSAppConstants;
import com.livefyre.comments.LFSConfig;
import com.livefyre.comments.LFUtils;
import com.livefyre.comments.R;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.HashMap;

import livefyre.streamhub.LFSActions;
import livefyre.streamhub.LFSConstants;
import livefyre.streamhub.WriteClient;

public class NewActivity extends BaseActivity {
    public static final String TAG = NewActivity.class.getSimpleName();


    Toolbar toolbar;
    TextView commentEt;
    LinearLayout attachImageLL;
    FrameLayout attacheImageFL;
    ImageView capturedImage;
    ProgressBar progressBar;
    RelativeLayout deleteCapturedImage;
    JSONObject imgObj;
    //id-selected comment Id Used for editing and new reply
    String imgUrl, purpose, id, body;
    boolean isEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_activity);
        pullViews();

        getDataFromIntent();

        setListenersToViews();

        setListenersToViewsAndSetConfig();
    }

    private void getDataFromIntent() {
        Intent intent = getIntent();
        purpose = intent.getStringExtra(LFSAppConstants.PURPOSE);

        if (purpose.equals(LFSAppConstants.NEW_COMMENT)) {

        } else if (purpose.equals(LFSAppConstants.NEW_REPLY)) {
            id = intent.getStringExtra(LFSAppConstants.ID);
        } else if (purpose.equals(LFSAppConstants.EDIT)) {
            id = intent.getStringExtra(LFSAppConstants.ID);
            body = intent.getStringExtra(LFSAppConstants.BODY);
            commentEt.setText(LFUtils.trimTrailingWhitespace(Html
                            .fromHtml(body)),
                    TextView.BufferType.SPANNABLE);
        }
    }

    private void pullViews() {
        attachImageLL = (LinearLayout) findViewById(R.id.attachImageLL);
        attacheImageFL = (FrameLayout) findViewById(R.id.attacheImageFL);
        capturedImage = (ImageView) findViewById(R.id.capturedImage);
        deleteCapturedImage = (RelativeLayout) findViewById(R.id.deleteCapturedImage);
        commentEt = (TextView) findViewById(R.id.commentEt);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
    }

    private void setListenersToViews() {
        attachImageLL.setOnClickListener(attachImageLLListener);
        deleteCapturedImage.setOnClickListener(deleteCapturedImageListener);
    }


    private void setListenersToViewsAndSetConfig() {
        toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayShowTitleEnabled(false);

        //Activity Name
        TextView activityName = (TextView) findViewById(R.id.activityTitle);

        if (purpose.equals(LFSAppConstants.NEW_COMMENT)) {
            commentEt.setHint("Write your comment here...");//setting hint to Edittext
            activityName.setText("New Comment");
        } else if (purpose.equals(LFSAppConstants.NEW_REPLY)) {
            commentEt.setHint("Write your Reply here...");//setting hint to Edittext
            activityName.setText("Reply");
            attachImageLL.setVisibility(View.VISIBLE);//Hide Image Selection option
        } else if (purpose.equals(LFSAppConstants.EDIT)) {
            activityName.setText("Edit");
            attachImageLL.setVisibility(View.GONE);//Hide Image Selection option
        }

        //Activity Icon
        ImageView homeIcon = (ImageView) findViewById(R.id.activityIcon);
        homeIcon.setBackgroundResource(R.drawable.close_b);

        LinearLayout activityIconLL = (LinearLayout) findViewById(R.id.activityIconLL);
        activityIconLL.setOnClickListener(homeIconListener);

        //Action
        TextView actionTv = (TextView) findViewById(R.id.actionTv);
        actionTv.setText("POST");

        LinearLayout actionLL = (LinearLayout) findViewById(R.id.actionLL);
        actionLL.setVisibility(View.VISIBLE);
        actionLL.setOnClickListener(actionTvListener);

    }


    void postNewComment(String body) {

        showProgressDialog();
        HashMap<String, Object> perameters = new HashMap<>();

        perameters.put(LFSConstants.LFSPostBodyKey, body);
        perameters.put(LFSConstants.LFSPostType,
                LFSConstants.LFSPostTypeComment);
        perameters.put(LFSConstants.LFSPostUserTokenKey, LFSConfig.USER_TOKEN);
        if (imgObj != null)
            perameters.put(LFSConstants.LFSPostAttachmentsKey,
                    (new JSONArray().put(imgObj)).toString());
        try {
            WriteClient.postContent(
                    LFSConfig.COLLECTION_ID, null, LFSConfig.USER_TOKEN,
                    perameters, new writeclientCallback());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    void postNewReply(String body) {
        showProgressDialog();

        if (purpose.equals(LFSAppConstants.NEW_REPLY)) {
            Log.d("REPLY", "IN NEW REPLY");
            HashMap<String, Object> perameters = new HashMap<>();
            perameters.put(LFSConstants.LFSPostBodyKey, body);
            perameters.put(LFSConstants.LFSPostType,
                    LFSConstants.LFSPostTypeReply);
            perameters.put(LFSConstants.LFSPostUserTokenKey,
                    LFSConfig.USER_TOKEN);
            if (imgObj != null)
                perameters.put(LFSConstants.LFSPostAttachmentsKey,
                        (new JSONArray().put(imgObj)).toString());
            try {
                WriteClient.postContent(
                        LFSConfig.COLLECTION_ID, id, LFSConfig.USER_TOKEN,
                        perameters, new newReplyCallback());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        } else if (purpose.equals(LFSAppConstants.EDIT)) {
            Log.d("EDIT", "IN EDIT REPLY");

            RequestParams perameters = new RequestParams();
            perameters.put(LFSConstants.LFSPostBodyKey, body);
            perameters.put(LFSConstants.LFSPostUserTokenKey,
                    LFSConfig.USER_TOKEN);
            WriteClient.postAction(LFSConfig.COLLECTION_ID, id,
                    LFSConfig.USER_TOKEN, LFSActions.EDIT, perameters,
                    new editCallback());

        }
    }

    //Call backs

    //New Comment
    public class writeclientCallback extends JsonHttpResponseHandler {

        public void onSuccess(JSONObject data) {
            dismissProgressDialog();
            showAlert("Comment Posted Successfully.", "OK", null);
        }

        @Override
        public void onFailure(Throwable error, String content) {
            super.onFailure(error, content);
            dismissProgressDialog();
            Log.d("data error", "" + content);

            try {
                JSONObject errorJson = new JSONObject(content);
                if (!errorJson.isNull("msg")) {

                    showAlert(errorJson.getString("msg"), "TRY AGAIN", tryAgain);
                } else {
                    showAlert("Something went wrong.", "TRY AGAIN", tryAgain);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                showAlert("Something went wrong.", "TRY AGAIN", tryAgain);

            }

        }
    }

    //New Reply
    public class newReplyCallback extends JsonHttpResponseHandler {

        public void onSuccess(JSONObject data) {
            dismissProgressDialog();
            showAlert("Reply Posted Successfully.", "OK", null);
            Intent returnIntent = new Intent();
            returnIntent.putExtra("result", 1);
            setResult(RESULT_OK, returnIntent);
        }

        @Override
        public void onFailure(Throwable error, String content) {
            super.onFailure(error, content);
            dismissProgressDialog();
            try {
                JSONObject errorJson = new JSONObject(content);
                if (!errorJson.isNull("msg")) {
                    showAlert(errorJson.getString("msg"), "TRY AGAIN", tryAgain);

                } else {
                    showAlert("Something went wrong.", "TRY AGAIN", tryAgain);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                showAlert("Something went wrong.", "TRY AGAIN", tryAgain);

            }
        }

    }

    // Edit Comment
    private class editCallback extends JsonHttpResponseHandler {

        public void onSuccess(JSONObject data) {
            dismissProgressDialog();
            showAlert("Reply Edited Successfully.", "OK", null);
        }

        @Override
        public void onFailure(Throwable error, String content) {
            super.onFailure(error, content);
            dismissProgressDialog();
            try {
                JSONObject errorJson = new JSONObject(content);
                if (!errorJson.isNull("msg")) {
                    showAlert(errorJson.getString("msg"), "TRY AGAIN", tryAgain);

                } else {
                    showAlert("Something went wrong.", "TRY AGAIN", tryAgain);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                showAlert("Something went wrong.", "TRY AGAIN", tryAgain);
            }
        }

    }

    // Listeners
    View.OnClickListener actionTvListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (!isNetworkAvailable()) {
                showAlert("No connection available", "TRY AGAIN", tryAgain);
                return;
            }

            String description = commentEt.getText().toString();

            if (description.length() == 0) {
                showAlert("Please enter text to post.", "TRY AGAIN", tryAgain);
                return;
            }

            if (purpose.equals(LFSAppConstants.NEW_COMMENT)) {

                String descriptionHTML = Html.toHtml((android.text.Spanned) commentEt.getText());
                postNewComment(descriptionHTML);
            } else {
                String htmlReplyText = Html.toHtml((android.text.Spanned) commentEt.getText());
                postNewReply(htmlReplyText);
            }

        }
    };

    View.OnClickListener homeIconListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    View.OnClickListener deleteCapturedImageListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            attachImageLL.setVisibility(View.VISIBLE);
            attacheImageFL.setVisibility(View.GONE);
        }
    };

    View.OnClickListener attachImageLLListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if(LFSConfig.FILEPICKER_API_KEY.length()==0){
                showToast("Something went wrong.");
            }else{
                Intent intent = new Intent(NewActivity.this, FilePicker.class);
                FilePickerAPI.setKey(LFSConfig.FILEPICKER_API_KEY);
                startActivityForResult(intent, FilePickerAPI.REQUEST_CODE_GETFILE);
            }
        }
    };

    // Dialog Listeners
    DialogInterface.OnClickListener selectImageDialogAction = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface arg0, int arg1) {
            Intent intent = new Intent(NewActivity.this, FilePicker.class);
            FilePickerAPI.setKey(LFSConfig.FILEPICKER_API_KEY);
            startActivityForResult(intent, FilePickerAPI.REQUEST_CODE_GETFILE);
        }
    };

    DialogInterface.OnClickListener tryAgain = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface arg0, int arg1) {

        }
    };

    //On Image Selected
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FilePickerAPI.REQUEST_CODE_GETFILE) {
            if (resultCode != RESULT_OK) {
                // Result was cancelled by the user or there was an error
                showAlert("No Image Selected.", "SELECT IMAGE", selectImageDialogAction);
                attachImageLL.setVisibility(View.VISIBLE);
                attacheImageFL.setVisibility(View.GONE);
                return;
            }
            attachImageLL.setVisibility(View.GONE);
            attacheImageFL.setVisibility(View.VISIBLE);

            String imgUrl = data.getExtras().getString("fpurl");
            application.printLog(true, TAG + "Uploaded Image URL", imgUrl + " ");
            try {
                imgObj = new JSONObject();
                imgObj.put("link", imgUrl);
                imgObj.put("provider_name", "LivefyreFilePicker");
                imgObj.put("thumbnail_url", imgUrl);
                imgObj.put("type", "photo");
                imgObj.put("url", imgUrl);
                try {
                    progressBar.setVisibility(View.VISIBLE);
                    Picasso.with(getBaseContext()).load(imgUrl).fit().into(capturedImage, new ImageLoadCallBack());
                } catch (Exception e) {
e.printStackTrace();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private class ImageLoadCallBack implements Callback {

        @Override
        public void onSuccess() {
            //Hide
            progressBar.setVisibility(View.GONE);

        }

        @Override
        public void onError() {
            //Hide
        }
    }
}

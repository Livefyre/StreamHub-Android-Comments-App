package com.livefyre.comments.activities;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.livefyre.comments.BaseActivity;
import com.livefyre.comments.LFSAppConstants;
import com.livefyre.comments.LFSConfig;
import com.livefyre.comments.R;
import com.livefyre.comments.adapter.CommentsAdapter;
import com.livefyre.comments.listeners.ContentUpdateListener;
import com.livefyre.comments.models.Content;
import com.livefyre.comments.parsers.ContentParser;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.squareup.otto.Bus;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;

import livefyre.streamhub.AdminClient;
import livefyre.streamhub.BootstrapClient;
import livefyre.streamhub.StreamClient;

import static android.support.v7.widget.RecyclerView.OnClickListener;
import static android.support.v7.widget.RecyclerView.OnItemTouchListener;
import static android.support.v7.widget.RecyclerView.OnScrollListener;

public class CommentsActivity extends BaseActivity implements ContentUpdateListener {
    public static final String TAG = CommentsActivity.class.getSimpleName();


    Toolbar toolbar;

    TextView activityTitle, notifMsgTV;

    RecyclerView commentsLV;
    CommentsAdapter mCommentsAdapter;
    ImageButton postNewCommentIv;
    ArrayList<Content> commentsArray;
    ContentParser content;
    private SwipeRefreshLayout swipeView;
    LinearLayout notification;
    Bus mBus = application.getBus();
    private String adminClintId = "No";
    private static final int DELETED = -1;
    private static final int PARENT = 0;
    private static final int CHILD = 1;
    ArrayList<String> newComments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.comments_activity);

        pullViews();

        setListenersToViews();

        buildToolBar();

        adminClintCall();
    }

    private void setListenersToViews() {
        postNewCommentIv.setOnClickListener(postNewCommentListener);
        commentsLV.addOnItemTouchListener(new RecyclerTouchListener(getApplicationContext(), commentsLV, new ClickListener() {
            @Override
            public void onClick(View view, int position) {
                int viewType = commentsArray.get(position).getContentType().getValue();
                switch (viewType) {
                    case PARENT:
                    case CHILD:
                        Intent detailViewIntent = new Intent(CommentsActivity.this, CommentActivity.class);
                        detailViewIntent.putExtra(LFSAppConstants.ID, commentsArray.get(position).getId());
                        startActivity(detailViewIntent);
                        break;
                    case DELETED:
                        break;
                }
            }

            @Override
            public void onLongClick(View view, int position) {
            }
        }));
        swipeView.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
        swipeView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeView.setRefreshing(true);
                mCommentsAdapter = null;
                commentsArray.clear();
                mCommentsAdapter = new CommentsAdapter(getApplication(), commentsArray);
                commentsLV.setAdapter(mCommentsAdapter);
                bootstrapClientCall();

                YoYo.with(Techniques.FadeIn)
                        .duration(700)
                        .playOn(findViewById(R.id.commentsLV));
            }
        });
        commentsLV.setOnScrollListener(onScrollListener);
        notification.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                YoYo.with(Techniques.BounceInUp)
                        .duration(700)
                        .playOn(findViewById(R.id.notification));
                notification.setVisibility(View.GONE);
                for (int m = 0; m < newComments.size(); m++) {
                    int flag = 0;
                    String stateBeanId = newComments.get(m);
                    Content stateBean = ContentParser.ContentMap.get(stateBeanId);
                    for (int i = 0; i < commentsArray.size(); i++) {
                        Content content = commentsArray.get(i);
                        if (content.getId().equals(stateBean.getParentId())) {
                            commentsArray.add(i + 1, stateBean);
                            mCommentsAdapter.notifyItemInserted(i + 1);
                            flag = 1;

                            break;
                        }
                    }
                    if (flag == 0) {
                        commentsArray.add(0, stateBean);
                        mCommentsAdapter.notifyItemInserted(0);
                    } else {
                    }
                    scrollToComment(stateBeanId);
                }
                newComments.clear();
            }
        });
    }

    private void scrollToComment(String mCommentBeanId) {
        for (int i = 0; i < commentsArray.size(); i++) {
            Content mBean = commentsArray.get(i);
            if (mBean.getId().equals(mCommentBeanId)) {
                commentsLV.smoothScrollToPosition(i);
                break;
            }
        }
    }

    private void buildToolBar() {

        toolbar = (Toolbar) findViewById(R.id.app_bar);
        //toolbar
        setSupportActionBar(toolbar);
        //disable title on toolbar
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        ImageView homeIcon = (ImageView) findViewById(R.id.activityIcon);
        homeIcon.setBackgroundResource(R.drawable.flame);

        activityTitle = (TextView) findViewById(R.id.activityTitle);
        activityTitle.setText("Comments");
        activityTitle.setOnClickListener(activityTitleListenerHide);

    }

    private void pullViews() {

        commentsLV = (RecyclerView) findViewById(R.id.commentsLV);
        commentsLV.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        postNewCommentIv = (ImageButton) findViewById(R.id.postNewCommentIv);
        notifMsgTV = (TextView) findViewById(R.id.notifMsgTV);
        notification = (LinearLayout) findViewById(R.id.notification);
        swipeView = (SwipeRefreshLayout) findViewById(R.id.swipe);

    }

    void adminClintCall() {
        if (!isNetworkAvailable()) {
            showAlert("No connection available", "TRY AGAIN", tryAgain);
            return;
        } else {
            showProgressDialog();
        }
        try {
            AdminClient.authenticateUser(LFSConfig.USER_TOKEN,
                    LFSConfig.COLLECTION_ID, LFSConfig.ARTICLE_ID,
                    LFSConfig.SITE_ID,
                    new AdminCallback());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }

    public class AdminCallback extends JsonHttpResponseHandler {

        public void onSuccess(JSONObject AdminClintJsonResponseObject) {
            JSONObject data;
            application.printLog(true, TAG + "-AdminCallback-onSuccess", AdminClintJsonResponseObject.toString());
            try {
                data = AdminClintJsonResponseObject.getJSONObject("data");

                if (!data.isNull("permissions")) {
                    JSONObject permissions = data.getJSONObject("permissions");
                    if (!permissions.isNull("moderator_key"))
                        application.putDataInSharedPref(
                                LFSAppConstants.ISMOD, "yes");
                    else {
                        application.putDataInSharedPref(
                                LFSAppConstants.ISMOD, "no");
                    }
                } else {
                    application.putDataInSharedPref(
                            LFSAppConstants.ISMOD, "no");
                }

                if (!data.isNull("profile")) {
                    JSONObject profile = data.getJSONObject("profile");

                    if (!profile.isNull("id")) {
                        application.putDataInSharedPref(
                                LFSAppConstants.ID, profile.getString("id"));
                        adminClintId = profile.getString("id");
                    }
                }

            } catch (JSONException e1) {
                e1.printStackTrace();
            }

            bootstrapClientCall();
        }

        @Override
        public void onFailure(Throwable error, String content) {
            super.onFailure(error, content);
            // Log.d("adminClintCall", "Fail");
            application.printLog(true, TAG + "-AdminCallback-onFailure", error.toString());

            bootstrapClientCall();
        }

    }

    void bootstrapClientCall() {
        try {
            BootstrapClient.getInit(LFSConfig.SITE_ID,
                    LFSConfig.ARTICLE_ID, new InitCallback());

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void loadImage(String imageURL) {
        if (imageURL.length() > 0)
            Picasso.with(getBaseContext()).load(imageURL);
    }

    Boolean isExistComment(String commentId) {
        for (Content bean : commentsArray) {
            if (bean.getId().equals(commentId))
                return true;
        }
        return false;
    }

    private class InitCallback extends JsonHttpResponseHandler {

        public void onSuccess(String data) {
            application.printLog(false, TAG + "-InitCallback-onSuccess", data.toString());

            buildCommentList(data);
            swipeView.setRefreshing(false);
        }

        @Override
        public void onFailure(Throwable error, String content) {
            super.onFailure(error, content);
            application.printLog(true, TAG + "-InitCallback-onFailure", error.toString());
        }
    }

    void buildCommentList(String data) {
        try {
            content = new ContentParser(new JSONObject(data), getBaseContext());
            content.getContentFromResponse(this);
            commentsArray = content.getDeletedObjects();
            mCommentsAdapter = new CommentsAdapter(this, commentsArray);
            commentsLV.setAdapter(mCommentsAdapter);
            streamClintCall();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        newComments = new ArrayList<>();
        swipeView.setEnabled(true);
        dismissProgressDialog();
    }

    void streamClintCall() {
        try {
            StreamClient.pollStreamEndpoint(
                    LFSConfig.COLLECTION_ID, ContentParser.lastEvent,
                    new StreamCallBack());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public class StreamCallBack extends AsyncHttpResponseHandler {

        public void onSuccess(String data) {
            if (data != null) {
                content.setStreamData(data);
            }
        }

        @Override
        public void onFailure(Throwable error, String content) {
            super.onFailure(error, content);
        }
    }

    DialogInterface.OnClickListener tryAgain = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface arg0, int arg1) {
            adminClintCall();
        }
    };

    OnClickListener postNewCommentListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            YoYo.with(Techniques.ZoomIn)
                    .duration(700)
                    .playOn(findViewById(R.id.notification));
            Intent intent = new Intent(CommentsActivity.this, NewActivity.class);
            intent.putExtra(LFSAppConstants.PURPOSE, LFSAppConstants.NEW_COMMENT);
            startActivity(intent);
        }
    };


    static class RecyclerTouchListener implements OnItemTouchListener {

        private GestureDetector gestureDetector;
        private ClickListener clickListener;

        public RecyclerTouchListener(Context context, final RecyclerView recyclerView, final ClickListener clickListener) {
            this.clickListener = clickListener;
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (child != null && clickListener != null) {
                        clickListener.onLongClick(child, recyclerView.getChildPosition(child));
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {

            View child = rv.findChildViewUnder(e.getX(), e.getY());
            if (child != null && clickListener != null && gestureDetector.onTouchEvent(e)) {
                clickListener.onClick(child, rv.getChildPosition(child));
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        }
    }

    public static interface ClickListener {
        public void onClick(View view, int position);

        public void onLongClick(View view, int position);
    }

    public OnScrollListener onScrollListener = new OnScrollListener() {
        boolean hideToolBar = false;

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (hideToolBar) {
                postNewCommentIv.setVisibility(View.GONE);
                getSupportActionBar().hide();
            } else {
                postNewCommentIv.setVisibility(View.VISIBLE);
                getSupportActionBar().show();
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (dy > 2) {
                hideToolBar = true;
            } else if (dy < -1) {
                hideToolBar = false;

            }
        }
    };

    OnClickListener activityTitleListenerHide = new OnClickListener() {
        @Override
        public void onClick(View v) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                activityTitle.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                activityTitle.setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
            }

            postNewCommentIv.setVisibility(View.GONE);


            activityTitle.setOnClickListener(activityTitleListenerShow);

        }
    };
    OnClickListener activityTitleListenerShow = new OnClickListener() {
        @Override
        public void onClick(View v) {

            postNewCommentIv.setVisibility(View.VISIBLE);

            activityTitle.setOnClickListener(activityTitleListenerHide);

        }
    };

    //    @Override
    public void onDataUpdate(HashSet<String> authorsSet, HashSet<String> statesSet, HashSet<String> annotationsSet, HashSet<String> updates) {
        application.printLog(true, TAG, "" + statesSet);
        for (String stateBeanId : statesSet) {
            Content stateBean = ContentParser.ContentMap.get(stateBeanId);
            if (stateBean.getVisibility().equals("1")) {

                if (isExistComment(stateBeanId)) continue;

                if (adminClintId.equals(stateBean.getAuthorId())) {
                    int flag = 0;
                    for (int i = 0; i < commentsArray.size(); i++) {
                        Content content = commentsArray.get(i);
                        if (content.getId().equals(stateBean.getParentId())) {
                            commentsArray.add(i + 1, stateBean);
                            mCommentsAdapter.notifyItemInserted(i + 1);
                            flag = 1;
                            break;
                        }
                    }
                    if (flag == 0) {
                        commentsArray.add(0, stateBean);
                        mCommentsAdapter.notifyItemInserted(0);
                    }
                } else {
                    newComments.add(0, stateBeanId);
                }
            } else {
                if (!content.hasVisibleChildContents(stateBeanId)) {
                    application.printLog(true, TAG, "Deleted Content");

                    for (int i = 0; i < commentsArray.size(); i++) {
                        Content bean = commentsArray.get(i);
                        if (bean.getId().equals(stateBeanId)) {
                            commentsArray.remove(i);
                            mCommentsAdapter.notifyItemRemoved(i);
                            break;
                        }
                    }
                }
            }
        }
        if (updates.size() > 0) {
            mBus.post(updates);
            mCommentsAdapter.notifyDataSetChanged();
        }

        if (newComments != null)
            if (newComments.size() > 0) {
                if (newComments.size() == 1) {
                    notifMsgTV.setText(newComments.size() + " New Comment");

                } else {
                    notifMsgTV.setText(newComments.size() + " New Comments");
                }
                notification.setVisibility(View.VISIBLE);
                YoYo.with(Techniques.DropOut)
                        .duration(700)
                        .playOn(findViewById(R.id.notification));

            } else {
                notification.setVisibility(View.GONE);
            }
    }

}

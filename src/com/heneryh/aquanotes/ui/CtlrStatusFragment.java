/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.heneryh.aquanotes.ui;

import com.heneryh.aquanotes.R;
import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.ui.widget.BlockView;
import com.heneryh.aquanotes.ui.widget.ControllersLayout;
import com.heneryh.aquanotes.ui.widget.ObservableScrollView;
import com.heneryh.aquanotes.ui.widget.Workspace;
import com.heneryh.aquanotes.util.AnalyticsUtils;
import com.heneryh.aquanotes.util.Maps;
import com.heneryh.aquanotes.util.MotionEventUtils;
import com.heneryh.aquanotes.util.NotifyingAsyncQueryHandler;
import com.heneryh.aquanotes.util.ParserUtils;
import com.heneryh.aquanotes.util.UIUtils;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

/**
 * Shows a horizontally-pageable calendar of conference days. Horizontaly paging is achieved using
 * {@link Workspace}, and the primary UI classes for rendering the calendar are
 * {@link com.heneryh.aquanotes.ui.widget.TimeRulerView},
 * {@link ControllersLayout}, and {@link BlockView}.
 */
public class CtlrStatusFragment extends Fragment implements
        NotifyingAsyncQueryHandler.AsyncQueryListener,
        ObservableScrollView.OnScrollListener,
        View.OnClickListener {
	
	LayoutInflater  mInflater;

    private static final String TAG = "CtlrStatusFragment";


    private static final HashMap<String, Integer> sTypeColumnMap = buildTypeColumnMap();

    // TODO: show blocks that don't fall into columns at the bottom

    public static final String EXTRA_TIME_START = "com.google.android.iosched.extra.TIME_START";
    public static final String EXTRA_TIME_END = "com.google.android.iosched.extra.TIME_END";

    private NotifyingAsyncQueryHandler mHandler;

    private Workspace mWorkspace;
    private TextView mTitle;
    private int mTitleCurrentCtlrIndex = -1;
    private View mLeftIndicator;
    private View mRightIndicator;

    /**
     * A helper class containing object references related to a particular day in the schedule.
     */
    private class Ctlr {
        private ViewGroup rootView;
        private ObservableScrollView scrollView;
//        private View nowView;
        private ControllersLayout controllersView;

        private int index = -1;
        private String label = null;
        private Uri probesUri = null;
    }

    private List<Ctlr> mCtlrs = new ArrayList<Ctlr>();

    private static HashMap<String, Integer> buildTypeColumnMap() {
        final HashMap<String, Integer> map = Maps.newHashMap();
        map.put(ParserUtils.BLOCK_TYPE_FOOD, 0);
        map.put(ParserUtils.BLOCK_TYPE_SESSION, 1);
        map.put(ParserUtils.BLOCK_TYPE_OFFICE_HOURS, 2);
        return map;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new NotifyingAsyncQueryHandler(getActivity().getContentResolver(), this);
        setHasOptionsMenu(true);
        AnalyticsUtils.getInstance(getActivity()).trackPageView("/Schedule");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	
    	/**
    	 * The ctlr_status fragment is a left/right scroll button a title and a workspace below it for content
    	 */
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.fragment_ctlr_status, null);

        mLeftIndicator = root.findViewById(R.id.indicator_left);
        mTitle = (TextView) root.findViewById(R.id.controller_title);
        mRightIndicator = root.findViewById(R.id.indicator_right);
        mWorkspace = (Workspace) root.findViewById(R.id.workspace);

        /**
         * Add click listeners for the scroll buttons.
         */
        mLeftIndicator.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() & MotionEventUtils.ACTION_MASK)
                        == MotionEvent.ACTION_DOWN) {
                    mWorkspace.scrollLeft();
                    return true;
                }
                return false;
            }
        });
        mLeftIndicator.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mWorkspace.scrollLeft();
            }
        });

        mRightIndicator.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() & MotionEventUtils.ACTION_MASK)
                        == MotionEvent.ACTION_DOWN) {
                    mWorkspace.scrollRight();
                    return true;
                }
                return false;
            }
        });
        mRightIndicator.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mWorkspace.scrollRight();
            }
        });


		
		mInflater = inflater;
//        int ctlrId = 999;
//        setupCtlr(inflater, 999);
////        setupDay(inflater, WED_START);
//
//        updateWorkspaceHeader(0);
//        
//        /**
//         * Set the scroll listener for the workspace
//         */
//        mWorkspace.setOnScrollListener(new Workspace.OnScrollListener() {
//            public void onScroll(float screenFraction) {
//                updateWorkspaceHeader(Math.round(screenFraction));
//            }
//        }, true);

        return root;
    }

    public void updateWorkspaceHeader(int ctlrIndex) {
        if (mTitleCurrentCtlrIndex == ctlrIndex) {
            return;
        }

        mTitleCurrentCtlrIndex = ctlrIndex;
        Ctlr ctlr = mCtlrs.get(ctlrIndex);
        mTitle.setText(ctlr.label);

        mLeftIndicator
                .setVisibility((ctlrIndex != 0) ? View.VISIBLE : View.INVISIBLE);
        mRightIndicator
                .setVisibility((ctlrIndex < mCtlrs.size() - 1) ? View.VISIBLE : View.INVISIBLE);
    }

    private void setupCtlr(LayoutInflater inflater, int controllerId, String title) {

    	Ctlr ctlr = new Ctlr();

        // Setup data
    	ctlr.index = mCtlrs.size();
    	
    	ctlr.probesUri = AquaNotesDbContract.Probes.buildQueryProbesUri(controllerId);

        // Setup views
    	ctlr.rootView = (ViewGroup) inflater.inflate(R.layout.controllers_content, null);

    	ctlr.scrollView = (ObservableScrollView) ctlr.rootView.findViewById(R.id.controllers_scroll); // tagged in controllers_content.xml
    	ctlr.scrollView.setOnScrollListener(this);

    	ctlr.controllersView = (ControllersLayout) ctlr.rootView.findViewById(R.id.controllers);
//    	ctlr.nowView = ctlr.rootView.findViewById(R.id.blocks_now);
//
//    	ctlr.blocksView.setDrawingCacheEnabled(true);
//    	ctlr.blocksView.setAlwaysDrawnWithCacheEnabled(true);
//
//        TimeZone.setDefault(UIUtils.CONFERENCE_TIME_ZONE);
        ctlr.label = title;

        mWorkspace.addView(ctlr.rootView);
        mCtlrs.add(ctlr);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Since we build our views manually instead of using an adapter, we
        // need to manually requery every time launched.
        requery();

//        getActivity().getContentResolver().registerContentObserver(
//                AquaNotesDbContract.Sessions.CONTENT_URI, true, mSessionChangesObserver);
//
//        // Start listening for time updates to adjust "now" bar. TIME_TICK is
//        // triggered once per minute, which is how we move the bar over time.
//        final IntentFilter filter = new IntentFilter();
//        filter.addAction(Intent.ACTION_TIME_TICK);
//        filter.addAction(Intent.ACTION_TIME_CHANGED);
//        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
//        getActivity().registerReceiver(mReceiver, filter, null, new Handler());
    }

    private void requery() {
//        for (Ctlr day : mCtlrs) {
            mHandler.startQuery(ControllersQuery._TOKEN, 0, Controllers.buildQueryControllersUri(), ControllersQuery.PROJECTION,
                    null, null, AquaNotesDbContract.Controllers.DEFAULT_SORT);
//        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
//                updateNowView(true);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
//        getActivity().unregisterReceiver(mReceiver);
//        getActivity().getContentResolver().unregisterContentObserver(mSessionChangesObserver);
    }

    /**
     * {@inheritDoc}
     */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (getActivity() == null) {
            return;
        }

        /** TODO: add token checker */
        try {
        	while (cursor.moveToNext()) {
        		final Integer controllerId = cursor.getInt(ControllersQuery._ID);
        		final String controllerTitle = cursor.getString(ControllersQuery.TITLE);
        		setupCtlr(mInflater, controllerId, controllerTitle);
        	}
        } finally {
        	cursor.close();
        }

        updateWorkspaceHeader(0);

        /**
         * Set the scroll listener for the workspace
         */
        mWorkspace.setOnScrollListener(new Workspace.OnScrollListener() {
        	public void onScroll(float screenFraction) {
        		updateWorkspaceHeader(Math.round(screenFraction));
        	}
        }, true);

//        Ctlr ctlr = (Ctlr) cookie;
//
//        // Clear out any existing sessions before inserting again
//        ctlr.blocksView.removeAllBlocks();
//
//        try {
//            while (cursor.moveToNext()) {
//                final String type = cursor.getString(BlocksQuery.BLOCK_TYPE);
//                final Integer column = sTypeColumnMap.get(type);
//                // TODO: place random blocks at bottom of entire layout
//                if (column == null) {
//                    continue;
//                }
//
//                final String blockId = cursor.getString(BlocksQuery.BLOCK_ID);
//                final String title = cursor.getString(BlocksQuery.BLOCK_TITLE);
//                final long start = cursor.getLong(BlocksQuery.BLOCK_START);
//                final long end = cursor.getLong(BlocksQuery.BLOCK_END);
//                final boolean containsStarred = cursor.getInt(BlocksQuery.CONTAINS_STARRED) != 0;
//
//                final BlockView blockView = new BlockView(getActivity(), blockId, title, start, end,
//                        containsStarred, column);
//
//                final int sessionsCount = cursor.getInt(BlocksQuery.SESSIONS_COUNT);
//                if (sessionsCount > 0) {
//                    blockView.setOnClickListener(this);
//                } else {
//                    blockView.setFocusable(false);
//                    blockView.setEnabled(false);
//                    LayerDrawable buttonDrawable = (LayerDrawable) blockView.getBackground();
//                    buttonDrawable.getDrawable(0).setAlpha(DISABLED_BLOCK_ALPHA);
//                    buttonDrawable.getDrawable(2).setAlpha(DISABLED_BLOCK_ALPHA);
//                }
//
//                ctlr.blocksView.addBlock(blockView);
//            }
//        } finally {
//            cursor.close();
//        }
    }

    /** {@inheritDoc} */
    public void onClick(View view) {
        if (view instanceof BlockView) {
//            String title = ((BlockView)view).getText().toString();
//            AnalyticsUtils.getInstance(getActivity()).trackEvent(
//                    "Schedule", "Session Click", title, 0);
//            final String blockId = ((BlockView) view).getBlockId();
//            final Uri sessionsUri = AquaNotesDbContract.Blocks.buildSessionsUri(blockId);
//
//            final Intent intent = new Intent(Intent.ACTION_VIEW, sessionsUri);
//            intent.putExtra(DbMaintProbesFragment.EXTRA_SCHEDULE_TIME_STRING,
//                    ((BlockView) view).getBlockTimeString());
//            ((BaseActivity) getActivity()).openActivityOrFragment(intent);
        }
    }

 
    public void onScrollChanged(ObservableScrollView view) {
        // Keep each day view at the same vertical scroll offset.
        final int scrollY = view.getScrollY();
        for (Ctlr ctlr : mCtlrs) {
            if (ctlr.scrollView != view) {
                ctlr.scrollView.scrollTo(0, scrollY);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.schedule_menu_items, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_now) {
//            if (!updateNowView(true)) {
//                Toast.makeText(getActivity(), R.string.toast_now_not_visible,
//                        Toast.LENGTH_SHORT).show();
//            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private ContentObserver mSessionChangesObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            requery();
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive time update");
//            updateNowView(false);
        }
    };

    private interface ControllersQuery {

        int _TOKEN = 0x1;
        
        String[] PROJECTION = {
//              String CONTROLLER_ID = "_id";
//              String TITLE = "title";
//              String WAN_URL = "wan_url";
//              String LAN_URL = "wifi_url";
//              String WIFI_SSID = "wifi_ssid";
//              String USER = "user";
//              String PW = "pw";
//              String LAST_UPDATED = "last_updated";
//              String UPDATE_INTERVAL = "update_i";
//              String DB_SAVE_DAYS = "db_save_days";
//              String CONTROLLER_TYPE = "controller_type";
                BaseColumns._ID,
                AquaNotesDbContract.Controllers.TITLE,
                AquaNotesDbContract.Controllers.WAN_URL,
                AquaNotesDbContract.Controllers.LAN_URL,
                AquaNotesDbContract.Controllers.WIFI_SSID,
                AquaNotesDbContract.Controllers.USER,
                AquaNotesDbContract.Controllers.PW,
                AquaNotesDbContract.Controllers.LAST_UPDATED,
                AquaNotesDbContract.Controllers.UPDATE_INTERVAL,
                AquaNotesDbContract.Controllers.DB_SAVE_DAYS,
                AquaNotesDbContract.Controllers.MODEL,
        };
        
        int _ID = 0;
        int TITLE = 1;
        int WAN_URL = 2;
        int LAN_URL = 3;
        int WIFI_SSID = 4;
        int USER = 5;
        int PW = 6;
        int LAST_UPDATED = 7;
        int UPDATE_INTERVAL = 8;
        int DB_SAVE_DAYS = 9;
        int MODEL = 10;
    }
}

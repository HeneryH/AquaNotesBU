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

import java.sql.Date;
import java.text.SimpleDateFormat;

import com.heneryh.aquanotes.R;
import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Probes;
import com.heneryh.aquanotes.util.ActivityHelper;
import com.heneryh.aquanotes.util.AnalyticsUtils;
import com.heneryh.aquanotes.util.NotifyingAsyncQueryHandler;
import com.heneryh.aquanotes.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.support.v4.app.ListFragment;
import android.text.Spannable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import static com.heneryh.aquanotes.util.UIUtils.buildStyledSnippet;
import static com.heneryh.aquanotes.util.UIUtils.formatSessionSubtitle;

/**
 * A {@link ListFragment} showing a list of sessions.
 */
public class DbMaintDataFragment extends ListFragment implements
        NotifyingAsyncQueryHandler.AsyncQueryListener {

    int mNum;

        public static final String EXTRA_SCHEDULE_TIME_STRING =
            "com.google.android.iosched.extra.SCHEDULE_TIME_STRING";

    private static final String STATE_CHECKED_POSITION = "checkedPosition";

    private Uri mTrackUri;
    private Cursor mCursor;
    private CursorAdapter mAdapter;
    private int mCheckedPosition = -1;
    private boolean mHasSetEmptyText = false;

    private NotifyingAsyncQueryHandler mHandler;
    private Handler mMessageQueueHandler = new Handler();

    /**
     * Create a new instance of CountingFragment, providing "num"
     * as an argument.
     */
    static DbMaintDataFragment newInstance(int num) {
    	DbMaintDataFragment f = new DbMaintDataFragment();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt("num", num);
        f.setArguments(args);

        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNum = getArguments() != null ? getArguments().getInt("num") : 1;
        mHandler = new NotifyingAsyncQueryHandler(getActivity().getContentResolver(), this);
        reloadFromArguments(getArguments());
    }
    
//    /**
//     * The Fragment's UI is xxx.
//     */
//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//            Bundle savedInstanceState) {
//        View v = inflater.inflate(R.id.fragment_data, container, false);
//        View tv = v.findViewById(R.id.text);
//        ((TextView)tv).setText("Fragment #" + mNum);
//        tv.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.gallery_thumb));
//        return v;
//    }


    public void reloadFromArguments(Bundle arguments) {
        // Teardown from previous arguments
        if (mCursor != null) {
            getActivity().stopManagingCursor(mCursor);
            mCursor = null;
        }

        mCheckedPosition = -1;
        setListAdapter(null);

        mHandler.cancelOperation(SearchQuery._TOKEN);
        mHandler.cancelOperation(PDataQuery._TOKEN);
        mHandler.cancelOperation(TracksQuery._TOKEN);

        // Load new arguments
        final Intent intent = BaseActivity.fragmentArgumentsToIntent(arguments);
        //final Uri dataUri = intent.getData();
        final int dataQueryToken;

        final Uri dataUri = Data.CONTENT_URI;  // not gettting from intent

        if (dataUri == null) {
            return;
        }

        String[] projection;
//        if (!AquaNotesDbContract.Sessions.isSearchUri(dataUri)) {
            mAdapter = new DataAdapter(getActivity());
            projection = PDataQuery.PROJECTION;
            dataQueryToken = PDataQuery._TOKEN;

//        } else {
//            mAdapter = new SearchAdapter(getActivity());
//            projection = SearchQuery.PROJECTION;
//            sessionQueryToken = SearchQuery._TOKEN;
//        }

        setListAdapter(mAdapter);

        // Start background query to load sessions
        mHandler.startQuery(dataQueryToken, null, dataUri, projection, null, null,
                AquaNotesDbContract.Data.DEFAULT_SORT);

        // If caller launched us with specific track hint, pass it along when
        // launching session details. Also start a query to load the track info.
        mTrackUri = intent.getParcelableExtra(SessionDetailFragment.EXTRA_TRACK);
        if (mTrackUri != null) {
            mHandler.startQuery(TracksQuery._TOKEN, mTrackUri, TracksQuery.PROJECTION);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        if (savedInstanceState != null) {
            mCheckedPosition = savedInstanceState.getInt(STATE_CHECKED_POSITION, -1);
        }

        if (!mHasSetEmptyText) {
            // Could be a bug, but calling this twice makes it become visible when it shouldn't
            // be visible.
            setEmptyText(getString(R.string.empty_sessions));
            mHasSetEmptyText = true;
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (getActivity() == null) {
            return;
        }

        if (token == PDataQuery._TOKEN || token == SearchQuery._TOKEN) {
            onDataOrSearchQueryComplete(cursor);
        } else if (token == TracksQuery._TOKEN) {
            onTrackQueryComplete(cursor);
        } else {
            Log.d("DbMaintDataFragment/onQueryComplete", "Query complete, Not Actionable: " + token);
            cursor.close();
        }
    }

    /**
     * Handle {@link SessionsQuery} {@link Cursor}.
     */
    private void onDataOrSearchQueryComplete(Cursor cursor) {
        if (mCursor != null) {
            // In case cancelOperation() doesn't work and we end up with consecutive calls to this
            // callback.
            getActivity().stopManagingCursor(mCursor);
            mCursor = null;
        }

        mCursor = cursor;
        getActivity().startManagingCursor(mCursor);
        mAdapter.changeCursor(mCursor);
        if (mCheckedPosition >= 0 && getView() != null) {
            getListView().setItemChecked(mCheckedPosition, true);
        }
    }

    /**
     * Handle {@link TracksQuery} {@link Cursor}.
     */
    private void onTrackQueryComplete(Cursor cursor) {
        try {
            if (!cursor.moveToFirst()) {
                return;
            }

            // Use found track to build title-bar
            ActivityHelper activityHelper = ((BaseActivity) getActivity()).getActivityHelper();
            String trackName = cursor.getString(TracksQuery.TRACK_NAME);
            activityHelper.setActionBarTitle(trackName);
            activityHelper.setActionBarColor(cursor.getInt(TracksQuery.TRACK_COLOR));

            AnalyticsUtils.getInstance(getActivity()).trackPageView("/Tracks/" + trackName);
        } finally {
            cursor.close();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMessageQueueHandler.post(mRefreshSessionsRunnable);
        getActivity().getContentResolver().registerContentObserver(
                AquaNotesDbContract.Sessions.CONTENT_URI, true, mSessionChangesObserver);
        if (mCursor != null) {
            mCursor.requery();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mMessageQueueHandler.removeCallbacks(mRefreshSessionsRunnable);
        getActivity().getContentResolver().unregisterContentObserver(mSessionChangesObserver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CHECKED_POSITION, mCheckedPosition);
    }

    /** {@inheritDoc} */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Launch viewer for specific session, passing along any track knowledge
        // that should influence the title-bar.
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        final String sessionId = cursor.getString(cursor.getColumnIndex(
                AquaNotesDbContract.Sessions.SESSION_ID));
        final Uri sessionUri = AquaNotesDbContract.Sessions.buildSessionUri(sessionId);
        final Intent intent = new Intent(Intent.ACTION_VIEW, sessionUri);
        intent.putExtra(SessionDetailFragment.EXTRA_TRACK, mTrackUri);
        ((BaseActivity) getActivity()).openActivityOrFragment(intent);

        getListView().setItemChecked(position, true);
        mCheckedPosition = position;
    }

    public void clearCheckedPosition() {
        if (mCheckedPosition >= 0) {
            getListView().setItemChecked(mCheckedPosition, false);
            mCheckedPosition = -1;
        }
    }

    /**
     * {@link CursorAdapter} that renders a {@link SessionsQuery}.
     */
    private class DataAdapter extends CursorAdapter {
        public DataAdapter(Context context) {
            super(context, null);
        }

        /** {@inheritDoc} */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getActivity().getLayoutInflater().inflate(R.layout.list_item_datum, parent,
                    false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final TextView titleView = (TextView) view.findViewById(R.id.datum_name);
            final TextView subtitleView = (TextView) view.findViewById(R.id.datum_timstamp);

            String name = cursor.getString(PDataQuery.NAME);
            String value = cursor.getString(PDataQuery.VALUE);
            long timestampL = cursor.getLong(PDataQuery.TIMESTAMP);

            String line1 = name + " = " + value;
            titleView.setText(line1);

            // Format time block this session occupies
//            final long blockStart = cursor.getLong(SessionsQuery.BLOCK_START);
//            final long blockEnd = cursor.getLong(SessionsQuery.BLOCK_END);
//            final String roomName = cursor.getString(SessionsQuery.ROOM_NAME);
//            final String subtitle = formatSessionSubtitle(timestamp, timestamp, " ", context);
			Date timestampD = new Date(timestampL);
			SimpleDateFormat formatter = new SimpleDateFormat("M/d/yy h:mm a");
			String timestampS = formatter.format(timestampD);

            subtitleView.setText(timestampS);

            final boolean starred = false; //cursor.getInt(SessionsQuery.STARRED) != 0;
            view.findViewById(R.id.star_button).setVisibility(
                    starred ? View.VISIBLE : View.INVISIBLE);

            // Possibly indicate that the session has occurred in the past.
//            UIUtils.setSessionTitleColor(blockStart, blockEnd, titleView, subtitleView);
        }
    }

    /**
     * {@link CursorAdapter} that renders a {@link SearchQuery}.
     */
    private class SearchAdapter extends CursorAdapter {
        public SearchAdapter(Context context) {
            super(context, null);
        }

        /** {@inheritDoc} */
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getActivity().getLayoutInflater().inflate(R.layout.list_item_datum_oneline, parent,
                    false);
        }

        /** {@inheritDoc} */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((TextView) view.findViewById(R.id.session_title)).setText(cursor
                    .getString(SearchQuery.TITLE));

            final String snippet = cursor.getString(SearchQuery.SEARCH_SNIPPET);

            final Spannable styledSnippet = buildStyledSnippet(snippet);
            ((TextView) view.findViewById(R.id.session_subtitle)).setText(styledSnippet);

            final boolean starred = cursor.getInt(SearchQuery.STARRED) != 0;
            view.findViewById(R.id.star_button).setVisibility(
                    starred ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private ContentObserver mSessionChangesObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (mCursor != null) {
                mCursor.requery();
            }
        }
    };

    private Runnable mRefreshSessionsRunnable = new Runnable() {
        public void run() {
            if (mAdapter != null) {
                // This is used to refresh session title colors.
                mAdapter.notifyDataSetChanged();
            }

            // Check again on the next quarter hour, with some padding to account for network
            // time differences.
            long nextQuarterHour = (SystemClock.uptimeMillis() / 900000 + 1) * 900000 + 5000;
            mMessageQueueHandler.postAtTime(mRefreshSessionsRunnable, nextQuarterHour);
        }
    };

    /**
     * {@link com.heneryh.aquanotes.provider.AquaNotesDbContract.Sessions} query parameters.
     */
    private interface SessionsQuery {
        int _TOKEN = 0x1;

        String[] PROJECTION = {
                BaseColumns._ID,
                AquaNotesDbContract.Sessions.SESSION_ID,
                AquaNotesDbContract.Sessions.SESSION_TITLE,
                AquaNotesDbContract.Sessions.SESSION_STARRED,
                AquaNotesDbContract.Blocks.BLOCK_START,
                AquaNotesDbContract.Blocks.BLOCK_END,
                AquaNotesDbContract.Rooms.ROOM_NAME,
        };

        int _ID = 0;
        int SESSION_ID = 1;
        int TITLE = 2;
        int STARRED = 3;
        int BLOCK_START = 4;
        int BLOCK_END = 5;
        int ROOM_NAME = 6;
    }

    /**
     * {@link com.heneryh.aquanotes.provider.AquaNotesDbContract.Tracks} query parameters.
     */
    private interface TracksQuery {
        int _TOKEN = 0x2;

        String[] PROJECTION = {
                AquaNotesDbContract.Tracks.TRACK_NAME,
                AquaNotesDbContract.Tracks.TRACK_COLOR,
        };

        int TRACK_NAME = 0;
        int TRACK_COLOR = 1;
    }

    /** {@link com.heneryh.aquanotes.provider.AquaNotesDbContract.Sessions} search query
     * parameters. */
    private interface SearchQuery {
        int _TOKEN = 0x3;

        String[] PROJECTION = {
                BaseColumns._ID,
                AquaNotesDbContract.Sessions.SESSION_ID,
                AquaNotesDbContract.Sessions.SESSION_TITLE,
                AquaNotesDbContract.Sessions.SEARCH_SNIPPET,
                AquaNotesDbContract.Sessions.SESSION_STARRED,
        };

        int _ID = 0;
        int SESSION_ID = 1;
        int TITLE = 2;
        int SEARCH_SNIPPET = 3;
        int STARRED = 4;
    }
    
    private interface PDataQuery {
        int _TOKEN = 0x4;
        
        String[] PROJECTION = {
                BaseColumns._ID,
                AquaNotesDbContract.ViewProbeData.TYPE,
                AquaNotesDbContract.ViewProbeData.VALUE,
                AquaNotesDbContract.ViewProbeData.TIMESTAMP,
                AquaNotesDbContract.ViewProbeData.PARENT_ID,
                AquaNotesDbContract.ViewProbeData.NAME,
                AquaNotesDbContract.ViewProbeData.RESOURCE_ID,
                AquaNotesDbContract.ViewProbeData.CONTROLLER_ID,
        };

        int _ID = 0;
        int TYPE = 1;
        int VALUE = 2;
        int TIMESTAMP = 3;
        int PARENT_ID = 4;
        int NAME = 5;
        int RESOURCE_ID = 6;
        int CONTROLLER_ID = 7;
    }
    
    private interface ODataQuery {
        int _TOKEN = 0x5;
        
        String[] PROJECTION = {
                BaseColumns._ID,
                AquaNotesDbContract.ViewOutletData.TYPE,
                AquaNotesDbContract.ViewOutletData.VALUE,
                AquaNotesDbContract.ViewOutletData.TIMESTAMP,
                AquaNotesDbContract.ViewOutletData.PARENT_ID,
                AquaNotesDbContract.ViewOutletData.NAME,
                AquaNotesDbContract.ViewOutletData.DEVICE_ID,
                AquaNotesDbContract.ViewOutletData.RESOURCE_ID,
                AquaNotesDbContract.ViewOutletData.CONTROLLER_ID,
        };

        int _ID = 0;
        int TYPE = 1;
        int VALUE = 2;
        int TIMESTAMP = 3;
        int PARENT_ID = 4;
        int NAME = 5;
        int DEVICE_ID = 6;
        int RESOURCE_ID = 7;
        int CONTROLLER_ID = 8;
    }

}

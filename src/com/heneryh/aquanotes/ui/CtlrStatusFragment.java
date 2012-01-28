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
import com.heneryh.aquanotes.ui.widget.ControllersLayoutDeleteMe;
import com.heneryh.aquanotes.ui.widget.ObservableScrollView;
import com.heneryh.aquanotes.ui.widget.Workspace;
import com.heneryh.aquanotes.util.AnalyticsUtils;
import com.heneryh.aquanotes.util.CatchNotesHelper;
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
import android.graphics.Typeface;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

/**
 * Shows a horizontally-pageable set of controller pages. Horizontaly paging is achieved using
 * {@link Workspace}, and the primary UI classes for rendering the calendar are
 */
public class CtlrStatusFragment extends Fragment implements
        NotifyingAsyncQueryHandler.AsyncQueryListener,
        ObservableScrollView.OnScrollListener,
        View.OnClickListener {
	
    private static final String TAG = "CtlrStatusFragment";

	
    public static final String EXTRA_TIME_START = "com.google.android.iosched.extra.TIME_START";
    public static final String EXTRA_TIME_END = "com.google.android.iosched.extra.TIME_END";

    private static final String TAG_PROBES = "probes";
    private static final String TAG_OUTLETS = "outlets";
    private static final String TAG_NOTES = "notes";

    private static final HashMap<String, Integer> sTypeColumnMap = buildTypeColumnMap();

    private NotifyingAsyncQueryHandler mHandler;

    private static StyleSpan sBoldSpan = new StyleSpan(Typeface.BOLD);

    private Workspace mWorkspace;
    private TextView mWorkspaceTitleView;
    private View mLeftIndicator;
    private View mRightIndicator;
    private int mTitleCurrentCtlrIndex = -1;

    /**
     * A helper class containing object references related to a particular controller tab-view.
     */
    private List<Ctlr> mCtlrs = new ArrayList<Ctlr>();

    private class Ctlr {
        private int index;
        
        private ViewGroup mRootView; // Host for the tab view within the fragment.  Below the L/R and Workspace Title
        private ObservableScrollView scrollView;
        private TabHost mTabHost;	 // Tab host for this controller

        
        private int mControllerId;
        
        private String mTitleString;  // Probes Tab
        private TextView mTitleView;// check
        private TextView mSubtitleView;// check
        private String mUrl;
        private String mHashtag;
        private TextView mTagDisplayView; // check
        private boolean mHasSummaryContent;
        private CompoundButton mStarredView;// check
        private TextView mAbstractView;// check
        private TextView mRequirementsView;// check  
        View mRequirementsBlockView; // check
        private String mRoomName;
        private String mSubtitle;
        private String mRoomId;
        private String mSessionId;
        private Uri mSessionUri;
        private Uri mTrackUri;
    }
 
    private boolean mSessionCursor = false;
    private boolean mSpeakersCursor = false;


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
        mWorkspaceTitleView = (TextView) root.findViewById(R.id.controller_title);
        mRightIndicator = root.findViewById(R.id.indicator_right);
        mWorkspace = (Workspace) root.findViewById(R.id.workspace);

        // Need at least one for the process to continue
        setupCtlr(new Ctlr(), -1,"Empty1");
//        setupCtlr(new Ctlr(), -2,"Empty2");

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
	
        setHasOptionsMenu(true);
        
        /**
         * Set the scroll listener for the workspace
         */
        mWorkspace.setOnScrollListener(new Workspace.OnScrollListener() {
            public void onScroll(float screenFraction) {
                updateWorkspaceHeader(Math.round(screenFraction));
            }
        }, true);

        return root;
    }

    public void updateWorkspaceHeader(int ctlrIndex) {
//    	if (mTitleCurrentCtlrIndex == ctlrIndex) 
//    		return;
    	
        mTitleCurrentCtlrIndex = ctlrIndex;
        Ctlr ctlr = mCtlrs.get(ctlrIndex);
        mWorkspaceTitleView.setText(ctlr.mTitleString);

        mLeftIndicator
                .setVisibility((ctlrIndex != 0) ? View.VISIBLE : View.INVISIBLE);
        mRightIndicator
                .setVisibility((ctlrIndex < mCtlrs.size() - 1) ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Prepare the TabHost for this controller and inflate it within a workspace pane
     * 
     * @param controllerId
     * @param title
     */
    private void setupCtlr(Ctlr ctlr, int controllerId, String title) {

    	LayoutInflater li = LayoutInflater.from(getActivity());

    	ctlr.mRootView = (ViewGroup) li.inflate(R.layout.controllers_content_tabbed, null);
    	ctlr.scrollView = (ObservableScrollView) ctlr.mRootView.findViewById(R.id.controllers_scroll);
        ctlr.scrollView.setOnScrollListener(this);
    	ctlr.mTabHost = (TabHost) ctlr.mRootView.findViewById(android.R.id.tabhost);
    	ctlr.mTabHost.setup();
    	ctlr.index = mCtlrs.size();
    	ctlr.mTitleView = (TextView) ctlr.mRootView.findViewById(R.id.session_title);
    	ctlr.mSubtitleView = (TextView) ctlr.mRootView.findViewById(R.id.session_subtitle);
    	ctlr.mStarredView = (CompoundButton) ctlr.mRootView.findViewById(R.id.star_button);
    	ctlr.mStarredView.setFocusable(true);
    	ctlr.mStarredView.setClickable(true);
    	ctlr.mControllerId=controllerId;
    	ctlr.mTitleString=title;
    	setupProbesTab(ctlr);
    	setupOutletsTab(ctlr);
    	setupNotesTab(ctlr);
    	mWorkspace.addView(ctlr.mRootView);
    	mCtlrs.add(ctlr);
    	updateWorkspaceHeader(ctlr.index);
     }
    
    /**
     * Build and add "summary" tab.
     */
    private void setupProbesTab(Ctlr ctlr) {
    	ctlr.mTabHost.addTab(ctlr.mTabHost.newTabSpec(TAG_PROBES)
                .setIndicator(buildIndicator(ctlr, R.string.controllers_tabs_probes))
                .setContent(R.id.tab_ctl_probes));
    	
    	ctlr.mAbstractView = (TextView) ctlr.mRootView.findViewById(R.id.session_abstract);
    	ctlr.mRequirementsView = (TextView) ctlr.mRootView.findViewById(R.id.session_requirements);
    	ctlr.mTagDisplayView = (TextView) ctlr.mRootView.findViewById(R.id.session_tags_button);
    	ctlr.mRequirementsBlockView = ctlr.mRootView.findViewById(R.id.session_requirements_block);

    }
    private void setupOutletsTab(Ctlr ctlr) {
        // Summary content comes from existing layout
    	ctlr.mTabHost.addTab(ctlr.mTabHost.newTabSpec(TAG_OUTLETS)
                .setIndicator(buildIndicator(ctlr, R.string.controllers_tabs_outlets))
                .setContent(R.id.tab_ctl_outlets));
    }
    private void setupNotesTab(Ctlr ctlr) {
        // Summary content comes from existing layout
    	ctlr.mTabHost.addTab(ctlr.mTabHost.newTabSpec(TAG_NOTES)
                .setIndicator(buildIndicator(ctlr, R.string.controllers_tabs_notes))
                .setContent(R.id.tab_ctl_notes));
    }

    /**
     * Handle {@link SessionsQuery} {@link Cursor}.
     */
    private void updateControllerTabs(Ctlr cntl, Cursor cursor) {
        try {
            mSessionCursor = true;

            // Header Area
            cntl.mTitleString = cursor.getString(ControllersQuery.TITLE);
            cntl.mSubtitle = cursor.getString(ControllersQuery.WAN_URL);
            cntl.mTitleView.setText(cntl.mTitleString);
            cntl.mSubtitleView.setText(cntl.mSubtitle);

            // Probes Tab Area
            cntl.mUrl = "http://test"; //cursor.getString(SessionsQuery.URL);
            if (TextUtils.isEmpty(cntl.mUrl)) {
            	cntl.mUrl = "";
            }

            cntl.mHashtag = ""; //cursor.getString(SessionsQuery.HASHTAG);
            if (!TextUtils.isEmpty(cntl.mHashtag)) {
                // Create the button text
                SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append(getString(R.string.tag_stream) + " ");
                int boldStart = sb.length();
                sb.append(getHashtagsString(cntl.mHashtag));
                sb.setSpan(sBoldSpan, boldStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                cntl.mTagDisplayView.setText(sb);

                cntl.mTagDisplayView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent intent = new Intent(getActivity(), TagStreamActivity.class);
                        intent.putExtra(TagStreamFragment.EXTRA_QUERY, getHashtagsString("was hash tag"));
                        startActivity(intent);
                    }
                });
            } else {
            	cntl.mTagDisplayView.setVisibility(View.GONE);
            }
            
            cntl.mRoomName = "Room";
            cntl.mRoomId = "Rm 202"; //cursor.getString(SessionsQuery.ROOM_ID);

            // Unregister around setting checked state to avoid triggering
            // listener since change isn't user generated.
//            cntl.mStarred.setOnCheckedChangeListener(null);
//            cntl.mStarred.setChecked(false);
//            cntl.mStarred.setOnCheckedChangeListener(this);

            final String sessionAbstract = "I can work on the order these show up, it is just a sort on the query.  Note there is a title above in the header between the l/r icons.  Also a title down in the tab host header.  I will do one or the other not both.  Oh, and you have to swipe l/r once to get that top title to sync.";  //cursor.getString(SessionsQuery.ABSTRACT);
            if (!TextUtils.isEmpty(sessionAbstract)) {
                UIUtils.setTextMaybeHtml(cntl.mAbstractView, sessionAbstract);
                cntl.mAbstractView.setVisibility(View.VISIBLE);
                cntl.mHasSummaryContent = true;
            } else {
            	cntl.mAbstractView.setVisibility(View.GONE);
            }

            final String sessionRequirements = "How shall I lay out this screen??? Suggestions?"; //cursor.getString(SessionsQuery.REQUIREMENTS);
            if (!TextUtils.isEmpty(sessionRequirements)) {
                UIUtils.setTextMaybeHtml(cntl.mRequirementsView, sessionRequirements);
                cntl.mRequirementsBlockView.setVisibility(View.VISIBLE);
                cntl.mHasSummaryContent = true;
            } else {
            	cntl.mRequirementsBlockView.setVisibility(View.GONE);
            }

            // Show empty message when all data is loaded, and nothing to show
            if (mSpeakersCursor && !cntl.mHasSummaryContent) {
            	cntl.mRootView.findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
            }

            AnalyticsUtils.getInstance(getActivity()).trackPageView("/Sessions/" + cntl.mTitleString);

//            updateLinksTab(cursor);
//            updateNotesTab();
        	updateWorkspaceHeader(cntl.index);


        } finally {
//            cursor.close();
        }
    }

    private void updateOutletsTab(Cursor cursor) {
//        ViewGroup container = (ViewGroup) mRootView.findViewById(R.id.links_container);
//
//        // Remove all views but the 'empty' view
//        int childCount = container.getChildCount();
//        if (childCount > 1) {
//            container.removeViews(1, childCount - 1);
//        }
//
//        LayoutInflater inflater = getLayoutInflater(null);
//
//        boolean hasLinks = false;
//        for (int i = 0; i < SessionsQuery.LINKS_INDICES.length; i++) {
//            final String url = cursor.getString(SessionsQuery.LINKS_INDICES[i]);
//            if (!TextUtils.isEmpty(url)) {
//                hasLinks = true;
//                ViewGroup linkContainer = (ViewGroup)
//                        inflater.inflate(R.layout.list_item_session_link, container, false);
//                ((TextView) linkContainer.findViewById(R.id.link_text)).setText(
//                        SessionsQuery.LINKS_TITLES[i]);
//                final int linkTitleIndex = i;
//                linkContainer.setOnClickListener(new View.OnClickListener() {
//                    public void onClick(View view) {
//                        fireLinkEvent(SessionsQuery.LINKS_TITLES[linkTitleIndex]);
//                    	Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
//                        startActivity(intent);
//                        
//                    }
//                });
//
//                container.addView(linkContainer);
//
//                // Create separator
//                View separatorView = new ImageView(getActivity());
//                separatorView.setLayoutParams(
//                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
//                                ViewGroup.LayoutParams.WRAP_CONTENT));
//                separatorView.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
//                container.addView(separatorView);
//            }
//        }
//
//        container.findViewById(R.id.empty_links).setVisibility(hasLinks ? View.GONE : View.VISIBLE);
    }

    private void updateNotesTab() {
//        final CatchNotesHelper helper = new CatchNotesHelper(getActivity());
//        final boolean notesInstalled = helper.isNotesInstalledAndMinimumVersion();
//
//        final Intent marketIntent = helper.notesMarketIntent();
//        final Intent newIntent = helper.createNoteIntent(
//                getString(R.string.note_template, mTitleString, getHashtagsString()));
//        
//        final Intent viewIntent = helper.viewNotesIntent(getHashtagsString());
//
//        // Set icons and click listeners
//        ((ImageView) mRootView.findViewById(R.id.notes_catch_market_icon)).setImageDrawable(
//                UIUtils.getIconForIntent(getActivity(), marketIntent));
//        ((ImageView) mRootView.findViewById(R.id.notes_catch_new_icon)).setImageDrawable(
//                UIUtils.getIconForIntent(getActivity(), newIntent));
//        ((ImageView) mRootView.findViewById(R.id.notes_catch_view_icon)).setImageDrawable(
//                UIUtils.getIconForIntent(getActivity(), viewIntent));
//
//        // Set click listeners
//        mRootView.findViewById(R.id.notes_catch_market_link).setOnClickListener(
//                new View.OnClickListener() {
//                    public void onClick(View view) {
//                        startActivity(marketIntent);
//                        fireNotesEvent(R.string.notes_catch_market_title);
//                    }
//                });
//
//        mRootView.findViewById(R.id.notes_catch_new_link).setOnClickListener(
//                new View.OnClickListener() {
//                    public void onClick(View view) {
//                        startActivity(newIntent);
//                        fireNotesEvent(R.string.notes_catch_new_title);
//                    }
//                });
//
//        mRootView.findViewById(R.id.notes_catch_view_link).setOnClickListener(
//                new View.OnClickListener() {
//                    public void onClick(View view) {
//                        startActivity(viewIntent);
//                        fireNotesEvent(R.string.notes_catch_view_title);
//                    }
//                });
//
//        // Show/hide elements
//        mRootView.findViewById(R.id.notes_catch_market_link).setVisibility(
//                notesInstalled ? View.GONE : View.VISIBLE);
//        mRootView.findViewById(R.id.notes_catch_market_separator).setVisibility(
//                notesInstalled ? View.GONE : View.VISIBLE);
//
//        mRootView.findViewById(R.id.notes_catch_new_link).setVisibility(
//                !notesInstalled ? View.GONE : View.VISIBLE);
//        mRootView.findViewById(R.id.notes_catch_new_separator).setVisibility(
//                !notesInstalled ? View.GONE : View.VISIBLE);
//
//        mRootView.findViewById(R.id.notes_catch_view_link).setVisibility(
//                !notesInstalled ? View.GONE : View.VISIBLE);
//        mRootView.findViewById(R.id.notes_catch_view_separator).setVisibility(
//                !notesInstalled ? View.GONE : View.VISIBLE);
    }


    /**
     * Build and add "notes" tab.
     */
    
    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label.
     *
     * @param textRes
     * @return View
     */
    private View buildIndicator(Ctlr ctlr, int textRes) {
        final TextView indicator = (TextView) getActivity().getLayoutInflater()
                .inflate(R.layout.tab_indicator,
                        (ViewGroup) ctlr.mRootView.findViewById(android.R.id.tabs), false);
        indicator.setText(textRes);
        return indicator;
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
        	/** For each controller in the database, */
        	while (cursor.moveToNext()) {
        		final Integer controllerId = cursor.getInt(ControllersQuery._ID);
        		final String controllerTitle = cursor.getString(ControllersQuery.TITLE);

        		// Look for this controller already in the list
        		int index = -1;
        		Iterator<Ctlr> iterator = mCtlrs.iterator();
        		while (iterator.hasNext()) {
        			Ctlr ctlr = iterator.next();
        			if(ctlr.mControllerId==controllerId) 
        				index=ctlr.index;
        		}

        		Ctlr thisCtlr;
        		if(mCtlrs.get(0).mControllerId==-1) {
        			// If only the stub one is there, reuse it.
        			thisCtlr = mCtlrs.get(0);
        			thisCtlr.mControllerId = controllerId;
        			thisCtlr.mTitleString = controllerTitle;
        		}
        		else if (index == -1) {
        			// else if it is not found, create a new one
        			thisCtlr = new Ctlr();
        			setupCtlr(thisCtlr, controllerId, controllerTitle);
        		}
        		else
        			// otherwise it must be in there somewhere
        			thisCtlr = mCtlrs.get(index);

        		updateControllerTabs(thisCtlr, cursor);
//                updateWorkspaceHeader(index);

        	} // end of while()
        } finally {
        	cursor.close();
        }

//        updateWorkspaceHeader(0);
//
//        /**
//         * Set the scroll listener for the workspace
//        */
//        mWorkspace.setOnScrollListener(new Workspace.OnScrollListener() {
//        	public void onScroll(float screenFraction) {
//        		updateWorkspaceHeader(Math.round(screenFraction));
//        	}
//        }, true);

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
        }
    };

    private String getHashtagsString(String mHashtag) {
        if (!TextUtils.isEmpty(mHashtag)) {
            return TagStreamFragment.CONFERENCE_HASHTAG + " #" + mHashtag;
        } else {
            return TagStreamFragment.CONFERENCE_HASHTAG;
        }
    }

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

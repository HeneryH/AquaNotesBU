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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.heneryh.aquanotes.R;
import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Probes;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.ui.phone.SessionDetailActivity;
import com.heneryh.aquanotes.ui.phone.VendorDetailActivity;
import com.heneryh.aquanotes.ui.widget.ObservableScrollView;
import com.heneryh.aquanotes.ui.widget.Workspace;
import com.heneryh.aquanotes.util.AnalyticsUtils;
import com.heneryh.aquanotes.util.MotionEventUtils;
import com.heneryh.aquanotes.util.NotifyingAsyncQueryHandler;
import com.heneryh.aquanotes.util.UIUtils;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;

/**
 * An activity that shows the raw database table contents. This activity can be
 * either single or multi-pane, depending on the device configuration. We want the multi-pane
 * support that {@link BaseMultiPaneActivity} offers, so we inherit from it instead of
 * {@link BaseSinglePaneActivity}.
 */
public class DbMaintActivity extends BaseMultiPaneActivity implements
						NotifyingAsyncQueryHandler.AsyncQueryListener,
						ObservableScrollView.OnScrollListener,
						View.OnClickListener {

	private NotifyingAsyncQueryHandler mHandler;

    public static final String TAG_PROBES = "probes";
    public static final String TAG_OUTLETS = "outlets";
    private static final String TAG_NOTES = "notes";

    private Workspace mWorkspace;
    private TextView mWorkspaceTitleView;
    private View mLeftIndicator;
    private View mRightIndicator;

    private static StyleSpan sBoldSpan = new StyleSpan(Typeface.BOLD);

    /**
     * A helper class containing object references related to a particular controller tab-view.
     */
    private List<Ctlr> mCtlrs = new ArrayList<Ctlr>();

    private class Ctlr {
        private int index;
        
        private ViewGroup mRootView; // Host for the tab view within the fragment.  Below the L/R and Workspace Title
        private ObservableScrollView scrollView;
        private TabHost mTabHost;
        private TabWidget mTabWidget;
        private TabManager mTabManager;

        private int mControllerId;
        private Uri mControllerUri;
        
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new NotifyingAsyncQueryHandler(getContentResolver(), this);
   
        getActivityHelper().setupActionBar(getTitle(), 0);

        setContentView(R.layout.activity_db_maint);

        mLeftIndicator = findViewById(R.id.indicator_left);
        mWorkspaceTitleView = (TextView) findViewById(R.id.controller_title);
        mRightIndicator = findViewById(R.id.indicator_right);
        mWorkspace = (Workspace) findViewById(R.id.workspace);

        // Need at least one for the process to continue, update later
        setupCtlr(new Ctlr(), -1,"Empty1");

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
	        
        /**
         * Set the scroll listener for the workspace
         */
        mWorkspace.setOnScrollListener(new Workspace.OnScrollListener() {
            public void onScroll(float screenFraction) {
                updateWorkspaceHeader(Math.round(screenFraction));
            }
        }, true);
        
        
   }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupSubActivity();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
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
    public void onPause() {
        super.onPause();
//        getActivity().unregisterReceiver(mReceiver);
//        getActivity().getContentResolver().unregisterContentObserver(mSessionChangesObserver);
    }

    public void updateWorkspaceHeader(int ctlrIndex) {

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

    	LayoutInflater li = getLayoutInflater();

    	ctlr.mRootView = (ViewGroup) li.inflate(R.layout.activity_db_maint_content_tabbed, null);
    	ctlr.scrollView = (ObservableScrollView) ctlr.mRootView.findViewById(R.id.controllers_scroll);
        ctlr.scrollView.setOnScrollListener(this);
        
    	ctlr.mTabHost = (TabHost) ctlr.mRootView.findViewById(android.R.id.tabhost);
    	ctlr.mTabWidget = (TabWidget) findViewById(android.R.id.tabs);
    	ctlr.mTabHost.setup();
    	ctlr.mTabManager = new TabManager(this, ctlr.mTabHost, R.id.realtabcontent);

    	ctlr.index = mCtlrs.size();
    	ctlr.mTitleView = (TextView) ctlr.mRootView.findViewById(R.id.session_title);
    	ctlr.mSubtitleView = (TextView) ctlr.mRootView.findViewById(R.id.session_subtitle);
    	
    	ctlr.mStarredView = (CompoundButton) ctlr.mRootView.findViewById(R.id.star_button);
    	ctlr.mStarredView.setFocusable(true);
    	ctlr.mStarredView.setClickable(true);
    	ctlr.mControllerId=controllerId;
    	ctlr.mTitleString=title;
    	
    	ctlr.mTabManager.addTab(ctlr.mTabHost.newTabSpec(TAG_PROBES)
    			.setIndicator(buildIndicator(ctlr, R.string.db_maint_probes)),
    			DbMaintProbesFragment.class, null);

    	ctlr.mTabManager.addTab(ctlr.mTabHost.newTabSpec(TAG_OUTLETS)
    			.setIndicator(buildIndicator(ctlr, R.string.db_maint_outlets)),
    			DbMaintProbesFragment.class, null);

//    	ctlr.mTabManager.addTab(ctlr.mTabHost.newTabSpec(TAG_DATA)
//    			.setIndicator(buildIndicator(ctlr, R.string.db_maint_data)),
//    			DbMaintDataFragment.class, null);
    	
    	mWorkspace.addView(ctlr.mRootView);
    	mCtlrs.add(ctlr);
    	updateWorkspaceHeader(ctlr.index);
     }
    
	@Override
	public void onQueryComplete(int token, Object cookie, Cursor cursor) {
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

    		thisCtlr.mControllerUri = Controllers.buildQueryControllerXUri(controllerId);

    		updateControllerTabs(thisCtlr, cursor);

    	} // end of while()
    } finally {
    	cursor.close();
    }

//    updateWorkspaceHeader(0);
//
//    /**
//     * Set the scroll listener for the workspace
//    */
//    mWorkspace.setOnScrollListener(new Workspace.OnScrollListener() {
//    	public void onScroll(float screenFraction) {
//    		updateWorkspaceHeader(Math.round(screenFraction));
//    	}
//    }, true);

//    Ctlr ctlr = (Ctlr) cookie;
//
//    // Clear out any existing sessions before inserting again
//    ctlr.blocksView.removeAllBlocks();
//
//    try {
//        while (cursor.moveToNext()) {
//            final String type = cursor.getString(BlocksQuery.BLOCK_TYPE);
//            final Integer column = sTypeColumnMap.get(type);
//            // TODO: place random blocks at bottom of entire layout
//            if (column == null) {
//                continue;
//            }
//
//            final String blockId = cursor.getString(BlocksQuery.BLOCK_ID);
//            final String title = cursor.getString(BlocksQuery.BLOCK_TITLE);
//            final long start = cursor.getLong(BlocksQuery.BLOCK_START);
//            final long end = cursor.getLong(BlocksQuery.BLOCK_END);
//            final boolean containsStarred = cursor.getInt(BlocksQuery.CONTAINS_STARRED) != 0;
//
//            final BlockView blockView = new BlockView(getActivity(), blockId, title, start, end,
//                    containsStarred, column);
//
//            final int sessionsCount = cursor.getInt(BlocksQuery.SESSIONS_COUNT);
//            if (sessionsCount > 0) {
//                blockView.setOnClickListener(this);
//            } else {
//                blockView.setFocusable(false);
//                blockView.setEnabled(false);
//                LayerDrawable buttonDrawable = (LayerDrawable) blockView.getBackground();
//                buttonDrawable.getDrawable(0).setAlpha(DISABLED_BLOCK_ALPHA);
//                buttonDrawable.getDrawable(2).setAlpha(DISABLED_BLOCK_ALPHA);
//            }
//
//            ctlr.blocksView.addBlock(blockView);
//        }
//    } finally {
//        cursor.close();
//    }
}

    /**
     * Handle {@link SessionsQuery} {@link Cursor}.
     */
    private void updateControllerTabs(Ctlr cntl, Cursor cursor) {
//        try {
////            mSessionCursor = true;
//
//            // Header Area
//            cntl.mTitleString = cursor.getString(ControllersQuery.TITLE);
//            cntl.mSubtitle = cursor.getString(ControllersQuery.WAN_URL);
//            cntl.mTitleView.setText(cntl.mTitleString);
//            cntl.mSubtitleView.setText(cntl.mSubtitle);
//
//            // Probes Tab Area
//            cntl.mUrl = "http://test"; //cursor.getString(SessionsQuery.URL);
//            if (TextUtils.isEmpty(cntl.mUrl)) {
//            	cntl.mUrl = "";
//            }
//
//            cntl.mHashtag = ""; //cursor.getString(SessionsQuery.HASHTAG);
//            if (!TextUtils.isEmpty(cntl.mHashtag)) {
//                // Create the button text
//                SpannableStringBuilder sb = new SpannableStringBuilder();
//                sb.append(getString(R.string.tag_stream) + " ");
//                int boldStart = sb.length();
//                sb.append(getHashtagsString(cntl.mHashtag));
//                sb.setSpan(sBoldSpan, boldStart, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//                cntl.mTagDisplayView.setText(sb);
//
////                cntl.mTagDisplayView.setOnClickListener(new View.OnClickListener() {
////                    public void onClick(View v) {
////                        Intent intent = new Intent(this, TagStreamActivity.class);
////                        intent.putExtra(TagStreamFragment.EXTRA_QUERY, getHashtagsString("was hash tag"));
////                        startActivity(intent);
////                    }
////                });
//            } else {
//            	cntl.mTagDisplayView.setVisibility(View.GONE);
//            }
//            
//            cntl.mRoomName = "Room";
//            cntl.mRoomId = "Rm 202"; //cursor.getString(SessionsQuery.ROOM_ID);
//
//            // Unregister around setting checked state to avoid triggering
//            // listener since change isn't user generated.
////            cntl.mStarred.setOnCheckedChangeListener(null);
////            cntl.mStarred.setChecked(false);
////            cntl.mStarred.setOnCheckedChangeListener(this);
//
//            final String sessionAbstract = "I can work on the order these show up, it is just a sort on the query.  Note there is a title above in the header between the l/r icons.  Also a title down in the tab host header.  I will do one or the other not both.  Oh, and you have to swipe l/r once to get that top title to sync.";  //cursor.getString(SessionsQuery.ABSTRACT);
//            if (!TextUtils.isEmpty(sessionAbstract)) {
//                UIUtils.setTextMaybeHtml(cntl.mAbstractView, sessionAbstract);
//                cntl.mAbstractView.setVisibility(View.VISIBLE);
//                cntl.mHasSummaryContent = true;
//            } else {
//            	cntl.mAbstractView.setVisibility(View.GONE);
//            }
//
//            final String sessionRequirements = "How shall I lay out this screen??? Suggestions?"; //cursor.getString(SessionsQuery.REQUIREMENTS);
//            if (!TextUtils.isEmpty(sessionRequirements)) {
//                UIUtils.setTextMaybeHtml(cntl.mRequirementsView, sessionRequirements);
//                cntl.mRequirementsBlockView.setVisibility(View.VISIBLE);
//                cntl.mHasSummaryContent = true;
//            } else {
//            	cntl.mRequirementsBlockView.setVisibility(View.GONE);
//            }
//
//            // Show empty message when all data is loaded, and nothing to show
//            if (false && !cntl.mHasSummaryContent) {
//            	cntl.mRootView.findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
//            }
//
//            AnalyticsUtils.getInstance(this).trackPageView("/Sessions/" + cntl.mTitleString);

//            updateLinksTab(cursor);
//            updateNotesTab();
        	updateWorkspaceHeader(cntl.index);

//
//        } finally {
////            cursor.close();
//        }
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
//
//
//    /**
//     * Build and add "notes" tab.
//     */
//    
//    /**
//     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
//     * its label.
//     *
//     * @param textRes
//     * @return View
//     */
////    private View buildIndicator(Ctlr ctlr, int textRes) {
////        final TextView indicator = (TextView) getActivity().getLayoutInflater()
////                .inflate(R.layout.tab_indicator,
////                        (ViewGroup) ctlr.mRootView.findViewById(android.R.id.tabs), false);
////        indicator.setText(textRes);
////        return indicator;
////    }
    
    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label.
     */
    private View buildIndicator(Ctlr ctlr, int textRes) {
        final TextView indicator = (TextView) getLayoutInflater().inflate(R.layout.tab_indicator,
        		ctlr.mTabWidget , false);
        indicator.setText(textRes);
        return indicator;
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
    
    private String getHashtagsString(String mHashtag) {
        if (!TextUtils.isEmpty(mHashtag)) {
            return TagStreamFragment.CONFERENCE_HASHTAG + " #" + mHashtag;
        } else {
            return TagStreamFragment.CONFERENCE_HASHTAG;
        }
    }

    @Override
    public FragmentReplaceInfo onSubstituteFragmentForActivityLaunch(String activityClassName) {
        if (findViewById(R.id.fragment_container_starred_detail) != null) {
            // The layout we currently have has a detail container, we can add fragments there.
            findViewById(android.R.id.empty).setVisibility(View.GONE);
            if (SessionDetailActivity.class.getName().equals(activityClassName)) {
                clearSelectedItems();
                return new FragmentReplaceInfo(
                        SessionDetailFragment.class,
                        "session_detail",
                        R.id.fragment_container_starred_detail);
            } else if (VendorDetailActivity.class.getName().equals(activityClassName)) {
                clearSelectedItems();
                return new FragmentReplaceInfo(
                        VendorDetailFragment.class,
                        "vendor_detail",
                        R.id.fragment_container_starred_detail);
            } else if (VendorDetailActivity.class.getName().equals(activityClassName)) {
                clearSelectedItems();
                return new FragmentReplaceInfo(
                        VendorDetailFragment.class,
                        "vendor_detail",
                        R.id.fragment_container_starred_detail);
            }
        }
        return null;
    }

    private void clearSelectedItems() {
//        if (mProbesFragment != null) {
//            mProbesFragment.clearCheckedPosition();
//        }
//        if (mControllersFragment != null) {
//            mControllersFragment.clearCheckedPosition();
//        }
    }
    
    /**
     * This is a helper class that implements a generic mechanism for
     * associating fragments with the tabs in a tab host.  It relies on a
     * trick.  Normally a tab host has a simple API for supplying a View or
     * Intent that each tab will show.  This is not sufficient for switching
     * between fragments.  So instead we make the content part of the tab host
     * 0dp high (it is not shown) and the TabManager supplies its own dummy
     * view to show as the tab content.  It listens to changes in tabs, and takes
     * care of switch to the correct fragment shown in a separate content area
     * whenever the selected tab changes.
     */
    public static class TabManager implements TabHost.OnTabChangeListener {
        private final FragmentActivity mActivity;
        private final TabHost mTabHost;
        private final int mContainerId;
        private final HashMap<String, TabInfo> mTabs = new HashMap<String, TabInfo>();
        TabInfo mLastTab;

        static final class TabInfo {
            private final String tag;
            private final Class<?> clss;
            private final Bundle args;
            private Fragment fragment;

            TabInfo(String _tag, Class<?> _class, Bundle _args) {
                tag = _tag;
                clss = _class;
                args = _args;
            }
        }

        static class DummyTabFactory implements TabHost.TabContentFactory {
            private final Context mContext;

            public DummyTabFactory(Context context) {
                mContext = context;
            }

            @Override
            public View createTabContent(String tag) {
                View v = new View(mContext);
                v.setMinimumWidth(0);
                v.setMinimumHeight(0);
                return v;
            }
        }

        public TabManager(FragmentActivity activity, TabHost tabHost, int containerId) {
            mActivity = activity;
            mTabHost = tabHost;
            mContainerId = containerId;
            mTabHost.setOnTabChangedListener(this);
        }

        public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {
            tabSpec.setContent(new DummyTabFactory(mActivity));
            String tag = tabSpec.getTag();

            TabInfo info = new TabInfo(tag, clss, args);

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state.  If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            info.fragment = mActivity.getSupportFragmentManager().findFragmentByTag(tag);
            if (info.fragment != null && !info.fragment.isDetached()) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                ft.detach(info.fragment);
                ft.commit();
            }
            
            mTabs.put(tag, info);
            mTabHost.addTab(tabSpec);
        }

        @Override
        public void onTabChanged(String tabId) {
            TabInfo newTab = mTabs.get(tabId);
            if (mLastTab != newTab) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                if (mLastTab != null) {
                    if (mLastTab.fragment != null) {
                        ft.detach(mLastTab.fragment);
                    }
                }
                if (newTab != null) {
                    if (newTab.fragment == null) {
                        newTab.fragment = Fragment.instantiate(mActivity,
                                newTab.clss.getName(), newTab.args);
                        ft.add(mContainerId, newTab.fragment, newTab.tag);
                    } else {
                        ft.attach(newTab.fragment);
                    }
                }

                mLastTab = newTab;
                ft.commit();
                mActivity.getSupportFragmentManager().executePendingTransactions();
            }
        }
    }

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		
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

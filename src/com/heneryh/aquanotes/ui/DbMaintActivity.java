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

import java.util.HashMap;

import com.heneryh.aquanotes.R;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Probes;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.ui.phone.SessionDetailActivity;
import com.heneryh.aquanotes.ui.phone.VendorDetailActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

/**
 * An activity that shows the raw database table contents. This activity can be
 * either single or multi-pane, depending on the device configuration. We want the multi-pane
 * support that {@link BaseMultiPaneActivity} offers, so we inherit from it instead of
 * {@link BaseSinglePaneActivity}.
 */
public class DbMaintActivity extends BaseMultiPaneActivity {

    public static final String TAG_CONTROLLERS = "controllers";
    public static final String TAG_PROBES = "probes";
    public static final String TAG_DATA = "data";

     TabHost mTabHost;
     TabWidget mTabWidget;
     TabManager mTabManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivityHelper().setupActionBar(getTitle(), 0);

        setContentView(R.layout.activity_db_maint);

        mTabHost = (TabHost)findViewById(android.R.id.tabhost);
        mTabWidget = (TabWidget) findViewById(android.R.id.tabs);
        mTabHost.setup();
        mTabManager = new TabManager(this, mTabHost, R.id.realtabcontent);

        setupControllersTab();
        setupProbesTab();
        setupDataTab();
        
        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
        } else {
           mTabHost.setCurrentTabByTag("controllers");
        }
   }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupSubActivity();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("tab", mTabHost.getCurrentTabTag());
    }

    /**
     * Build and add "Controllers" tab.
     */
    private void setupControllersTab() {
        // TODO: this is very inefficient and messy, clean it up
//        FrameLayout fragmentContainer = new FrameLayout(this);
//        fragmentContainer.setId(R.id.fragment_controllers);
//        fragmentContainer.setLayoutParams(
//                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
//                        ViewGroup.LayoutParams.FILL_PARENT));
//        ((ViewGroup) findViewById(android.R.id.tabcontent)).addView(fragmentContainer);
//
//        final Intent intent = new Intent(Intent.ACTION_VIEW, Controllers.CONTENT_URI);
//
//        final FragmentManager fm = getSupportFragmentManager();
//
//        mControllersFragment = (DbMaintControllersFragment) fm.findFragmentByTag("controllers");
//        if (mControllersFragment == null) {
//            mControllersFragment = new DbMaintControllersFragment();
//            mControllersFragment.setArguments(intentToFragmentArguments(intent));
//            fm.beginTransaction()
//                    .add(R.id.fragment_controllers, mControllersFragment, "controllers")
//                    .commit();
//        }
//
//        // Vendors content comes from reused activity
//        mTabHost.addTab(mTabHost.newTabSpec(TAG_CONTROLLERS)
//                .setIndicator(buildIndicator(R.string.db_maint_controllers))
//                .setContent(R.id.fragment_controllers));
        mTabManager.addTab(mTabHost.newTabSpec(TAG_CONTROLLERS)
        		.setIndicator(buildIndicator(R.string.db_maint_controllers)),
                DbMaintControllersFragment.class, null);
    }

    /**
     * Build and add "probes" tab.
     */
    private void setupProbesTab() {
        // TODO: this is very inefficient and messy, clean it up
//        FrameLayout fragmentContainer = new FrameLayout(this);
//        fragmentContainer.setId(R.id.fragment_probes);
//        fragmentContainer.setLayoutParams(
//                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
//                        ViewGroup.LayoutParams.FILL_PARENT));
//        ((ViewGroup) findViewById(android.R.id.tabcontent)).addView(fragmentContainer);
//
//        final Intent intent = new Intent(Intent.ACTION_VIEW, Probes.CONTENT_URI);
//
//        final FragmentManager fm = getSupportFragmentManager();
//        mProbesFragment = (DbMaintProbesFragment) fm.findFragmentByTag("probes");
//        if (mProbesFragment == null) {
//            mProbesFragment = new DbMaintProbesFragment();
//            mProbesFragment.setArguments(intentToFragmentArguments(intent));
//            fm.beginTransaction()
//                    .add(R.id.fragment_probes, mProbesFragment, "probes")
//                    .commit();
//        }
//
//        // Sessions content comes from reused activity
//        mTabHost.addTab(mTabHost.newTabSpec(TAG_PROBES)
//                .setIndicator(buildIndicator(R.string.db_maint_probes))
//                .setContent(R.id.fragment_probes));
        mTabManager.addTab(mTabHost.newTabSpec(TAG_PROBES)
        		.setIndicator(buildIndicator(R.string.db_maint_probes)),
                DbMaintProbesFragment.class, null);
    }

    /**
     * Build and add "data" tab.
     */
    private void setupDataTab() {
        // TODO: this is very inefficient and messy, clean it up
//        FrameLayout fragmentContainer = new FrameLayout(this);
//        fragmentContainer.setId(R.id.fragment_data);
//        fragmentContainer.setLayoutParams(
//                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
//                        ViewGroup.LayoutParams.FILL_PARENT));
//        ((ViewGroup) findViewById(android.R.id.tabcontent)).addView(fragmentContainer);
//
//        final Intent intent = new Intent(Intent.ACTION_VIEW, Data.CONTENT_URI);
//
//        final FragmentManager fm = getSupportFragmentManager();
//        mDataFragment = (DbMaintDataFragment) fm.findFragmentByTag("data");
//        if (mDataFragment == null) {
//        	mDataFragment = new DbMaintDataFragment();
//        	mDataFragment.setArguments(intentToFragmentArguments(intent));
//            fm.beginTransaction()
//                    .add(R.id.fragment_data, mDataFragment, "data")
//                    .commit();
//        }
//
//        // Sessions content comes from reused activity
//        mTabHost.addTab(mTabHost.newTabSpec(TAG_DATA)
//                .setIndicator(buildIndicator(R.string.db_maint_data))
//                .setContent(R.id.fragment_data));
        mTabManager.addTab(mTabHost.newTabSpec(TAG_DATA)
        		.setIndicator(buildIndicator(R.string.db_maint_data)),
                DbMaintDataFragment.class, null);
    }

    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label.
     */
    private View buildIndicator(int textRes) {
        final TextView indicator = (TextView) getLayoutInflater().inflate(R.layout.tab_indicator,
        		mTabWidget , false);
        indicator.setText(textRes);
        return indicator;
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

}

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
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Probes;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Sessions;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Vendors;
import com.heneryh.aquanotes.ui.phone.SessionDetailActivity;
import com.heneryh.aquanotes.ui.phone.VendorDetailActivity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

/**
 * An activity that shows the user's starred sessions and sandbox companies. This activity can be
 * either single or multi-pane, depending on the device configuration. We want the multi-pane
 * support that {@link BaseMultiPaneActivity} offers, so we inherit from it instead of
 * {@link BaseSinglePaneActivity}.
 */
public class DbMaintActivity extends BaseMultiPaneActivity {

    public static final String TAG_CONTROLLERS = "controllers";
    public static final String TAG_PROBES = "probes";
    public static final String TAG_DATA = "data";

    private TabHost mTabHost;
    private TabWidget mTabWidget;

    private DbMaintControllersFragment mControllersFragment;
    private DbMaintProbesFragment mProbesFragment;
    private DbMaintDataFragment mDataFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_db_maint);
        getActivityHelper().setupActionBar(getTitle(), 0);

        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabWidget = (TabWidget) findViewById(android.R.id.tabs);
        mTabHost.setup();

        setupControllersTab();
        setupProbesTab();
        setupDataTab();
   }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupSubActivity();

        ViewGroup detailContainer = (ViewGroup) findViewById(R.id.fragment_container_starred_detail);
        if (detailContainer != null && detailContainer.getChildCount() > 1) {
            findViewById(android.R.id.empty).setVisibility(View.GONE);
        }
    }

    /**
     * Build and add "Controllers" tab.
     */
    private void setupControllersTab() {
        // TODO: this is very inefficient and messy, clean it up
        FrameLayout fragmentContainer = new FrameLayout(this);
        fragmentContainer.setId(R.id.fragment_controllers);
        fragmentContainer.setLayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.FILL_PARENT));
        ((ViewGroup) findViewById(android.R.id.tabcontent)).addView(fragmentContainer);

        final Intent intent = new Intent(Intent.ACTION_VIEW, Controllers.CONTENT_URI);

        final FragmentManager fm = getSupportFragmentManager();

        mControllersFragment = (DbMaintControllersFragment) fm.findFragmentByTag("controllers");
        if (mControllersFragment == null) {
            mControllersFragment = new DbMaintControllersFragment();
            mControllersFragment.setArguments(intentToFragmentArguments(intent));
            fm.beginTransaction()
                    .add(R.id.fragment_controllers, mControllersFragment, "controllers")
                    .commit();
        }

        // Vendors content comes from reused activity
        mTabHost.addTab(mTabHost.newTabSpec(TAG_CONTROLLERS)
                .setIndicator(buildIndicator(R.string.db_maint_controllers))
                .setContent(R.id.fragment_controllers));
    }

    /**
     * Build and add "sessions" tab.
     */
    private void setupProbesTab() {
        // TODO: this is very inefficient and messy, clean it up
        FrameLayout fragmentContainer = new FrameLayout(this);
        fragmentContainer.setId(R.id.fragment_probes);
        fragmentContainer.setLayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.FILL_PARENT));
        ((ViewGroup) findViewById(android.R.id.tabcontent)).addView(fragmentContainer);

        final Intent intent = new Intent(Intent.ACTION_VIEW, Probes.CONTENT_URI);

        final FragmentManager fm = getSupportFragmentManager();
        mProbesFragment = (DbMaintProbesFragment) fm.findFragmentByTag("probes");
        if (mProbesFragment == null) {
            mProbesFragment = new DbMaintProbesFragment();
            mProbesFragment.setArguments(intentToFragmentArguments(intent));
            fm.beginTransaction()
                    .add(R.id.fragment_probes, mProbesFragment, "probes")
                    .commit();
        }

        // Sessions content comes from reused activity
        mTabHost.addTab(mTabHost.newTabSpec(TAG_PROBES)
                .setIndicator(buildIndicator(R.string.db_maint_probes))
                .setContent(R.id.fragment_probes));
    }

    /**
     * Build and add "sessions" tab.
     */
    private void setupDataTab() {
        // TODO: this is very inefficient and messy, clean it up
        FrameLayout fragmentContainer = new FrameLayout(this);
        fragmentContainer.setId(R.id.fragment_data);
        fragmentContainer.setLayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.FILL_PARENT));
        ((ViewGroup) findViewById(android.R.id.tabcontent)).addView(fragmentContainer);

        final Intent intent = new Intent(Intent.ACTION_VIEW, Data.CONTENT_URI);

        final FragmentManager fm = getSupportFragmentManager();
        mDataFragment = (DbMaintDataFragment) fm.findFragmentByTag("data");
        if (mDataFragment == null) {
        	mDataFragment = new DbMaintDataFragment();
        	mDataFragment.setArguments(intentToFragmentArguments(intent));
            fm.beginTransaction()
                    .add(R.id.fragment_data, mDataFragment, "data")
                    .commit();
        }

        // Sessions content comes from reused activity
        mTabHost.addTab(mTabHost.newTabSpec(TAG_DATA)
                .setIndicator(buildIndicator(R.string.db_maint_data))
                .setContent(R.id.fragment_data));
    }

    /**
     * Build a {@link View} to be used as a tab indicator, setting the requested string resource as
     * its label.
     */
    private View buildIndicator(int textRes) {
        final TextView indicator = (TextView) getLayoutInflater().inflate(R.layout.tab_indicator,
                mTabWidget, false);
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
        if (mProbesFragment != null) {
            mProbesFragment.clearCheckedPosition();
        }
        if (mControllersFragment != null) {
            mControllersFragment.clearCheckedPosition();
        }
    }
}

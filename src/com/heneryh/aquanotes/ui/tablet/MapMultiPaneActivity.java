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

package com.heneryh.aquanotes.ui.tablet;

import com.heneryh.aquanotes.R;
import com.heneryh.aquanotes.ui.BaseMultiPaneActivity;
import com.heneryh.aquanotes.ui.MapFragment;
import com.heneryh.aquanotes.ui.SessionDetailFragment;
import com.heneryh.aquanotes.ui.DbMaintProbesFragment;
import com.heneryh.aquanotes.ui.VendorDetailFragment;
import com.heneryh.aquanotes.ui.DbMaintControllersFragment;
import com.heneryh.aquanotes.ui.phone.SessionDetailActivity;
import com.heneryh.aquanotes.ui.phone.SessionsActivity;
import com.heneryh.aquanotes.ui.phone.VendorDetailActivity;
import com.heneryh.aquanotes.ui.phone.DBMaintActivity;

import android.app.FragmentBreadCrumbs;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;

/**
 * A multi-pane activity, where the primary navigation pane is a {@link MapFragment}, that shows
 * {@link DbMaintProbesFragment}, {@link SessionDetailFragment}, {@link DbMaintControllersFragment}, and
 * {@link VendorDetailFragment} as popups.
 *
 * This activity requires API level 11 or greater because of its use of {@link FragmentBreadCrumbs}.
 */
public class MapMultiPaneActivity extends BaseMultiPaneActivity implements
        View.OnClickListener, FragmentManager.OnBackStackChangedListener {

    private static final int POPUP_TYPE_SESSIONS = 1;
    private static final int POPUP_TYPE_VENDORS = 2;

    private int mPopupType = -1;
    private boolean mPauseBackStackWatcher = false;

    private FragmentManager mFragmentManager;
    private FragmentBreadCrumbs mFragmentBreadCrumbs;

    private MapFragment mMapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mFragmentManager = getSupportFragmentManager();
        mFragmentManager.addOnBackStackChangedListener(this);

        mFragmentBreadCrumbs = (FragmentBreadCrumbs) findViewById(R.id.breadcrumbs);
        mFragmentBreadCrumbs.setActivity(this);

        mMapFragment = (MapFragment) mFragmentManager.findFragmentByTag("map");
        if (mMapFragment == null) {
            mMapFragment = new MapFragment();
            mMapFragment.setArguments(intentToFragmentArguments(getIntent()));

            mFragmentManager.beginTransaction()
                    .add(R.id.fragment_container_map, mMapFragment, "map")
                    .commit();
        }

        findViewById(R.id.close_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                clearBackStack(getSupportFragmentManager());
            }
        });

        updateBreadCrumb();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupSubActivity();
    }

    @Override
    public FragmentReplaceInfo onSubstituteFragmentForActivityLaunch(String activityClassName) {
        if (SessionsActivity.class.getName().equals(activityClassName)) {
            clearBackStack(getSupportFragmentManager());
            mPopupType = POPUP_TYPE_SESSIONS;
            showHideDetailAndPan(true);
            return new FragmentReplaceInfo(
                    DbMaintProbesFragment.class,
                    "sessions",
                    R.id.fragment_container_map_detail);
        } else if (SessionDetailActivity.class.getName().equals(activityClassName)) {
            mPopupType = POPUP_TYPE_SESSIONS;
            showHideDetailAndPan(true);
            return new FragmentReplaceInfo(
                    SessionDetailFragment.class,
                    "session_detail",
                    R.id.fragment_container_map_detail);
        } else if (DBMaintActivity.class.getName().equals(activityClassName)) {
            clearBackStack(getSupportFragmentManager());
            mPopupType = POPUP_TYPE_VENDORS;
            showHideDetailAndPan(true);
            return new FragmentReplaceInfo(
                    DbMaintControllersFragment.class,
                    "vendors",
                    R.id.fragment_container_map_detail);
        } else if (VendorDetailActivity.class.getName().equals(activityClassName)) {
            mPopupType = POPUP_TYPE_VENDORS;
            showHideDetailAndPan(true);
            return new FragmentReplaceInfo(
                    VendorDetailFragment.class,
                    "vendor_detail",
                    R.id.fragment_container_map_detail);
        }
        return null;
    }

    @Override
    protected void onBeforeCommitReplaceFragment(FragmentManager fm, FragmentTransaction ft,
            Fragment fragment) {
        super.onBeforeCommitReplaceFragment(fm, ft, fragment);
        if (fragment instanceof DbMaintProbesFragment || fragment instanceof DbMaintControllersFragment) {
            mPauseBackStackWatcher = true;
            clearBackStack(fm);
            mPauseBackStackWatcher = false;
        }
        ft.addToBackStack(null);
        updateBreadCrumb();
    }

    /**
     * Handler for the breadcrumb parent.
     */
    public void onClick(View view) {
        mFragmentManager.popBackStack();
    }

    private void clearBackStack(FragmentManager fm) {
        while (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate();
        }
    }

    public void onBackStackChanged() {
        if (mPauseBackStackWatcher) {
            return;
        }

        if (mFragmentManager.getBackStackEntryCount() == 0) {
            showHideDetailAndPan(false);
        }
        updateBreadCrumb();
    }

    private void showHideDetailAndPan(boolean show) {
        View detailPopup = findViewById(R.id.map_detail_popup);
        if (show != (detailPopup.getVisibility() == View.VISIBLE)) {
            detailPopup.setVisibility(show ? View.VISIBLE : View.GONE);
            mMapFragment.panLeft(show ? 0.25f : -0.25f);
        }
    }

    public void updateBreadCrumb() {
        final String title = (mPopupType == POPUP_TYPE_SESSIONS)
                ? getString(R.string.title_sessions)
                : getString(R.string.title_vendors);
        final String detailTitle = (mPopupType == POPUP_TYPE_SESSIONS)
                ? getString(R.string.title_session_detail)
                : getString(R.string.title_vendor_detail);

        if (mFragmentManager.getBackStackEntryCount() >= 2) {
            mFragmentBreadCrumbs.setParentTitle(title, title, this);
            mFragmentBreadCrumbs.setTitle(detailTitle, detailTitle);
        } else {
            mFragmentBreadCrumbs.setParentTitle(null, null, null);
            mFragmentBreadCrumbs.setTitle(title, title);
        }
    }
}

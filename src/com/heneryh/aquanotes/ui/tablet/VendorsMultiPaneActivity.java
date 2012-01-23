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
import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.ui.BaseMultiPaneActivity;
import com.heneryh.aquanotes.ui.TracksFragment;
import com.heneryh.aquanotes.ui.VendorDetailFragment;
import com.heneryh.aquanotes.ui.DbMaintControllersFragment;
import com.heneryh.aquanotes.ui.phone.VendorDetailActivity;
import com.heneryh.aquanotes.ui.phone.DBMaintActivity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.ViewGroup;

/**
 * A multi-pane activity, consisting of a {@link TracksDropdownFragment}, a
 * {@link DbMaintControllersFragment}, and {@link VendorDetailFragment}. This activity is very similar in
 * function to {@link SessionsMultiPaneActivity}.
 *
 * This activity requires API level 11 or greater because {@link TracksDropdownFragment} requires
 * API level 11.
 */
public class VendorsMultiPaneActivity extends BaseMultiPaneActivity {

    private TracksDropdownFragment mTracksDropdownFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(
                R.layout.activity_vendors);

        Intent intent = new Intent();
        intent.setData(AquaNotesDbContract.Tracks.CONTENT_URI);
        intent.putExtra(TracksFragment.EXTRA_NEXT_TYPE, TracksFragment.NEXT_TYPE_VENDORS);

        final FragmentManager fm = getSupportFragmentManager();
        mTracksDropdownFragment = (TracksDropdownFragment) fm.findFragmentById(
                R.id.fragment_tracks_dropdown);
        mTracksDropdownFragment.reloadFromArguments(intentToFragmentArguments(intent));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupSubActivity();

        ViewGroup detailContainer = (ViewGroup)
                findViewById(R.id.fragment_container_vendor_detail);
        if (detailContainer != null && detailContainer.getChildCount() > 0) {
            findViewById(R.id.fragment_container_vendor_detail).setBackgroundColor(0xffffffff);
        }
    }

    @Override
    public FragmentReplaceInfo onSubstituteFragmentForActivityLaunch(String activityClassName) {
        if (DBMaintActivity.class.getName().equals(activityClassName)) {
            return new FragmentReplaceInfo(
                    DbMaintControllersFragment.class,
                    "vendors",
                    R.id.fragment_container_vendors);
        } else if (VendorDetailActivity.class.getName().equals(activityClassName)) {
            findViewById(R.id.fragment_container_vendor_detail).setBackgroundColor(
                    0xffffffff);
            return new FragmentReplaceInfo(
                    VendorDetailFragment.class,
                    "vendor_detail",
                    R.id.fragment_container_vendor_detail);
        }
        return null;
    }
}

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
import com.heneryh.aquanotes.configure.ConfigurePrefs;
import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.service.SyncService;
import com.heneryh.aquanotes.util.AnalyticsUtils;
import com.heneryh.aquanotes.util.DetachableResultReceiver;
import com.heneryh.aquanotes.util.EulaHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Front-door {@link Activity} that displays high-level features the schedule application offers to
 * users. Depending on whether the device is a phone or an Android 3.0+ tablet, different layouts
 * will be used. For example, on a phone, the primary content is a {@link DashboardFragment},
 * whereas on a tablet, both a {@link DashboardFragment} and a {@link TagStreamFragment} are
 * displayed.
 */
public class HomeActivity extends BaseActivity {
    private static final String TAG = "HomeActivity";

    private TagStreamFragment mTagStreamFragment;
    private SyncStatusUpdaterFragment mSyncStatusUpdaterFragment;
    private Uri mControllerUri;
	ContentResolver dbResolverHomeAct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int mControllerId = 0;

        if (!EulaHelper.hasAcceptedEula(this)) {
            EulaHelper.showEula(false, this);
        }
        
        dbResolverHomeAct = getContentResolver();


        AnalyticsUtils.getInstance(this).trackPageView("/Home");

    	/**
    	 * This home screen activity can be launched via several actions.  If it comes from an intent though a 
    	 * widget press, then the controller ID will be in an extra of the intent.
    	 * 
    	 * If could also be started from the main app drawer in which case we can't be sure which controller to use.
    	 * We need to figure out which controller to use, if there are more than one active controllers, 
    	 * present a list of them to connect to.
    	 * 
    	 * If there is only one, then default just use that one.
    	 * 
    	 * If there are none, present the configuration screen and add one.
    	 */
        final Intent intent = getIntent();
        Uri metaData = intent.getData();
        if (metaData == null) {
        	
        	/**
        	 * If there is no controller ID in the intent extra data then we must have been launched from the 
        	 * app-drawer with no controller passed in from intent.  Since we're not coming from a widget we have
        	 * a couple of options to check out which controller to use
        	 */

            Log.d(TAG, "Launching app from app-drawer.");

            // Poll the database for list of all registered controllers
     		Cursor cursor = null;
    		try {
    			Uri controllersQueryUri = Controllers.buildQueryControllersUri();
    			cursor = dbResolverHomeAct.query(controllersQueryUri, ControllersQuery.PROJECTION, null, null, null);
    			if(cursor.getCount()==0) {
    				/**
    				 * If there are no controllers in the db then we'll need to kickstart the configure screen and
    				 * wait for a response.
    				 */
    	            Log.d(TAG, "Launching app from app-drawer - zero controllers in the db, using 999.");

    				// Launch Preference activity
    				Intent i = new Intent(this, ConfigurePrefs.class);
    				mControllerId = 999; // use the special case of widget=999 , this is not a good solution
    				i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mControllerId);
    				startActivity(i);
    				/** 
    				 * note that the config screen will start the service too, the service start below will likely be started
    				 * prior to the config being completed.
    				 */
    			} else if (cursor.getCount()==1) {
    				/**
    				 * If there is only one controller in the db, then just assume it is that one, push it onto the
    				 * update queue and run with it.
    				 */
    				cursor.moveToFirst();
    				mControllerId = cursor.getInt(ControllersQuery._ID); 
//    				SyncService.requestUpdate(mControllerId);
    	            Log.d(TAG, "Launching app from app-drawer - one controller in the db, using " + mControllerId);
    			} else {
    				final CharSequence[] items = {"Red", "Green", "Blue"};

    				AlertDialog.Builder builder = new AlertDialog.Builder(this);
    				builder.setTitle("Pick a color");
    				builder.setItems(items, new DialogInterface.OnClickListener() {
    				    public void onClick(DialogInterface dialog, int item) {
    				        Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
    				    }
    				});
    				AlertDialog alert = builder.create();
    				// need to choose which one to use!!
//    				if (cursor != null && cursor.moveToFirst()) {
//    				title = cursor.getString(COL_CNT_TITLE);
//    				username = cursor.getString(COL_CNT_USERNAME);
//    				password = cursor.getString(COL_CNT_PASSWORD);
//    				apexBaseURL = cursor.getString(COL_CNT_URL);
//    				interval = cursor.getInt(COL_CNT_INTERVAL);
//    				prune_age = 14; //cursor.getInt(COL_CNT_PRUNE);				
//    			}
    			}
    		} catch (SQLException e) {
    			Log.e(TAG, "onCreate: getting controller facts.", e);	
    			// need a little more here!
    		} finally {
    			if (cursor != null) {
    				cursor.close();
    			}
    		}
        } else {
        	/**
        	 * If there is controller ID in the intent extra data then we must have been launched from the 
        	 * a widget so pull the referenced controller ID from the intent, push it onto the update queue and run with it
        	 */

            Log.d(TAG, "Launched from a widget and pulling extra intent data for controller: " + metaData);
            
            // the stuff below pulls the title which we should do in other cases too!  Fix this!
            Uri controllerUri = metaData;
            Cursor cursor = null;
             try {
                cursor = dbResolverHomeAct.query(controllerUri, ControllersQuery.PROJECTION, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String titleString = cursor.getString(ControllersQuery.TITLE);
                    mControllerId = cursor.getInt(ControllersQuery._ID);
                    Log.d(TAG, "Controller title: " + titleString);
//    				SyncService.requestUpdate(mControllerId);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        setContentView(R.layout.activity_home);
        getActivityHelper().setupActionBar(null, 0);

        FragmentManager fm = getSupportFragmentManager();

        mTagStreamFragment = (TagStreamFragment) fm.findFragmentById(R.id.fragment_tag_stream);

        mSyncStatusUpdaterFragment = (SyncStatusUpdaterFragment) fm
                .findFragmentByTag(SyncStatusUpdaterFragment.TAG);
        if (mSyncStatusUpdaterFragment == null) {
            mSyncStatusUpdaterFragment = new SyncStatusUpdaterFragment();
            fm.beginTransaction().add(mSyncStatusUpdaterFragment,
                    SyncStatusUpdaterFragment.TAG).commit();

            triggerRefresh();
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getActivityHelper().setupHomeActivity();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.refresh_menu_items, menu);
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_refresh) {
            triggerRefresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void triggerRefresh() {
        final Intent intent = new Intent(Intent.ACTION_SYNC, null, this, SyncService.class);
//        intent.putExtra(SyncService.EXTRA_STATUS_RECEIVER, mSyncStatusUpdaterFragment.mReceiver);
//        startService(intent);

        if (mTagStreamFragment != null) {
            mTagStreamFragment.refresh();
        }
    }

    private void updateRefreshStatus(boolean refreshing) {
        getActivityHelper().setRefreshActionButtonCompatState(refreshing);
    }

    /**
     * A non-UI fragment, retained across configuration changes, that updates its activity's UI
     * when sync status changes.
     */
    public static class SyncStatusUpdaterFragment extends Fragment
            implements DetachableResultReceiver.Receiver {
        public static final String TAG = SyncStatusUpdaterFragment.class.getName();

        private boolean mSyncing = false;
        private DetachableResultReceiver mReceiver;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
            mReceiver = new DetachableResultReceiver(new Handler());
            mReceiver.setReceiver(this);
        }

        /** {@inheritDoc} */
        public void onReceiveResult(int resultCode, Bundle resultData) {
            HomeActivity activity = (HomeActivity) getActivity();
            if (activity == null) {
                return;
            }

            switch (resultCode) {
                case SyncService.STATUS_RUNNING: {
                    mSyncing = true;
                    break;
                }
                case SyncService.STATUS_FINISHED: {
                    mSyncing = false;
                    break;
                }
                case SyncService.STATUS_ERROR: {
                    // Error happened down in SyncService, show as toast.
                    mSyncing = false;
                    final String errorText = getString(R.string.toast_sync_error, resultData
                            .getString(Intent.EXTRA_TEXT));
                    Toast.makeText(activity, errorText, Toast.LENGTH_LONG).show();
                    break;
                }
            }

            activity.updateRefreshStatus(mSyncing);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            ((HomeActivity) getActivity()).updateRefreshStatus(mSyncing);
        }
    }

    private interface ControllersQuery {
        String[] PROJECTION = {
//              String CONTROLLER_ID = "_id";
//              String TITLE = "title";
//              String WAN_URL = "wan_url";
//              String WIFI_URL = "wifi_url";
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
                AquaNotesDbContract.Controllers.WIFI_URL,
                AquaNotesDbContract.Controllers.WIFI_SSID,
                AquaNotesDbContract.Controllers.USER,
                AquaNotesDbContract.Controllers.PW,
                AquaNotesDbContract.Controllers.LAST_UPDATED,
                AquaNotesDbContract.Controllers.UPDATE_INTERVAL,
                AquaNotesDbContract.Controllers.DB_SAVE_DAYS,
                AquaNotesDbContract.Controllers.CONTROLLER_TYPE,
        };
        
        int _ID = 0;
        int TITLE = 1;
        int WAN_URL = 2;
        int WIFI_URL = 3;
        int WIFI_SSID = 4;
        int USER = 5;
        int PW = 6;
        int LAST_UPDATED = 7;
        int UPDATE_INTERVAL = 8;
        int DB_SAVE_DAYS = 9;
        int CONTROLLER_TYPE = 10;
    }
    }

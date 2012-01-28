/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.heneryh.aquanotes.ui.widget;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import android.widget.RemoteViews;

import java.sql.Date;
import java.text.SimpleDateFormat;

import com.heneryh.aquanotes.R;
import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.service.SyncService;


/**
 * Define a simple widget that shows the Apex status. To build
 * an update we spawn a background {@link Service} to perform the API queries.
 */
public class Widget1x1 extends AppWidgetProvider {
	private static final String TAG = "ApexWidget1x1";


	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// If no specific widgets requested, collect list of all
		// BTW, how do you get an update for just one widget?
		// I think the answer is exemplified in the configure.save action.
		if (appWidgetIds == null) {
			appWidgetIds = appWidgetManager.getAppWidgetIds(
					new ComponentName(context, Widget1x1.class));
		}

		// Request update for these widgets and launch updater service
		SyncService.requestUpdate(appWidgetIds);
		context.startService(new Intent(context, SyncService.class));
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		ContentResolver resolver = context.getContentResolver();
		for (int appWidgetId : appWidgetIds) {
			Log.d(TAG, "Deleting appWidgetId=" + appWidgetId);
			Uri controllerUri = Controllers.buildQueryControllerXUri(appWidgetId);
			resolver.delete(controllerUri, null, null);
		}
	}

	/**
	 * Build an update for the given medium widget. Should only be called from a
	 * service or thread to prevent ANR during database queries.
	 */
	public static RemoteViews buildUpdate(Context context, Uri controllerUri) {
		Log.d(TAG, "Building Apex 1x1 widget update");

		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_1x1);

		boolean forecastFilled = false;

//		ContentResolver resolver = context.getContentResolver();
//		Resources res = context.getResources();

//		Cursor cursor = null;

		// Pull out controller title and other data for this instance
//		try {
//			cursor = resolver.query(controllerUri, PROJECTION_CONTROLLERS, null, null, null);
//			if (cursor != null && cursor.moveToFirst()) {
//
//				/////////////////////////
//				// We have three parts to the widget
//				// 1) Title    2)Time   
//				// 3) StatusText
//
//				// Controller friendly title
//				String title = cursor.getString(COL_TITLE);
//				views.setTextViewText(R.id.widget_apex_title, title);
//
//				// Timestamp
//				Date timestamp = new Date(cursor.getLong(COL_LAST_UPDATED));
//				//SimpleDateFormat formatter = new SimpleDateFormat("M/d h:mm a");
//				SimpleDateFormat formatter = new SimpleDateFormat("M/d/yy h:mm a");
//				String timestampS = formatter.format(timestamp);
//				views.setTextViewText(R.id.widget_apex_time,timestampS);
//
//			} else {
//				Log.e(TAG, "Didn't find a database entry for this controller.");
//			}
//		} catch (SQLException e) {
//			Log.e(TAG, "building widget screen (title & time) from database.", e);	
//		} finally {
//			if (cursor != null) {
//				cursor.close();
//			}
//		}


		// Fetch the XML status from the device
//		String statusText = null;
//		statusText = Apex.getShortXMLStatus(context, controllerUri);
//		views.setTextViewText(R.id.widget_probe_values, statusText.trim());
		
//		String title = "test1";
//		views.setTextViewText(R.id.widget_apex_title, title);
//		String timestampS = "test2";
//		views.setTextViewText(R.id.widget_apex_time,timestampS);
//		String statusText = "test3";
//		views.setTextViewText(R.id.widget_probe_values, statusText.trim());

		forecastFilled = true; // should make sure we have good data first. ERROR HANDLING!

		/////////////////////////

		// Find the forecast nearest now and build update using it
		//        try {
		//            Uri forecastAtUri = Uri.withAppendedPath(controllerUri, Controllers.TWIG_PROBExxx);
		//            Uri forecastAtNowUri = Uri.withAppendedPath(forecastAtUri,
		//                    Long.toString(System.currentTimeMillis()));
		//            cursor = resolver.query(forecastAtNowUri, PROJECTION_FORECASTS, null, null, null);
		//            if (cursor != null && cursor.moveToFirst()) {
		//
		//                String conditions = cursor.getString(COL_CONDITIONS);
		//                int iconResource = ForecastUtils.getIconForForecast(conditions, daytime);
		//                int tempHigh = cursor.getInt(COL_TEMP_HIGH);
		//                int tempLow = cursor.getInt(COL_TEMP_LOW);
		//
		//                views.setTextViewText(R.id.conditions, conditions);
		//                views.setImageViewResource(R.id.icon, iconResource);
		//
		//                if (tempHigh == Integer.MIN_VALUE || tempLow == Integer.MIN_VALUE) {
		//                    views.setViewVisibility(R.id.high, View.GONE);
		//                    views.setViewVisibility(R.id.low, View.GONE);
		//                } else {
		//                    views.setViewVisibility(R.id.high, View.VISIBLE);
		//                    views.setViewVisibility(R.id.low, View.VISIBLE);
		//                    views.setTextViewText(R.id.high,
		//                            ForecastUtils.formatTemp(res, tempHigh, tempUnits));
		//                    views.setTextViewText(R.id.low,
		//                            ForecastUtils.formatTemp(res, tempLow, tempUnits));
		//                }
		//
//		forecastFilled = true;
		//            }
		//        } finally {
		//            if (cursor != null) {
		//                cursor.close();
		//            }
		//        }

		// If not filled correctly, show error message and hide other fields
//		if (!forecastFilled) {
//			views = new RemoteViews(context.getPackageName(), R.layout.widget_2x1_initial);
//			//views.setTextViewText(R.id.loading, res.getString(R.string.widget_error));
//			Log.e(TAG, "Error filling the widget data, using the error template.");
//		}

//		// Connect click intent to launch details dialog
//		Intent detailIntent = new Intent(context, ApexActivity.class);
//		detailIntent.setData(controllerUri);
//		PendingIntent pending = PendingIntent.getActivity(context, 0, detailIntent, 0);
//		views.setOnClickPendingIntent(R.id.widget_1x1, pending);

		return views;
	}
	
	/**
	 * Projection and column definitions for the controller table.  Note that the widget_id is being
	 * used as the controller_id in the database.  This aids in looking up data for "this" controller
	 * and assumes a 1:1 relationship between widgets and controllers.
	 */
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

	// Probes database fields that we are interested in here.  Actually both probes and outlets are in this
	// same database with a field that distinguishes whether probe or outlet.

}
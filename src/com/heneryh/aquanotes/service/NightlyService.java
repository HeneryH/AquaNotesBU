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

package com.heneryh.aquanotes.service;

import com.heneryh.aquanotes.R;
import com.heneryh.aquanotes.configure.ConfigurePrefs;
import com.heneryh.aquanotes.io.ApexExecutor;
import com.heneryh.aquanotes.io.ApexStateXMLParser;
import com.heneryh.aquanotes.io.LocalBlocksHandler;
import com.heneryh.aquanotes.io.LocalExecutor;
import com.heneryh.aquanotes.io.LocalRoomsHandler;
import com.heneryh.aquanotes.io.LocalSearchSuggestHandler;
import com.heneryh.aquanotes.io.LocalSessionsHandler;
import com.heneryh.aquanotes.io.LocalTracksHandler;
import com.heneryh.aquanotes.io.NewXmlHandler.HandlerException;
import com.heneryh.aquanotes.io.RemoteExecutor;
import com.heneryh.aquanotes.io.RemoteSessionsHandler;
import com.heneryh.aquanotes.io.RemoteSpeakersHandler;
import com.heneryh.aquanotes.io.RemoteVendorsHandler;
import com.heneryh.aquanotes.io.RemoteWorksheetsHandler;
import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.provider.AquaNotesDbProvider;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;
import org.xml.sax.helpers.DefaultHandler;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.GZIPInputStream;

/**
 * Background {@link Service} that does some nightly cleanup
 */
public class NightlyService extends IntentService {
    private static final String TAG = "NightlyService";



	/**
	 * Flag if there is an update thread already running. We only launch a new
	 * thread if one isn't already running.
	 */
	private static boolean sThreadRunning = false;

	private static final int SECOND_IN_MILLIS = (int) DateUtils.SECOND_IN_MILLIS;


	/**
	 * There is an embedded http client helper below
	 */
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String ENCODING_GZIP = "gzip";

	private ApexExecutor mRemoteExecutor;

	private ContentResolver dbResolverNightlySrvc;

	Context mNightlyServiceContext;

	/**
	 * Main service methods
	 */
    public NightlyService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();

		/**
		 * helper class for defaultHttpClient seen below
		 */
        final HttpClient httpClient = getHttpClient(this);

		/**
		 * Interface to the database which is passed into the remoteExecutor.  Is there an advantage to
		 * having a centralized one rather than each getting there own???  Might want to look at this more.
		 * Seems like the answer is that you need the context to get the resolver
		 */
		dbResolverNightlySrvc = getContentResolver();


        /**
		 * Create the executor for the controller of choice.  Now it is just the apex but I can see using
		 * other ones like the DA.  Pass in the http client and database resolver it will need to do its job.
		 */
		mRemoteExecutor = new ApexExecutor(this, httpClient, dbResolverNightlySrvc);

		mNightlyServiceContext = this;
}

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent(intent=" + intent.toString() + ")");

		/**
		 * Using the intent, we can tell why we are running this service
		 */

		/**
		 *  Only start processing thread if not already running, if the thread was running it would
		 *  grab the queue items
		 */
		synchronized (sLock) {
			if (!sThreadRunning) {
				sThreadRunning = true;
				new NightlyThread().execute();
			}
		}
	} // end of onHandleIntent()
    
	/**
	 * Background task to handle Apex lookups. This correctly shows and
	 * hides the loading animation from the GUI thread before starting a
	 * background query to the API. When finished, it transitions
	 * back to the GUI thread where it updates with the newly-found entry.
	 */
	private class NightlyThread extends AsyncTask<String, Integer, Boolean> {

		/**
		 * Before jumping into background thread, start sliding in the
		 * {@link ProgressBar}. We'll only show it once the animation finishes.
		 * 
		 * This method is executed in the UI thread space and has access to
		 * graphical elements of the UI.
		 */
		@Override
		protected void onPreExecute() {
		}

		/**
		 * Perform the background query.
		 * 
		 * This method is executed in the background thread space and
		 * does NOT have access to the graphical elements of the UI.
		 */
		@Override
		protected Boolean doInBackground(String... args) {
			Log.d(TAG, "Processing thread started");
			
			ContentResolver dbResolverNightlySrvcThread = getContentResolver();
			
     		Cursor cursor = null;
    		try {
    			/**
    			 * For each controller in the database
    			 */
    			Uri controllersQueryUri = Controllers.buildQueryControllersUri();
    			cursor = dbResolverNightlySrvcThread.query(controllersQueryUri, ControllersQuery.PROJECTION, null, null, null);
    			if (cursor != null && cursor.moveToFirst()) {
 //   				while (!cursor.isAfterLast()) {
    					/**
    					 * Prune the data as specified in that controller's preferences
    					 * BUG, the controller is ignored now, need to somehow join the data to the controller.
    					 */
        				int mControllerId = cursor.getInt(ControllersQuery._ID); 
        				int mPruneDays = cursor.getInt(ControllersQuery.DB_SAVE_DAYS); 
    					Uri prunePUri = Data.buildDeletePDataOlderThanUri(mControllerId, mPruneDays);
    					Uri pruneOUri = Data.buildDeleteODataOlderThanUri(mControllerId, mPruneDays);
    					dbResolverNightlySrvcThread.delete(prunePUri, null, null);
    					dbResolverNightlySrvcThread.delete(pruneOUri, null, null);
//    				}
    			}
    		} catch (SQLException e) {
    			Log.e(TAG, "prune: deleting data.", e);	
    			// need a little more here!
    		} finally {
    			if (cursor != null) {
    				cursor.close();
    			}
    		}



			return true;
		} // end of doInBackgrount

		/**
		 * Our progress update pushes a timestamp/error update.
		 * 
		 * This method is executed in the UI thread space.
		 */
		@Override
		protected void onProgressUpdate(Integer... arg) {
		}

		/**
		 * When finished, push the newly-found entry content into our
		 * {@link WebView} and hide the {@link ProgressBar}.
		 * 
		 * This method is executed in the UI thread space.
		 */
		@Override
		protected void onPostExecute(Boolean resultFailedFlag) {

			Time now = new Time();
			now.setToNow();
			
			Time nextNoon = new Time(now);
			nextNoon.hour=12;
			nextNoon.minute=00;
			nextNoon.second=00;
			nextNoon.yearDay++;
			
			
			Intent intent = new Intent(mNightlyServiceContext, NightlyService.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(mNightlyServiceContext, 0, intent, 0);

			AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
			alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, nextNoon.toMillis(false), 24*1000*60*60, pendingIntent);

			// No updates remaining, so stop service
			stopSelf();
		}
	}

    /**
     * Generate and return a {@link HttpClient} configured for general use,
     * including setting an application-specific user-agent string.
     */
	public static HttpClient getHttpClient(Context context) {
		final HttpParams params = new BasicHttpParams();

		// Use generous timeouts for slow mobile networks
		HttpConnectionParams.setConnectionTimeout(params, 20 * SECOND_IN_MILLIS);
		HttpConnectionParams.setSoTimeout(params, 20 * SECOND_IN_MILLIS);

		HttpConnectionParams.setSocketBufferSize(params, 8192);
		HttpProtocolParams.setUserAgent(params, buildUserAgent(context));

		final DefaultHttpClient client = new DefaultHttpClient(params);

		client.addRequestInterceptor(new HttpRequestInterceptor() {
			public void process(HttpRequest request, HttpContext context) {
				// Add header to accept gzip content
				if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
					request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
				}
			}
		});

		client.addResponseInterceptor(new HttpResponseInterceptor() {
			public void process(HttpResponse response, HttpContext context) {
				// Inflate any responses compressed with gzip
				final HttpEntity entity = response.getEntity();
				final Header encoding = entity.getContentEncoding();
				if (encoding != null) {
					for (HeaderElement element : encoding.getElements()) {
						if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
							response.setEntity(new InflatingEntity(response.getEntity()));
							break;
						}
					}
				}
			}
		});
		return client;
	}

    /**
     * Build and return a user-agent string that can identify this application
     * to remote servers. Contains the package name and version code.
     */
    private static String buildUserAgent(Context context) {
        try {
            final PackageManager manager = context.getPackageManager();
            final PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);

            // Some APIs require "(gzip)" in the user-agent string.
            return info.packageName + "/" + info.versionName
                    + " (" + info.versionCode + ") (gzip)";
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Simple {@link HttpEntityWrapper} that inflates the wrapped
     * {@link HttpEntity} by passing it through {@link GZIPInputStream}.
     */
    private static class InflatingEntity extends HttpEntityWrapper {
        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }

    private interface Prefs {
        String IOSCHED_SYNC = "iosched_sync";
        String LOCAL_VERSION = "local_version";
    }
    
	/**
	 * Lock used when maintaining queue of requested updates.
	 */
	private static Object sLock = new Object();

    private interface ControllersQuery {
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

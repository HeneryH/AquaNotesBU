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
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ContentResolver;
import android.content.Context;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.GZIPInputStream;

/**
 * Background {@link Service} that synchronizes data living in
 * {@link AquaNotesDbProvider}. Reads data from both local {@link Resources} and
 * from remote sources, such as a spreadsheet.
 */
public class SyncService extends IntentService {
    private static final String TAG = "SyncService";

	/**
	 * Intent actions and extras
	 */
    public static final String EXTRA_STATUS_RECEIVER = "com.heneryh.aquanotes.extra.STATUS_RECEIVER";
	public static final String ACTION_UPDATE_SINGLE = "com.heneryh.aquanotes.UPDATE_SINGLE";
	public static final String ACTION_UPDATE_ALL = "com.heneryh.aquanotes.UPDATE_ALL";

	/**
	 * Status flags to be sent back to the calling activity via the receiver
	 */
    public static final int STATUS_RUNNING = 0x1;
    public static final int STATUS_ERROR = 0x2;
    public static final int STATUS_FINISHED = 0x3;

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

    private static final int VERSION_NONE = 0;
    private static final int VERSION_CURRENT = 11;

    private LocalExecutor mLocalExecutor;
	private ApexExecutor mRemoteExecutor;

	private ContentResolver dbResolverSyncSrvc;
	private ResultReceiver guiStatusReceiver;

	Context mSyncServiceContext;

	/**
	 * Main service methods
	 */
    public SyncService() {
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
		dbResolverSyncSrvc = getContentResolver();

        mLocalExecutor = new LocalExecutor(getResources(), dbResolverSyncSrvc);

        /**
		 * Create the executor for the controller of choice.  Now it is just the apex but I can see using
		 * other ones like the DA.  Pass in the http client and database resolver it will need to do its job.
		 */
		mRemoteExecutor = new ApexExecutor(this, httpClient, dbResolverSyncSrvc);

		mSyncServiceContext = this;
}

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent(intent=" + intent.toString() + ")");

		/**
		 * Using the intent, we can tell why we are running this service
		 */
		if (ACTION_UPDATE_ALL.equals(intent.getAction())) { // This came from the timer expiring, get all widgets onto queue
		} else if (ACTION_UPDATE_SINGLE.equals(intent.getAction())) { // This came from the a widget update, id is in the queue

		} else if(Intent.ACTION_SYNC.equals(intent.getAction())) { // this came from the main GUI
			guiStatusReceiver = intent.getParcelableExtra(EXTRA_STATUS_RECEIVER);
		}

		/**
		 *  Only start processing thread if not already running, if the thread was running it would
		 *  grab the queue items
		 */
		synchronized (sLock) {
			if (!sThreadRunning) {
				sThreadRunning = true;
				new SyncThread().execute();
			}
		}
	} // end of onHandleIntent()
    
	/**
	 * Background task to handle Apex lookups. This correctly shows and
	 * hides the loading animation from the GUI thread before starting a
	 * background query to the API. When finished, it transitions
	 * back to the GUI thread where it updates with the newly-found entry.
	 */
	private class SyncThread extends AsyncTask<String, Integer, Boolean> {

		/**
		 * Before jumping into background thread, start sliding in the
		 * {@link ProgressBar}. We'll only show it once the animation finishes.
		 * 
		 * This method is executed in the UI thread space and has access to
		 * graphical elements of the UI.
		 */
		@Override
		protected void onPreExecute() {
			if (guiStatusReceiver != null) 
				guiStatusReceiver.send(STATUS_RUNNING, Bundle.EMPTY);	
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
			
			/**
			 * We can only pass a single result back to the main thread which will then report status
			 * to the gui.
			 */
			boolean resultFailedFlag=false;
			ContentResolver dbResolverSyncSrvcThread = getContentResolver();

			final SharedPreferences prefs = getSharedPreferences(Prefs.IOSCHED_SYNC,
					Context.MODE_PRIVATE);
			final int localVersion = prefs.getInt(Prefs.LOCAL_VERSION, VERSION_NONE);

			try {
				// Bulk of sync work, performed by executing several fetches from
				// local and online sources.

				final long startLocal = System.currentTimeMillis();
				final boolean localParse = localVersion < VERSION_CURRENT;
				Log.d(TAG, "found localVersion=" + localVersion + " and VERSION_CURRENT="
						+ VERSION_CURRENT);
				if (localParse) {
					// Load static local data
					mLocalExecutor.execute(R.xml.blocks, new LocalBlocksHandler());
					mLocalExecutor.execute(R.xml.rooms, new LocalRoomsHandler());
					mLocalExecutor.execute(R.xml.tracks, new LocalTracksHandler());
					mLocalExecutor.execute(R.xml.search_suggest, new LocalSearchSuggestHandler());
					mLocalExecutor.execute(R.xml.sessions, new LocalSessionsHandler());

					// Parse values from local cache first, since spreadsheet copy
					// or network might be down.
					mLocalExecutor.execute(mSyncServiceContext, "cache-sessions.xml", new RemoteSessionsHandler());
					mLocalExecutor.execute(mSyncServiceContext, "cache-speakers.xml", new RemoteSpeakersHandler());
					mLocalExecutor.execute(mSyncServiceContext, "cache-vendors.xml", new RemoteVendorsHandler());

					// Save local parsed version
					prefs.edit().putInt(Prefs.LOCAL_VERSION, VERSION_CURRENT).commit();
				} // end of localParse
				Log.d(TAG, "local sync took " + (System.currentTimeMillis() - startLocal) + "ms");

				// Always hit remote spreadsheet for any updates
				final long startRemote = System.currentTimeMillis();
//            	mRemoteExecutor
//                    .executeGet(WORKSHEETS_URL, new RemoteWorksheetsHandler(mRemoteExecutor));
				/**
				 * Interval to wait between background widget updates. These will be pulled
				 * from the database during processing.
				 */
				int updateIntervalMins=0;
				long updateIntervalMillis=0;

				/**
				 * Length of time before we consider cached data stale. If a widget
				 * update is requested, and {@link AppWidgetsColumns#LAST_UPDATED} is inside
				 * this threshold, we use the cached data to build the update.
				 * Otherwise, we first trigger an update.
				 */
				long probesCacheThrottle = (0) * DateUtils.MINUTE_IN_MILLIS;

				updateIntervalMins = 99;  // trying to find the right spot for this...
				// We don't want to go nuts with a million updates prior to the 
				// update frequency being set the first time. It may not even be needed any more with 
				// various fixes over time.

				long now = System.currentTimeMillis();

				while (hasMoreUpdates()) {

					// Pull the next update request off the queue
					// and build a database Uri from it.
					int controllerId = getNextUpdate();
					Uri controllerUri = Controllers.buildQueryControllerXUri(controllerId);

					// Check if controller is configured in the database, 
					// and if we need to then update cache
					Cursor cursor = null;
					boolean isConfigured = false;
					boolean shouldUpdate = false;
					try {
						cursor = dbResolverSyncSrvcThread.query(controllerUri, ControllersQuery.PROJECTION, null, null, null);
						if (cursor != null && cursor.moveToFirst()) {
							// Pull the database info for this controller
							updateIntervalMins = cursor.getInt(ControllersQuery.UPDATE_INTERVAL); // getInt() will autoconvert the string to an int.
							long lastUpdated = cursor.getLong(ControllersQuery.LAST_UPDATED);

							// This is a little silly, if the db query works then it must be configured.

							if(lastUpdated>0) {
								// How long ago was the controller updated?
								float deltaMinutes = (float)(now - lastUpdated) / (float)(DateUtils.MINUTE_IN_MILLIS);
								Log.d(TAG, "Delta since last update for controller id " + controllerId + " is " + deltaMinutes + " min");

								// To reduce cluttering the net, if we just got an update, don't do
								// it again.
								shouldUpdate = (Math.abs(now - lastUpdated) > probesCacheThrottle);
							} else {
								Log.d(TAG, "Configured but not yet pulled any data.");
								shouldUpdate = true;
							}
						}
					} catch (SQLException e) {
						Log.e(TAG, "Checking if the controller is configured", e);
						resultFailedFlag=true;
						if (guiStatusReceiver != null) {
							// Pass back error to surface listener
							final Bundle bundle = new Bundle();
							bundle.putString(Intent.EXTRA_TEXT, e.toString());
							guiStatusReceiver.send(STATUS_ERROR, bundle);
						}
					} finally {
						if (cursor != null) {
							cursor.close();
						}
					}

				  if (shouldUpdate) {
						try {
							Log.d(TAG, "Going to perform an update");

							// Last update is outside throttle window, so update again
							
							// The logic for handling status, data and programs similarly is not fully hashed out yet...
							DefaultHandler  xmlParser = new ApexStateXMLParser(dbResolverSyncSrvcThread, controllerUri);
							mRemoteExecutor.executeGet(controllerUri, xmlParser);
							Log.d(TAG, "remote sync took " + (System.currentTimeMillis() - startRemote) + "ms");

							// Announce success to any surface listener
							Log.d(TAG, "sync finished");

							if(controllerId==999) {
							} else {
								// Process this update through the correct provider
								AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mSyncServiceContext);

								AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(controllerId);
								String providerName = info.provider.getClassName();   // <--- there are crash reports of null pointer here.  How?
								RemoteViews updateViews = null;
								Log.d(TAG, "Build a graphical update whatever type of widget this is.");
//								if (providerName.equals(Widget2x1.class.getName())) {
//									Log.d(TAG, "Building a 2x1 widget, ID = " + controllerId + ".");
//									Log.d(TAG, "Building a 2x1 widget, Uri = " + controllerUri + ".");
//									updateViews = Widget2x1.buildUpdate(mSyncServiceContext, controllerUri);
//								} else if (providerName.equals(Widget2x2.class.getName())) {
//									Log.d(TAG, "Building a 2x2 widget, ID = " + controllerId + ".");
//									Log.d(TAG, "Building a 2x2 widget, Uri = " + controllerUri + ".");
//									updateViews = Widget2x2.buildUpdate(mSyncServiceContext, controllerUri);
//								} else if (providerName.equals(Widget1x1.class.getName())) {
//									Log.d(TAG, "Building a 1x1 widget, ID = " + controllerId + ".");
//									Log.d(TAG, "Building a 1x1 widget, Uri = " + controllerUri + ".");
//									updateViews = Widget1x1.buildUpdate(mSyncServiceContext, controllerUri);
//								}

								// Push this update to surface
								if (updateViews != null) {
									Log.d(TAG, "Pushing update to the surface, ID = " + controllerId + ".");
									appWidgetManager.updateAppWidget(controllerId, updateViews);
								} else {
									Log.e(TAG, "Some problem building the view, not pushed to the surface.");
								}
							}
						} catch (HandlerException e) {
							Log.e(TAG, "Problem while syncing", e);
							resultFailedFlag=true;
							if (guiStatusReceiver != null) {
								// Pass back error to surface listener
								final Bundle bundle = new Bundle();
								bundle.putString(Intent.EXTRA_TEXT, e.toString());
								guiStatusReceiver.send(STATUS_ERROR, bundle);
							}
						}
					} // end of if(should update)
				} // end of while(more updates)

				// Schedule next update alarm.  updateFreqMins will be 99 if not at least one configured
				// updateFreqMins will be set from the last widget updated above
				if(updateIntervalMins!=99 && updateIntervalMins!=0) {
					updateIntervalMillis = updateIntervalMins * DateUtils.MINUTE_IN_MILLIS;

					Time nextTime = new Time();
					nextTime.set(now + updateIntervalMillis);
					long nextUpdate = nextTime.toMillis(false);

					float deltaMinutes = (float)(nextUpdate - now) / (float)DateUtils.MINUTE_IN_MILLIS;
					Log.d(TAG, "Requesting next update in " + deltaMinutes + " min");

					Intent updateIntent = new Intent(ACTION_UPDATE_ALL);
					updateIntent.setClass(mSyncServiceContext, SyncService.class);

					PendingIntent pendingIntent = PendingIntent.getService(mSyncServiceContext, 0, updateIntent, 0);

					//The following is a hack for some failure condition that causes the alarm
					// to not get reset
					long repeatInterval = updateIntervalMillis + 1*DateUtils.MINUTE_IN_MILLIS;

					// Schedule alarm, and force the device awake for this update
					AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
					//alarmManager.set(AlarmManager.RTC_WAKEUP, nextUpdate, pendingIntent);
					alarmManager.setRepeating(AlarmManager.RTC, nextUpdate, repeatInterval, pendingIntent);
				Log.d(TAG, "remote sync took " + (System.currentTimeMillis() - startRemote) + "ms");
				}
			} catch (Exception e) {
				Log.e(TAG, "Problem while syncing", e);
				resultFailedFlag = true;
			} // end of catch exception


//        	Log.d(TAG, "sync finished");
			return resultFailedFlag;
		} // end of doInBackgrount

		/**
		 * Our progress update pushes a timestamp/error update.
		 * 
		 * This method is executed in the UI thread space.
		 */
		@Override
		protected void onProgressUpdate(Integer... arg) {
			//if (guiStatusReceiver != null) guiStatusReceiver.send(STATUS_RUNNING, Bundle.EMPTY);	
		}

		/**
		 * When finished, push the newly-found entry content into our
		 * {@link WebView} and hide the {@link ProgressBar}.
		 * 
		 * This method is executed in the UI thread space.
		 */
		@Override
		protected void onPostExecute(Boolean resultFailedFlag) {
	        // Announce success to any surface listener
			if (guiStatusReceiver != null && !resultFailedFlag) {
				guiStatusReceiver.send(STATUS_FINISHED, Bundle.EMPTY); 
			}
			else if (guiStatusReceiver != null && resultFailedFlag) {
//              // Pass back error to surface listener
//              final Bundle bundle = new Bundle();
//              bundle.putString(Intent.EXTRA_TEXT, e.toString());
				// how do we get the error back here?? 
				guiStatusReceiver.send(STATUS_ERROR, Bundle.EMPTY);
			}

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
	 * Maintain a queue of widgets that are requesting update.  
	 * 
	 */
	
	/**
	 * Lock used when maintaining queue of requested updates.
	 */
	private static Object sLock = new Object();

	/**
	 * Internal queue of requested widget updates. You <b>must</b> access
	 * through {@link #requestUpdate(int[])} or {@link #getNextUpdate()} to make
	 * sure your access is correctly synchronized.
	 */
	private static Queue<Integer> sControllerIds = new LinkedList<Integer>();

	/**
	 * Request updates for the given widgets. Will only queue them up, you are
	 * still responsible for starting a processing thread if needed, usually by
	 * starting the parent service.
	 */
	public static void requestUpdate(int[] controllerIds) {
		synchronized (sLock) {
			for (int controllerId : controllerIds) {
				sControllerIds.add(controllerId);
			}
		}
	}

	/**
	 * Request updates for the given widgets. Will only queue them up, you are
	 * still responsible for starting a processing thread if needed, usually by
	 * starting the parent service.
	 */
	public static void requestUpdate(int controllerId) {
		synchronized (sLock) {
			sControllerIds.add(controllerId);
		}
	}

	/**
	 * Peek if we have more updates to perform. This method is special because
	 * it assumes you're calling from the update thread, and that you will
	 * terminate if no updates remain. (It atomically resets
	 * {@link #sThreadRunning} when none remain to prevent race conditions.)
	 */
	private static boolean hasMoreUpdates() {
		synchronized (sLock) {
			boolean hasMore = !sControllerIds.isEmpty();
			if (!hasMore) {
				sThreadRunning = false;
			}
			return hasMore;
		}
	}

	/**
	 * Poll the next widget update in the queue.
	 */
	private static int getNextUpdate() {
		synchronized (sLock) {
			if (sControllerIds.peek() == null) {
				return AppWidgetManager.INVALID_APPWIDGET_ID;
			} else {
				return sControllerIds.poll();
			}
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

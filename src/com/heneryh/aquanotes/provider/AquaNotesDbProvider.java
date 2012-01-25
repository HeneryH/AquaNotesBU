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

package com.heneryh.aquanotes.provider;

import com.heneryh.aquanotes.provider.AquaNotesDbContract.Blocks;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Controllers;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Outlets;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Probes;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Rooms;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.SearchSuggest;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Sessions;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Speakers;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Tracks;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Vendors;
import com.heneryh.aquanotes.provider.AquaNotesDatabase.SessionsSearchColumns;
import com.heneryh.aquanotes.provider.AquaNotesDatabase.SessionsSpeakers;
import com.heneryh.aquanotes.provider.AquaNotesDatabase.SessionsTracks;
import com.heneryh.aquanotes.provider.AquaNotesDatabase.Tables;
import com.heneryh.aquanotes.provider.AquaNotesDatabase.VendorsSearchColumns;
import com.heneryh.aquanotes.service.SyncService;
import com.heneryh.aquanotes.util.SelectionBuilder;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



/**
 * Provider that stores {@link AquaNotesDbContract} data. Data is usually inserted
 * by {@link SyncService}, and queried by various {@link Activity} instances.
 */
public class AquaNotesDbProvider extends ContentProvider {
    private static final String TAG = "AquaNotesDbProvider";
    private static final boolean LOGV = true; //Log.isLoggable(TAG, Log.VERBOSE);

    private AquaNotesDatabase mOpenHelper;

    private static final UriMatcher sUriMatcher = buildUriMatcher();

	/**
	 *  URI IDs to be used in the switch () statements
	 *  notice two possible switch statements for the same twig depending on if something follows
	 */
	private static final int CONTROLLERS = 901;
	private static final int CONTROLLERS_ID = 902;
	private static final int CONTROLLERS_URL = 903;

	private static final int PROBES = 904;
	private static final int CONTROLLERS_ID_PROBES = 905;
	private static final int CONTROLLERS_ID_PROBES_ID = 906;
	private static final int CONTROLLERS_ID_PROBES_NAME = 907;
	
	private static final int OUTLETS = 908;
	private static final int CONTROLLERS_ID_OUTLETS = 909;
	private static final int CONTROLLERS_ID_OUTLETS_ID = 910;
	private static final int CONTROLLERS_ID_OUTLETS_DEVICE_ID = 911;
	private static final int CONTROLLERS_ID_OUTLETS_RSC = 912; 

	private static final int DATA = 913; 
	private static final int PDATA = 920; 
	private static final int ODATA = 921; 
	private static final int CONTROLLERS_ID_PROBEDATA_AT = 914; 
	private static final int CONTROLLERS_ID_PROBEDATA_FOR_ID = 915; 
	private static final int CONTROLLERS_ID_PROBEDATA_FOR_NAME = 916; 
	private static final int CONTROLLERS_ID_OUTLETDATA_AT = 917; 
	private static final int CONTROLLERS_ID_OUTLETDATA_FOR_ID = 918; 
	private static final int CONTROLLERS_ID_OUTLETDATA_FOR_DEVICE_ID = 919; 
    
    private static final int BLOCKS = 100;
    private static final int BLOCKS_BETWEEN = 101;
    private static final int BLOCKS_ID = 102;
    private static final int BLOCKS_ID_SESSIONS = 103;

    private static final int TRACKS = 200;
    private static final int TRACKS_ID = 201;
    private static final int TRACKS_ID_SESSIONS = 202;
    private static final int TRACKS_ID_VENDORS = 203;

    private static final int ROOMS = 300;
    private static final int ROOMS_ID = 301;
    private static final int ROOMS_ID_SESSIONS = 302;

    private static final int SESSIONS = 400;
    private static final int SESSIONS_STARRED = 401;
    private static final int SESSIONS_SEARCH = 402;
    private static final int SESSIONS_AT = 403;
    private static final int SESSIONS_ID = 404;
    private static final int SESSIONS_ID_SPEAKERS = 405;
    private static final int SESSIONS_ID_TRACKS = 406;

    private static final int SPEAKERS = 500;
    private static final int SPEAKERS_ID = 501;
    private static final int SPEAKERS_ID_SESSIONS = 502;

    private static final int VENDORS = 600;
    private static final int VENDORS_STARRED = 601;
    private static final int VENDORS_SEARCH = 603;
    private static final int VENDORS_ID = 604;

    private static final int SEARCH_SUGGEST = 800;

    private static final String MIME_XML = "text/xml";

    /**
     * Build and return a {@link UriMatcher} that catches all {@link Uri}
     * variations supported by this {@link ContentProvider}.
     */
    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = AquaNotesDbContract.CONTENT_AUTHORITY;

    	/**
    	 *  URI IDs to be used in the switch () statements
    	 *  notice two possible switch statements for the same twig depending on if something follows
    	 */
//////////////////////////////////////////////
//    	private static final int CONTROLLERS = 101;
//    	private static final int CONTROLLERS_ID = 102;
//    	private static final int CONTROLLERS_URL = 903;
//      private static final String PATH_CONTROLLERS = "controllers";
        matcher.addURI(authority, "controllers", CONTROLLERS);
		// query = return all controllers 
		// insert = add the controller defined in the values object, auto-create a controller_id
		// update = ?
		// delete = delete all controllers, probes, data, etc ie everything!
		// getType = return type for multiple items

        matcher.addURI(authority, "controllers/#", CONTROLLERS_ID);
		// query = return just one controller
		// insert = ?
		// update = Update this one controller with the values object
		// delete = delete the referenced controller, its probes and all probe data for this controller
		// getType = return type for single item

        matcher.addURI(authority, "controllers/*", CONTROLLERS_URL);
		// query = return just one controller
		// insert = ?
		// update = Update this one controller with the values object
		// delete = delete the referenced controller, its probes and all probe data for this controller
		// getType = return type for single item

//////////////////////////////////////////////
//    	private static final int PROBES = 903;
//    	private static final int CONTROLLERS_ID_PROBES = 904;
//    	private static final int CONTROLLERS_ID_PROBES_ID = 905;
//    	private static final int CONTROLLERS_ID_PROBES_NAME = 906;
//        private static final String PATH_PROBES = "probes";
//        private static final String PATH_PROBES_NAME = "probes_nm";
        matcher.addURI(authority, "probes", PROBES);
		// query = return all probes for all controllers, used just in the db maint screen
		// insert = while this might work, use the one with the controller specified 
		// update = ?
		// delete = ?
		// getType = return type for multiple items

        matcher.addURI(authority, "controllers/#/probes", CONTROLLERS_ID_PROBES);
		// query = return all probes for a given controller
		// insert = add a probe defined by the values object to the referenced controller, auto-create a probe_id 
		// update = ?
		// delete = delete ?
		// getType = return type for multiple items

		matcher.addURI(authority, "controllers/#/probes/#", CONTROLLERS_ID_PROBES_ID);
		// query = return just one probe, by ID
		// insert = add ?
		// update = ?
		// delete = delete ?
		// getType = return type for single item

		matcher.addURI(authority, "controllers/#/probes_nm/*", CONTROLLERS_ID_PROBES_NAME);
		// query = return just one probe, by name
		// insert = add ?
		// update = ?
		// delete = delete ?
		// getType = return type for single item

//		private static final int OUTLETS = 907;
//		private static final int CONTROLLERS_ID_OUTLETS = 908;
//		private static final int CONTROLLERS_ID_OUTLETS_ID = 909;
//		private static final int CONTROLLERS_ID_OUTLETS_DEVICE_ID = 910;
//		private static final int CONTROLLERS_ID_OUTLETS_RSC = 911; 
//	    private static final String PATH_OUTLETS = "outlets";
//	    private static final String PATH_OUTLETS_RESOURCE_ID = "outlets_rsc";
//	    private static final String PATH_OUTLETS_DEVICE_ID = "outlets_did";
        matcher.addURI(authority, "outlets", OUTLETS);
		// query = return all outelts for all controllers, used just in the db maint screen
		// insert = while this might work, use the one with the controller specified 
		// update = ?
		// delete = ?
		// getType = return type for multiple items

        matcher.addURI(authority, "controllers/#/outlets", CONTROLLERS_ID_OUTLETS);
		// query = return all outlets for a given controller
		// insert = add a probe defined by the values object to the referenced controller, auto-create a probe_id 
		// update = ?
		// delete = delete ?
		// getType = ?

		matcher.addURI(authority, "controllers/#/outlets/#", CONTROLLERS_ID_OUTLETS_ID);
		// query = return just one outlet, by ID
		// insert = add ?
		// update = ?
		// delete = delete ?
		// getType = ?

		matcher.addURI(authority, "controllers/#/outlets_did/*", CONTROLLERS_ID_OUTLETS_DEVICE_ID);
		// query = return just one probe, by device id
		// insert = add ?
		// update = ?
		// delete = delete ?
		// getType = ?

		matcher.addURI(authority, "controllers/#/outlets_rsc/#", CONTROLLERS_ID_OUTLETS_RSC);
		// query = return the probe record for a given controller and outlet name.
		// insert = N/A
		// update = N/A
		// delete = N/A
		// getType = ?

//////////////////////////////////////////////
//		private static final int DATA = 912; 
//		private static final int CONTROLLERS_ID_PROBEDATA_AT = 913; 
//		private static final int CONTROLLERS_ID_PROBEDATA_FOR_ID = 914; 
//		private static final int CONTROLLERS_ID_PROBEDATA_FOR_NAME = 915; 
//		private static final int CONTROLLERS_ID_OUTLETDATA_AT = 916; 
//		private static final int CONTROLLERS_ID_OUTLETDATA_FOR_ID = 917; 
//		private static final int CONTROLLERS_ID_OUTLETDATA_FOR_DEVICE_ID = 918; 
//	    private static final String PATH_DATA = "data";
//	    private static final String PATH_PROBE_DATA_AT = "pdata_at";
//	    private static final String PATH_PROBE_DATA_FOR_ID = "pdata_id";
//	    private static final String PATH_PROBE_DATA_FOR_NAME = "pdata_nm";
//	    private static final String PATH_OUTLET_DATA_AT = "odata_at";
//	    private static final String PATH_OUTLET_DATA_FOR_ID = "odata_id";
//	    private static final String PATH_OUTLET_DATA_FOR_DID = "odata_did";
		matcher.addURI(authority, "data", DATA);
		matcher.addURI(authority, "pdata", PDATA);
		matcher.addURI(authority, "odata", ODATA);
		// query = return all the probe data records for all controllers, only used in db maint screen.
		// insert = N/A
		// update = N/A
		// delete = 
		// getType = ?

		matcher.addURI(authority, "controllers/#/pdata_at/*", CONTROLLERS_ID_PROBEDATA_AT);
		// query = return all the probe data records for a given controller at the timestamp provided.
		// insert = N/A
		// update = N/A
		// delete = delete probe data older than '*'
		// getType = ?

		matcher.addURI(authority, "controllers/#/odata_at/*", CONTROLLERS_ID_OUTLETDATA_AT);
		// query = return all the outlet data records for a given controller at the timestamp provided.
		// insert = N/A
		// update = N/A
		// delete = delete outlet data older than '*'
		// getType = ?


		matcher.addURI(authority, "controllers/#/pdata_for_id/#", CONTROLLERS_ID_PROBEDATA_FOR_ID);
		// query = return all the probe data records for a given controller and probe name.
		// insert = N/A
		// update = N/A
		// delete = N/A
		// getType = ?

		matcher.addURI(authority, "controllers/#/odata_for_id/#", CONTROLLERS_ID_OUTLETDATA_FOR_ID);
		// query = return all the outlet data records for a given controller and outlet name.
		// insert = N/A
		// update = N/A
		// delete = N/A
		// getType = ?

		matcher.addURI(authority, "controllers/#/pdata_for_nm/*", CONTROLLERS_ID_PROBEDATA_FOR_NAME);
		// query = return all the probe data records for a given controller and probe name.
		// insert = N/A
		// update = N/A
		// delete = N/A
		// getType = ?

		matcher.addURI(authority, "controllers/#/odata_for_deviceID/*", CONTROLLERS_ID_OUTLETDATA_FOR_DEVICE_ID);
		// query = return all the outlet data records for a given controller and outlet device id.
		// insert = N/A
		// update = N/A
		// delete = N/A
		// getType = ?  
		
		matcher.addURI(authority, "blocks", BLOCKS);
        matcher.addURI(authority, "blocks/between/*/*", BLOCKS_BETWEEN);
        matcher.addURI(authority, "blocks/*", BLOCKS_ID);
        matcher.addURI(authority, "blocks/*/sessions", BLOCKS_ID_SESSIONS);

        matcher.addURI(authority, "tracks", TRACKS);
        matcher.addURI(authority, "tracks/*", TRACKS_ID);
        matcher.addURI(authority, "tracks/*/sessions", TRACKS_ID_SESSIONS);
        matcher.addURI(authority, "tracks/*/vendors", TRACKS_ID_VENDORS);

        matcher.addURI(authority, "rooms", ROOMS);
        matcher.addURI(authority, "rooms/*", ROOMS_ID);
        matcher.addURI(authority, "rooms/*/sessions", ROOMS_ID_SESSIONS);

        matcher.addURI(authority, "sessions", SESSIONS);
        matcher.addURI(authority, "sessions/starred", SESSIONS_STARRED);
        matcher.addURI(authority, "sessions/search/*", SESSIONS_SEARCH);
        matcher.addURI(authority, "sessions/at/*", SESSIONS_AT);
        matcher.addURI(authority, "sessions/*", SESSIONS_ID);
        matcher.addURI(authority, "sessions/*/speakers", SESSIONS_ID_SPEAKERS);
        matcher.addURI(authority, "sessions/*/tracks", SESSIONS_ID_TRACKS);

        matcher.addURI(authority, "speakers", SPEAKERS);
        matcher.addURI(authority, "speakers/*", SPEAKERS_ID);
        matcher.addURI(authority, "speakers/*/sessions", SPEAKERS_ID_SESSIONS);

        matcher.addURI(authority, "vendors", VENDORS);
        matcher.addURI(authority, "vendors/starred", VENDORS_STARRED);
        matcher.addURI(authority, "vendors/search/*", VENDORS_SEARCH);
        matcher.addURI(authority, "vendors/*", VENDORS_ID);

        matcher.addURI(authority, "search_suggest_query", SEARCH_SUGGEST);

        return matcher;
    }

    @Override
    public boolean onCreate() {
        final Context context = getContext();
        mOpenHelper = new AquaNotesDatabase(context);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String getType(Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
//////////////////////////////////////////////
//    	private static final int CONTROLLERS = 901;
//    	private static final int CONTROLLERS_ID = 902;
//    	private static final int CONTROLLERS_URL = 903;
//private static final String PATH_CONTROLLERS = "controllers";
        case CONTROLLERS:
        	return Controllers.CONTENT_TYPE;
        	
        case CONTROLLERS_ID:
        	return Controllers.CONTENT_ITEM_TYPE;

        case CONTROLLERS_URL:
        	return Controllers.CONTENT_ITEM_TYPE;

//////////////////////////////////////////////
//        	private static final int PROBES = 903;
//        	private static final int CONTROLLERS_ID_PROBES = 904;
//        	private static final int CONTROLLERS_ID_PROBES_ID = 905;
//        	private static final int CONTROLLERS_ID_PROBES_NAME = 906;
//private static final String PATH_PROBES = "probes";
//private static final String PATH_PROBES_NAME = "probes_nm";
        case PROBES:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_PROBES:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_PROBES_ID:
        	return Probes.CONTENT_ITEM_TYPE;
        	
        case CONTROLLERS_ID_PROBES_NAME:
        	return Probes.CONTENT_ITEM_TYPE;
        	
//        	private static final int OUTLETS = 907;
//        	private static final int CONTROLLERS_ID_OUTLETS = 908;
//        	private static final int CONTROLLERS_ID_OUTLETS_ID = 909;
//        	private static final int CONTROLLERS_ID_OUTLETS_DEVICE_ID = 910;
//        	private static final int CONTROLLERS_ID_OUTLETS_RSC = 911; 
//private static final String PATH_OUTLETS = "outlets";
//private static final String PATH_OUTLETS_RESOURCE_ID = "outlets_rsc";
//private static final String PATH_OUTLETS_DEVICE_ID = "outlets_did";
        case OUTLETS:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_OUTLETS:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_OUTLETS_ID:
        	return Probes.CONTENT_ITEM_TYPE;
        	
        case CONTROLLERS_ID_OUTLETS_DEVICE_ID:
        	return Probes.CONTENT_ITEM_TYPE;
        	
        case CONTROLLERS_ID_OUTLETS_RSC:
        	return Probes.CONTENT_ITEM_TYPE;

//////////////////////////////////////////////
//        	private static final int DATA = 912; 
//        	private static final int CONTROLLERS_ID_PROBEDATA_AT = 913; 
//        	private static final int CONTROLLERS_ID_PROBEDATA_FOR_ID = 914; 
//        	private static final int CONTROLLERS_ID_PROBEDATA_FOR_NAME = 915; 
//        	private static final int CONTROLLERS_ID_OUTLETDATA_AT = 916; 
//        	private static final int CONTROLLERS_ID_OUTLETDATA_FOR_ID = 917; 
//        	private static final int CONTROLLERS_ID_OUTLETDATA_FOR_DEVICE_ID = 918; 
//    	    private static final String PATH_DATA = "data";
//    	    private static final String PATH_PROBE_DATA_AT = "pdata_at";
//    	    private static final String PATH_PROBE_DATA_FOR_ID = "pdata_id";
//    	    private static final String PATH_PROBE_DATA_FOR_NAME = "pdata_nm";
//    	    private static final String PATH_OUTLET_DATA_AT = "odata_at";
//    	    private static final String PATH_OUTLET_DATA_FOR_ID = "odata_id";
//    	    private static final String PATH_OUTLET_DATA_FOR_DID = "odata_did";
        case DATA:
        	return Probes.CONTENT_TYPE;
        case PDATA:
        	return Probes.CONTENT_TYPE;
        case ODATA:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_PROBEDATA_AT:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_OUTLETDATA_AT:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_PROBEDATA_FOR_ID:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_OUTLETDATA_FOR_ID:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_PROBEDATA_FOR_NAME:
        	return Probes.CONTENT_TYPE;
        	
        case CONTROLLERS_ID_OUTLETDATA_FOR_DEVICE_ID:
        	return Probes.CONTENT_TYPE;
        	
        	
            case BLOCKS:
                return Blocks.CONTENT_TYPE;
            case BLOCKS_BETWEEN:
                return Blocks.CONTENT_TYPE;
            case BLOCKS_ID:
                return Blocks.CONTENT_ITEM_TYPE;
            case BLOCKS_ID_SESSIONS:
                return Sessions.CONTENT_TYPE;
            case TRACKS:
                return Tracks.CONTENT_TYPE;
            case TRACKS_ID:
                return Tracks.CONTENT_ITEM_TYPE;
            case TRACKS_ID_SESSIONS:
                return Sessions.CONTENT_TYPE;
            case TRACKS_ID_VENDORS:
                return Vendors.CONTENT_TYPE;
            case ROOMS:
                return Rooms.CONTENT_TYPE;
            case ROOMS_ID:
                return Rooms.CONTENT_ITEM_TYPE;
            case ROOMS_ID_SESSIONS:
                return Sessions.CONTENT_TYPE;
            case SESSIONS:
                return Sessions.CONTENT_TYPE;
            case SESSIONS_STARRED:
                return Sessions.CONTENT_TYPE;
            case SESSIONS_SEARCH:
                return Sessions.CONTENT_TYPE;
            case SESSIONS_AT:
                return Sessions.CONTENT_TYPE;
            case SESSIONS_ID:
                return Sessions.CONTENT_ITEM_TYPE;
            case SESSIONS_ID_SPEAKERS:
                return Speakers.CONTENT_TYPE;
            case SESSIONS_ID_TRACKS:
                return Tracks.CONTENT_TYPE;
            case SPEAKERS:
                return Speakers.CONTENT_TYPE;
            case SPEAKERS_ID:
                return Speakers.CONTENT_ITEM_TYPE;
            case SPEAKERS_ID_SESSIONS:
                return Sessions.CONTENT_TYPE;
            case VENDORS:
                return Vendors.CONTENT_TYPE;
            case VENDORS_STARRED:
                return Vendors.CONTENT_TYPE;
            case VENDORS_SEARCH:
                return Vendors.CONTENT_TYPE;
            case VENDORS_ID:
                return Vendors.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (LOGV) Log.v(TAG, "query(uri=" + uri + ", proj=" + Arrays.toString(projection) + ")");
        final SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        final int match = sUriMatcher.match(uri);
        switch (match) {
//        /**
//         * First off, the sample uses a simple selection builder and a common query.  I don't know why
//         * it didn't work for me but I went brute force...
//         */
//        
//        // All all controllers
//        case CONTROLLERS: {
//        	qb.setTables(Tables.CONTROLLERS);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//
//        // Controller where ID=x
//        case CONTROLLERS_ID: {
//        	String controllerId = Controllers.getControllerId(uri);
//        	qb.setTables(Tables.CONTROLLERS);
//        	qb.appendWhere(BaseColumns._ID + "=" + controllerId);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//
//        // Controller where ID=x
//        case CONTROLLERS_URL: {
//        	String controllerUrl = Controllers.getControllerId(uri);
//        	qb.setTables(Tables.CONTROLLERS);
//        	qb.appendWhere(Controllers.WAN_URL + "=" + controllerUrl);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//
//        // All probes regardless of controller, maint only
//        case PROBES: {
//        	qb.setTables(Tables.PROBES);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//
//        // All outlets regardless of controller, maint only
//        case OUTLETS: {
//        	qb.setTables(Tables.OUTLETS);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//
//        // Get a probe record by controller id and probe name.
//        case CONTROLLERS_ID_PROBES_NAME: {
//        	String controllerId = uri.getPathSegments().get(1);
//        	String probeName = uri.getPathSegments().get(3);
//        	qb.setTables(Tables.PROBES);
//        	qb.appendWhere(Probes.CONTROLLER_ID + "=");
//        	qb.appendWhereEscapeString(controllerId);
//        	qb.appendWhere(" and " + Probes.NAME + "=");
//        	qb.appendWhereEscapeString(probeName);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//
//        // Get a outlet record by controller id and outlet device id.
//        case CONTROLLERS_ID_OUTLETS_DEVICE_ID: {   
//        	String controllerId = uri.getPathSegments().get(1);
//        	String outletDeviceID = uri.getPathSegments().get(3);
//        	qb.setTables(Tables.OUTLETS);
//        	qb.appendWhere(Outlets.CONTROLLER_ID + "=");
//        	qb.appendWhereEscapeString(controllerId);
//        	qb.appendWhere(" and " + Outlets.DEVICE_ID + "=");
//        	qb.appendWhereEscapeString(outletDeviceID);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//        
//        // Get all data records regardless of controller, db maint use only
//        case DATA: {
//        	qb.setTables(Tables.PDATA_VIEW);
//        	return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit1);
//        }
//        
        default: {
        	// Most cases are handled with simple SelectionBuilder
        	final SelectionBuilder builder = buildExpandedSelection(uri, match);
        	return builder.where(selection, selectionArgs).query(db, projection, sortOrder);
        }
        case SEARCH_SUGGEST: {
        	final SelectionBuilder builder = new SelectionBuilder();

        	// Adjust incoming query to become SQL text match
        	selectionArgs[0] = selectionArgs[0] + "%";
        	builder.table(Tables.SEARCH_SUGGEST);
        	builder.where(selection, selectionArgs);
        	builder.map(SearchManager.SUGGEST_COLUMN_QUERY,
        			SearchManager.SUGGEST_COLUMN_TEXT_1);

        	projection = new String[] { BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1,
        			SearchManager.SUGGEST_COLUMN_QUERY };

        	final String limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT);
        	return builder.query(db, projection, null, null, SearchSuggest.DEFAULT_SORT, limit);
        }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (LOGV) Log.v(TAG, "insert(uri=" + uri + ", values=" + values.toString() + ")");
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
		long rowId;

        switch (match) {
        
        // Insert a single controller
		case CONTROLLERS: {
			rowId = db.insertOrThrow(Tables.CONTROLLERS, Controllers.TITLE, values);
            getContext().getContentResolver().notifyChange(uri, null);
            return ContentUris.withAppendedId(uri, rowId);
		}
		
		// Insert a probe for controller x.
		case CONTROLLERS_ID_PROBES: {
			rowId = db.insertOrThrow(Tables.PROBES, Probes.NAME, values);
            getContext().getContentResolver().notifyChange(uri, null);
            return ContentUris.withAppendedId(uri, rowId);
		}
		
		// Insert an outlet for controller x.
		case CONTROLLERS_ID_OUTLETS: {
			rowId = db.insertOrThrow(Tables.OUTLETS, Outlets.NAME, values);
            getContext().getContentResolver().notifyChange(uri, null);
            return ContentUris.withAppendedId(uri, rowId);
		}
		
		// Insert data for controller x and probe/outlet y
		case CONTROLLERS_ID_PROBES_ID: {
			values.put(Data.TYPE, 1);
			rowId = db.insertOrThrow(Tables.DATA, Data.VALUE, values);
            getContext().getContentResolver().notifyChange(uri, null);
            return ContentUris.withAppendedId(uri, rowId);
		}
		case CONTROLLERS_ID_OUTLETS_ID: {
			values.put(Data.TYPE, 0);
			rowId = db.insertOrThrow(Tables.DATA, Data.VALUE, values);
            getContext().getContentResolver().notifyChange(uri, null);
            return ContentUris.withAppendedId(uri, rowId);
		}

            case BLOCKS: {
                db.insertOrThrow(Tables.BLOCKS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Blocks.buildBlockUri(values.getAsString(Blocks.BLOCK_ID));
            }
            case TRACKS: {
                db.insertOrThrow(Tables.TRACKS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Tracks.buildTrackUri(values.getAsString(Tracks.TRACK_ID));
            }
            case ROOMS: {
                db.insertOrThrow(Tables.ROOMS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Rooms.buildRoomUri(values.getAsString(Rooms.ROOM_ID));
            }
            case SESSIONS: {
                db.insertOrThrow(Tables.SESSIONS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Sessions.buildSessionUri(values.getAsString(Sessions.SESSION_ID));
            }
            case SESSIONS_ID_SPEAKERS: {
                db.insertOrThrow(Tables.SESSIONS_SPEAKERS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Speakers.buildSpeakerUri(values.getAsString(SessionsSpeakers.SPEAKER_ID));
            }
            case SESSIONS_ID_TRACKS: {
                db.insertOrThrow(Tables.SESSIONS_TRACKS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Tracks.buildTrackUri(values.getAsString(SessionsTracks.TRACK_ID));
            }
            case SPEAKERS: {
                db.insertOrThrow(Tables.SPEAKERS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Speakers.buildSpeakerUri(values.getAsString(Speakers.SPEAKER_ID));
            }
            case VENDORS: {
                db.insertOrThrow(Tables.VENDORS, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return Vendors.buildVendorUri(values.getAsString(Vendors.VENDOR_ID));
            }
            case SEARCH_SUGGEST: {
                db.insertOrThrow(Tables.SEARCH_SUGGEST, null, values);
                getContext().getContentResolver().notifyChange(uri, null);
                return SearchSuggest.CONTENT_URI;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (LOGV) Log.v(TAG, "update(uri=" + uri + ", values=" + values.toString() + ")");
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        
//        
//    	switch (sUriMatcher.match(uri)) {
//    	case CONTROLLERS_ID: {
//    		String controllerId = uri.getPathSegments().get(1);
//    		return db.update(Tables.CONTROLLERS, values, BaseColumns._ID + "=?",
//    				new String []{controllerId});
//		}
//    	case CONTROLLERS_URL: {
//    		String controllerId = uri.getPathSegments().get(1);
//    		return db.update(Tables.CONTROLLERS, values, Controllers.WAN_URL + "=?",
//    				new String []{controllerId});
//		}
//    	// I guess updating probes/outlets works with the default???
//    	}
        final SelectionBuilder builder = buildSimpleSelection(uri);
        int retVal = builder.where(selection, selectionArgs).update(db, values);
        getContext().getContentResolver().notifyChange(uri, null);
        return retVal;
    }

    /** {@inheritDoc} */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (LOGV) Log.v(TAG, "delete(uri=" + uri + ")");
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        
		int count = 0;
//
//		switch (sUriMatcher.match(uri)) {
//		case CONTROLLERS: {
//			//count = db.delete(TABLE_CONTROLLERS, selection, selectionArgs);
//			break;
//		}
//		case CONTROLLERS_ID: {
//			// Delete a specific controller and all its probes and data
//			String controllerId = uri.getPathSegments().get(1);
//			count = db.delete(Tables.CONTROLLERS, BaseColumns._ID + "=?", 
//					new String[]{controllerId});
//			count += db.delete(Tables.PROBES, Probes.CONTROLLER_ID + "=?",
//					new String[]{controllerId});
//			count += db.delete(Tables.OUTLETS, Outlets.CONTROLLER_ID + "=?",
//					new String[]{controllerId});
////			count += db.delete(Tables.DATA, Data.C + "=?",
////					new String[]{controllerId});
//			break;
//		}
//		case CONTROLLERS_URL: {
//			// Delete a specific controller and all its probes and data
//			String controllerUrl = uri.getPathSegments().get(1);
//			count = db.delete(Tables.CONTROLLERS, Controllers.WAN_URL + "=?", 
//					new String[]{controllerUrl});
//			/* TODO: need to cascase the deletes, same for above */
////			count += db.delete(Tables.PROBES, Probes.CONTROLLER_ID + "=?",
////					new String[]{controllerId});
////			count += db.delete(Tables.OUTLETS, Outlets.CONTROLLER_ID + "=?",
////					new String[]{controllerId});
////			count += db.delete(Tables.DATA, Data.C + "=?",
////					new String[]{controllerId});
//			break;
//		}
//		case CONTROLLERS_ID_PROBEDATA_AT: {
//			// Delete all the probe data older than x (in days)
//			String controllerId = uri.getPathSegments().get(1);
//			long ageDays = Long.parseLong(uri.getPathSegments().get(3));
//			long now = System.currentTimeMillis();
//			String cutoff = Long.toString(now- (ageDays*DateUtils.DAY_IN_MILLIS));
//			count += db.delete(Tables.DATA, 
////					Data.CONTROLLER_ID + "= ? and " +
//							Data.TYPE + "= ? and " +
//							Data.TIMESTAMP + "< ?", 
//							new String[]{/*controllerId,*/ "1", cutoff});
//			break;
//		}
//		case CONTROLLERS_ID_OUTLETDATA_AT: {
//			// Delete all the probe data older than x
//			String controllerId = uri.getPathSegments().get(1);
//			long age = Long.parseLong(uri.getPathSegments().get(3));
//			long now = System.currentTimeMillis();
//			String cutoff = Long.toString(now-age);
//			count += db.delete(Tables.DATA, 
////					ProbeDataColumns.CONTROLLER_ID + "= ?  and " +
//							Data.TYPE + "= ? and " +
//							Data.TIMESTAMP + "< ?",
//							new String[]{/*controllerId,*/ "0", cutoff});
//			break;
//		}
//		default:
	        final SelectionBuilder builder = buildSimpleSelection(uri);
	        count = builder.where(selection, selectionArgs).delete(db);
//		}
//
//		
//		
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    /**
     * Apply the given set of {@link ContentProviderOperation}, executing inside
     * a {@link SQLiteDatabase} transaction. All changes will be rolled back if
     * any single one fails.
     */
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                results[i] = operations.get(i).apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Build a simple {@link SelectionBuilder} to match the requested
     * {@link Uri}. This is usually enough to support {@link #insert},
     * {@link #update}, and {@link #delete} operations.
     */
    private SelectionBuilder buildSimpleSelection(Uri uri) {
        final SelectionBuilder builder = new SelectionBuilder();
        final int match = sUriMatcher.match(uri);
        switch (match) {
        case CONTROLLERS: {
            return builder.table(Tables.CONTROLLERS);
        }
        case CONTROLLERS_ID: {
            final String controllerId = Controllers.getControllerId(uri);
            return builder.table(Tables.CONTROLLERS)
                    .where(BaseColumns._ID + "=?", controllerId);
        }
        case CONTROLLERS_URL: {
            final String controllerURL = Controllers.getControllerId(uri);
    		/** TODO: I sure hope this is escaped! */
            return builder.table(Tables.CONTROLLERS)
                    .where(Controllers.WAN_URL + "=?", controllerURL);
        }
        case BLOCKS: {
            return builder.table(Tables.BLOCKS);
        }
            case BLOCKS_ID: {
                final String blockId = Blocks.getBlockId(uri);
                return builder.table(Tables.BLOCKS)
                        .where(Blocks.BLOCK_ID + "=?", blockId);
            }
            case TRACKS: {
                return builder.table(Tables.TRACKS);
            }
            case TRACKS_ID: {
                final String trackId = Tracks.getTrackId(uri);
                return builder.table(Tables.TRACKS)
                        .where(Tracks.TRACK_ID + "=?", trackId);
            }
            case ROOMS: {
                return builder.table(Tables.ROOMS);
            }
            case ROOMS_ID: {
                final String roomId = Rooms.getRoomId(uri);
                return builder.table(Tables.ROOMS)
                        .where(Rooms.ROOM_ID + "=?", roomId);
            }
            case SESSIONS: {
                return builder.table(Tables.SESSIONS);
            }
            case SESSIONS_ID: {
                final String sessionId = Sessions.getSessionId(uri);
                return builder.table(Tables.SESSIONS)
                        .where(Sessions.SESSION_ID + "=?", sessionId);
            }
            case SESSIONS_ID_SPEAKERS: {
                final String sessionId = Sessions.getSessionId(uri);
                return builder.table(Tables.SESSIONS_SPEAKERS)
                        .where(Sessions.SESSION_ID + "=?", sessionId);
            }
            case SESSIONS_ID_TRACKS: {
                final String sessionId = Sessions.getSessionId(uri);
                return builder.table(Tables.SESSIONS_TRACKS)
                        .where(Sessions.SESSION_ID + "=?", sessionId);
            }
            case SPEAKERS: {
                return builder.table(Tables.SPEAKERS);
            }
            case SPEAKERS_ID: {
                final String speakerId = Speakers.getSpeakerId(uri);
                return builder.table(Tables.SPEAKERS)
                        .where(Speakers.SPEAKER_ID + "=?", speakerId);
            }
            case VENDORS: {
                return builder.table(Tables.VENDORS);
            }
            case VENDORS_ID: {
                final String vendorId = Vendors.getVendorId(uri);
                return builder.table(Tables.VENDORS)
                        .where(Vendors.VENDOR_ID + "=?", vendorId);
            }
            case SEARCH_SUGGEST: {
                return builder.table(Tables.SEARCH_SUGGEST);
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    /**
     * Build an advanced {@link SelectionBuilder} to match the requested
     * {@link Uri}. This is usually only used by {@link #query}, since it
     * performs table joins useful for {@link Cursor} data.
     */
    private SelectionBuilder buildExpandedSelection(Uri uri, int match) {
        final SelectionBuilder builder = new SelectionBuilder();
        switch (match) {

//        // I just don't get this yet, I have a view for the join anyway...
   
        case CONTROLLERS: {
            return builder.table(Tables.CONTROLLERS);
        }
        case CONTROLLERS_ID: {
            final String controllerId = Controllers.getControllerId(uri);
            return builder.table(Tables.CONTROLLERS)
                    .where(BaseColumns._ID + "=?", controllerId);
        }
        case CONTROLLERS_URL: {
            final String controllerURL = Controllers.getControllerId(uri);
    		/** TODO: I sure hope this is escaped! */
            return builder.table(Tables.CONTROLLERS)
                    .where(Controllers.WAN_URL + "=?", Uri.decode(controllerURL));
        }

        case CONTROLLERS_ID_PROBES_NAME: {
        	final String controllerId = Probes.getControllerId(uri);
        	final String probeName = Probes.getProbeName(uri);
        	return builder.table(Tables.PROBES)
        			.where(Probes.CONTROLLER_ID + "=?", controllerId)
        			.where(Probes.NAME + "=?", probeName);
        }

        case CONTROLLERS_ID_OUTLETS_DEVICE_ID: {
        	final String controllerId = Probes.getControllerId(uri);
        	final String outletDID = Probes.getProbeName(uri);
        	return builder.table(Tables.OUTLETS)
        			.where(Outlets.CONTROLLER_ID + "=?", controllerId)
        			.where(Outlets.DEVICE_ID + "=?", outletDID);
        }
        case PROBES: {
        	return builder.table(Tables.PROBES);
        }
        case OUTLETS: {
        	return builder.table(Tables.OUTLETS);
        }
      
      // Get all data records regardless of controller, db maint use only
        /** TODO: probe and outlet data both! */
      case DATA: {
      	return builder.table(Tables.DATA);
      }
      case PDATA: {
        	return builder.table(Tables.PDATA_VIEW)
			.where(Data.TYPE + "=?", "1");
        }
      case ODATA: {
      	return builder.table(Tables.PDATA_VIEW)
			.where(Data.TYPE + "=?", "0");
        }
      

        case BLOCKS: {
                return builder.table(Tables.BLOCKS);
            }
            case BLOCKS_BETWEEN: {
                final List<String> segments = uri.getPathSegments();
                final String startTime = segments.get(2);
                final String endTime = segments.get(3);
                return builder.table(Tables.BLOCKS)
                        .map(Blocks.SESSIONS_COUNT, Subquery.BLOCK_SESSIONS_COUNT)
                        .map(Blocks.CONTAINS_STARRED, Subquery.BLOCK_CONTAINS_STARRED)
                        .where(Blocks.BLOCK_START + ">=?", startTime)
                        .where(Blocks.BLOCK_START + "<=?", endTime);
            }
            case BLOCKS_ID: {
                final String blockId = Blocks.getBlockId(uri);
                return builder.table(Tables.BLOCKS)
                        .map(Blocks.SESSIONS_COUNT, Subquery.BLOCK_SESSIONS_COUNT)
                        .map(Blocks.CONTAINS_STARRED, Subquery.BLOCK_CONTAINS_STARRED)
                        .where(Blocks.BLOCK_ID + "=?", blockId);
            }
            case BLOCKS_ID_SESSIONS: {
                final String blockId = Blocks.getBlockId(uri);
                return builder.table(Tables.SESSIONS_JOIN_BLOCKS_ROOMS)
                        .map(Blocks.SESSIONS_COUNT, Subquery.BLOCK_SESSIONS_COUNT)
                        .map(Blocks.CONTAINS_STARRED, Subquery.BLOCK_CONTAINS_STARRED)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.SESSION_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(Qualified.SESSIONS_BLOCK_ID + "=?", blockId);
            }
            case TRACKS: {
                return builder.table(Tables.TRACKS)
                        .map(Tracks.SESSIONS_COUNT, Subquery.TRACK_SESSIONS_COUNT)
                        .map(Tracks.VENDORS_COUNT, Subquery.TRACK_VENDORS_COUNT);
            }
            case TRACKS_ID: {
                final String trackId = Tracks.getTrackId(uri);
                return builder.table(Tables.TRACKS)
                        .where(Tracks.TRACK_ID + "=?", trackId);
            }
            case TRACKS_ID_SESSIONS: {
                final String trackId = Tracks.getTrackId(uri);
                return builder.table(Tables.SESSIONS_TRACKS_JOIN_SESSIONS_BLOCKS_ROOMS)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.SESSION_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(Qualified.SESSIONS_TRACKS_TRACK_ID + "=?", trackId);
            }
            case TRACKS_ID_VENDORS: {
                final String trackId = Tracks.getTrackId(uri);
                return builder.table(Tables.VENDORS_JOIN_TRACKS)
                        .mapToTable(Vendors._ID, Tables.VENDORS)
                        .mapToTable(Vendors.TRACK_ID, Tables.VENDORS)
                        .where(Qualified.VENDORS_TRACK_ID + "=?", trackId);
            }
            case ROOMS: {
                return builder.table(Tables.ROOMS);
            }
            case ROOMS_ID: {
                final String roomId = Rooms.getRoomId(uri);
                return builder.table(Tables.ROOMS)
                        .where(Rooms.ROOM_ID + "=?", roomId);
            }
            case ROOMS_ID_SESSIONS: {
                final String roomId = Rooms.getRoomId(uri);
                return builder.table(Tables.SESSIONS_JOIN_BLOCKS_ROOMS)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(Qualified.SESSIONS_ROOM_ID + "=?", roomId);
            }
            case SESSIONS: {
                return builder.table(Tables.SESSIONS_JOIN_BLOCKS_ROOMS)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS);
            }
            case SESSIONS_STARRED: {
                return builder.table(Tables.SESSIONS_JOIN_BLOCKS_ROOMS)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(Sessions.SESSION_STARRED + "=1");
            }
            case SESSIONS_SEARCH: {
                final String query = Sessions.getSearchQuery(uri);
                return builder.table(Tables.SESSIONS_SEARCH_JOIN_SESSIONS_BLOCKS_ROOMS)
                        .map(Sessions.SEARCH_SNIPPET, Subquery.SESSIONS_SNIPPET)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.SESSION_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(SessionsSearchColumns.BODY + " MATCH ?", query);
            }
            case SESSIONS_AT: {
                final List<String> segments = uri.getPathSegments();
                final String time = segments.get(2);
                return builder.table(Tables.SESSIONS_JOIN_BLOCKS_ROOMS)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(Sessions.BLOCK_START + "<=?", time)
                        .where(Sessions.BLOCK_END + ">=?", time);
            }
            case SESSIONS_ID: {
                final String sessionId = Sessions.getSessionId(uri);
                return builder.table(Tables.SESSIONS_JOIN_BLOCKS_ROOMS)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(Qualified.SESSIONS_SESSION_ID + "=?", sessionId);
            }
            case SESSIONS_ID_SPEAKERS: {
                final String sessionId = Sessions.getSessionId(uri);
                return builder.table(Tables.SESSIONS_SPEAKERS_JOIN_SPEAKERS)
                        .mapToTable(Speakers._ID, Tables.SPEAKERS)
                        .mapToTable(Speakers.SPEAKER_ID, Tables.SPEAKERS)
                        .where(Qualified.SESSIONS_SPEAKERS_SESSION_ID + "=?", sessionId);
            }
            case SESSIONS_ID_TRACKS: {
                final String sessionId = Sessions.getSessionId(uri);
                return builder.table(Tables.SESSIONS_TRACKS_JOIN_TRACKS)
                        .mapToTable(Tracks._ID, Tables.TRACKS)
                        .mapToTable(Tracks.TRACK_ID, Tables.TRACKS)
                        .where(Qualified.SESSIONS_TRACKS_SESSION_ID + "=?", sessionId);
            }
            case SPEAKERS: {
                return builder.table(Tables.SPEAKERS);
            }
            case SPEAKERS_ID: {
                final String speakerId = Speakers.getSpeakerId(uri);
                return builder.table(Tables.SPEAKERS)
                        .where(Speakers.SPEAKER_ID + "=?", speakerId);
            }
            case SPEAKERS_ID_SESSIONS: {
                final String speakerId = Speakers.getSpeakerId(uri);
                return builder.table(Tables.SESSIONS_SPEAKERS_JOIN_SESSIONS_BLOCKS_ROOMS)
                        .mapToTable(Sessions._ID, Tables.SESSIONS)
                        .mapToTable(Sessions.SESSION_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.BLOCK_ID, Tables.SESSIONS)
                        .mapToTable(Sessions.ROOM_ID, Tables.SESSIONS)
                        .where(Qualified.SESSIONS_SPEAKERS_SPEAKER_ID + "=?", speakerId);
            }
            case VENDORS: {
                return builder.table(Tables.VENDORS_JOIN_TRACKS)
                        .mapToTable(Vendors._ID, Tables.VENDORS)
                        .mapToTable(Vendors.TRACK_ID, Tables.VENDORS);
            }
            case VENDORS_STARRED: {
                return builder.table(Tables.VENDORS_JOIN_TRACKS)
                        .mapToTable(Vendors._ID, Tables.VENDORS)
                        .mapToTable(Vendors.TRACK_ID, Tables.VENDORS)
                        .where(Vendors.VENDOR_STARRED + "=1");
            }
            case VENDORS_SEARCH: {
                final String query = Vendors.getSearchQuery(uri);
                return builder.table(Tables.VENDORS_SEARCH_JOIN_VENDORS_TRACKS)
                        .map(Vendors.SEARCH_SNIPPET, Subquery.VENDORS_SNIPPET)
                        .mapToTable(Vendors._ID, Tables.VENDORS)
                        .mapToTable(Vendors.VENDOR_ID, Tables.VENDORS)
                        .mapToTable(Vendors.TRACK_ID, Tables.VENDORS)
                        .where(VendorsSearchColumns.BODY + " MATCH ?", query);
            }
            case VENDORS_ID: {
                final String vendorId = Vendors.getVendorId(uri);
                return builder.table(Tables.VENDORS_JOIN_TRACKS)
                        .mapToTable(Vendors._ID, Tables.VENDORS)
                        .mapToTable(Vendors.TRACK_ID, Tables.VENDORS)
                        .where(Vendors.VENDOR_ID + "=?", vendorId);
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    private interface Subquery {
        String BLOCK_SESSIONS_COUNT = "(SELECT COUNT(" + Qualified.SESSIONS_SESSION_ID + ") FROM "
                + Tables.SESSIONS + " WHERE " + Qualified.SESSIONS_BLOCK_ID + "="
                + Qualified.BLOCKS_BLOCK_ID + ")";

        String BLOCK_CONTAINS_STARRED = "(SELECT MAX(" + Qualified.SESSIONS_STARRED + ") FROM "
                + Tables.SESSIONS + " WHERE " + Qualified.SESSIONS_BLOCK_ID + "="
                + Qualified.BLOCKS_BLOCK_ID + ")";

        String TRACK_SESSIONS_COUNT = "(SELECT COUNT(" + Qualified.SESSIONS_TRACKS_SESSION_ID
                + ") FROM " + Tables.SESSIONS_TRACKS + " WHERE "
                + Qualified.SESSIONS_TRACKS_TRACK_ID + "=" + Qualified.TRACKS_TRACK_ID + ")";

        String TRACK_VENDORS_COUNT = "(SELECT COUNT(" + Qualified.VENDORS_VENDOR_ID + ") FROM "
                + Tables.VENDORS + " WHERE " + Qualified.VENDORS_TRACK_ID + "="
                + Qualified.TRACKS_TRACK_ID + ")";

        String SESSIONS_SNIPPET = "snippet(" + Tables.SESSIONS_SEARCH + ",'{','}','\u2026')";
        String VENDORS_SNIPPET = "snippet(" + Tables.VENDORS_SEARCH + ",'{','}','\u2026')";
    }

    /**
     * {@link AquaNotesDbContract} fields that are fully qualified with a specific
     * parent {@link Tables}. Used when needed to work around SQL ambiguity.
     */
    private interface Qualified {
        String SESSIONS_SESSION_ID = Tables.SESSIONS + "." + Sessions.SESSION_ID;
        String SESSIONS_BLOCK_ID = Tables.SESSIONS + "." + Sessions.BLOCK_ID;
        String SESSIONS_ROOM_ID = Tables.SESSIONS + "." + Sessions.ROOM_ID;

        String SESSIONS_TRACKS_SESSION_ID = Tables.SESSIONS_TRACKS + "."
                + SessionsTracks.SESSION_ID;
        String SESSIONS_TRACKS_TRACK_ID = Tables.SESSIONS_TRACKS + "."
                + SessionsTracks.TRACK_ID;

        String SESSIONS_SPEAKERS_SESSION_ID = Tables.SESSIONS_SPEAKERS + "."
                + SessionsSpeakers.SESSION_ID;
        String SESSIONS_SPEAKERS_SPEAKER_ID = Tables.SESSIONS_SPEAKERS + "."
                + SessionsSpeakers.SPEAKER_ID;

        String VENDORS_VENDOR_ID = Tables.VENDORS + "." + Vendors.VENDOR_ID;
        String VENDORS_TRACK_ID = Tables.VENDORS + "." + Vendors.TRACK_ID;

        @SuppressWarnings("hiding")
        String SESSIONS_STARRED = Tables.SESSIONS + "." + Sessions.SESSION_STARRED;

        String TRACKS_TRACK_ID = Tables.TRACKS + "." + Tracks.TRACK_ID;
        String BLOCKS_BLOCK_ID = Tables.BLOCKS + "." + Blocks.BLOCK_ID;
    }
}

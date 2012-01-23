package com.heneryh.aquanotes.io;

import java.util.LinkedList;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.heneryh.aquanotes.provider.AquaNotesDbContract;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Data;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Outlets;
import com.heneryh.aquanotes.provider.AquaNotesDbContract.Probes;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

// This is a class that will parse the Apex XML response.
// More info on Android XML parsers is available on the net.
//
// The format of the Apex response looks like this:
//
//	<status software="4.02_2H10" hardware="1.0">
//		<hostname>Apex</hostname>
//		<serial>AC4:01405</serial>
//		<date>04/13/2010 09:43:44</date>
//
//		<power>
//		<failed>03/13/2010 09:25:29</failed>
//		<restored>03/14/2010 01:48:26</restored>
//		</power>
//
//		<probes>
//
//			<probe>
//		  		<name>Temp</name>
//				<value>78.1</value>
//			</probe>
//
//			<probe>
//				<name>pH</name>
//				<value>8.02</value>
//			</probe>
//
//			<probe>
//				<name>Amp_3</name>
//				<value>01.0 </value>
//			</probe>
//		</probes>
//
//		<outlets>
//
//			<outlet>
//				<name>WhiteLEDs</name>
//				<state>MaxWhite</state>
//			</outlet>
//
//			<outlet>
//				<name>BlueLEDs</name>
//				<state>MaxBlue</state>
//			</outlet>
//
//			<outlet>
//				<name>VarSpd3_I3</name>
//				<state>PF3</state>
//			</outlet>
//
//			<outlet>
//				<name>VarSpd4_I4</name>
//				<state>PF4</state>
//			</outlet>
//
//			<outlet>
//				<name>SndAlm_I6</name>
//				<state>AOF</state>
//			</outlet>
//
//			<outlet>
//				<name>SndWrn_I7</name>
//				<state>AOF</state>
//			</outlet>
//
//			<outlet>
//				<name>EmailAlm_I5</name>
//				<state>AOF</state>
//			</outlet>
//
//			<outlet>
//				<name>Actinics</name>
//				<state>AON</state>
//			</outlet>
//
//			<outlet>
//				<name>MH</name>
//				<state>AOF</state>
//			</outlet>
//
//			<outlet>
//				<name>T5s</name>
//				<state>AON</state>
//			</outlet>
//
//			<outlet>
//				<name>TunzeLeft</name>
//				<state>AON</state>
//			</outlet>
//
//			<outlet>
//				<name>Heater</name>
//				<state>AOF</state>
//			</outlet>
//
//			<outlet>
//				<name>FugeLights</name>
//				<state>AOF</state>
//			</outlet>
//
//			<outlet>
//				<name>ClosedLoop</name>
//				<state>AON</state>
//			</outlet>
//
//			<outlet>
//				<name>TunzeRight</name>
//				<state>AON</state>
//			</outlet>
//		</outlets>
//	</status>  

public class ApexStateXMLParser extends DefaultHandler {

	// ===========================================================
	// Fields
	// ===========================================================
	private static final boolean LOGD = true;
	private static final String LOG_TAG = "ApexStateXMLParser";

	private boolean in_date_tag = false;
	private boolean in_serial_tag = false;
	private boolean in_probes_tag = false;
	private boolean in_outlets_tag = false;
	private boolean in_probe_tag = false;
	private boolean in_outlet_tag = false;
	private boolean in_name_tag = false;
	private boolean in_value_tag = false;
	private boolean in_xstatus_tag = false;
	private boolean in_state_tag = false;
	private boolean in_devid_tag = false;
	
	private StringBuilder builder;

	
	// This is used to hold the current probe or outlet name so that when
	// we get to the value/state we can add the appropriate object to the 
	// database.
	public Date timeStamp;
	public String deviceType = null;
	private String currentName = null;
	private String currentDeviceID = null;
	private String currentValue = null;
	private String currentState = null;
	private String version = null;
	
	Uri controllerUri;
	ContentResolver mResolver;

	public ApexStateXMLParser(ContentResolver resolver, Uri ctlr) {
		mResolver = resolver;
		controllerUri = ctlr;
	}
	
	// ===========================================================
	// Methods
	// ===========================================================
	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		builder = new StringBuilder();
	}

	@Override
	public void endDocument() throws SAXException {
		// if the version is less than 4.05 then tag the device type with a special code.
		// I do this in the endDocument phase because I can't be sure the device type is filled
		// in prior to the version.  I don't want the 'old' tag to get put into the device type
		// then get overridden with the device type.  I'm essentially reusing the device type
		// to tag an old version.  Not good, but a hack.
		String versionSubstring = version.substring(0,4);
		Float versionNumber=(float) 0.0;
		Float minVersion = new Float(4.05);
		
		try { versionNumber = Float.valueOf(versionSubstring);}
		catch (NumberFormatException e) {} // need to error checking here.
		
		if (versionNumber.compareTo(minVersion)<0) {
			// Update the database to tag the fact that the firmware is old and not supported
			ContentValues values = new ContentValues();
	        values.clear();
			values.put(AquaNotesDbContract.Controllers.MODEL, "Apex-old");
			try {
				mResolver.update(controllerUri, values, null, null);
			} catch (SQLException e) {
				Log.e(LOG_TAG, "Updating controller to old version, tagging the database.", e);
			}
		}
		
	}

	/** Gets be called on opening tags like:
	 * <tag>
	 * Can provide attribute(s), when xml is of the form:
	 *     <tag attribute="attributeValue">*/
	
	// For us, we'll just note where we are in the tree.
	// This knowledge will help us when we close tags later.
	@Override
	public void startElement(String namespaceURI, String localName,
			String qName, Attributes atts) throws SAXException {
		super.startElement(namespaceURI, localName, qName, atts);
		if (localName.equalsIgnoreCase("date")) {
			this.in_date_tag = true;
		} else if (localName.equalsIgnoreCase("serial")) {
			this.in_serial_tag = true;
		} else if (localName.equalsIgnoreCase("probes")) {
			this.in_probes_tag = true;
		}else if (localName.equalsIgnoreCase("outlets")) {
			this.in_outlets_tag = true;
		}else if (localName.equalsIgnoreCase("probe")) {
			this.in_probe_tag = true;
		}else if (localName.equalsIgnoreCase("outlet")) {
			this.in_outlet_tag = true;
		}else if (localName.equalsIgnoreCase("name")) {
			this.in_name_tag = true;
		}else if (localName.equalsIgnoreCase("deviceID")) {
			this.in_devid_tag = true;
		}else if (localName.equalsIgnoreCase("xstatus")) {
			this.in_xstatus_tag = true;
		}else if (localName.equalsIgnoreCase("value")) {
			this.in_value_tag = true;
		}else if (localName.equalsIgnoreCase("state")) {
			this.in_state_tag = true;
		}else if (localName.equalsIgnoreCase("status")) {
			version = atts.getValue("software");
		}
	}

	/** Gets be called on closing tags like:
	 * </tag> */
	@Override
	public void endElement(String namespaceURI, String localName, String qName)
	throws SAXException {

		super.endElement(namespaceURI, localName, qName);

		// For each end-tag, we'll check to make sure we are where we think we should
		// be by checking the state variables.  If in the correct state, we'll do the 
		// appropriate action for that end-tag.  We'll also need to update the state.
		if (localName.equalsIgnoreCase("probe")) {
			if(this.in_probes_tag & this.in_probe_tag & (currentName!=null)  & (currentValue!=null)) {
				/////////////////////////////////
				// We've gotten to the end of a probe and assume we've already captured the name and value.
				/////////////////////////////////
				// Step 1 is to get the probe record number so we can link to the proper probe
				Cursor cursor = null;
				Integer parentRecord = 0;
				Integer resourceID = 0;
				//float currentProbeValue = Float.parseFloat(currentProbeValueString);
//				Uri activeProbesUri = Uri.withAppendedPath(controllerUri, Probes.TWIG_PROBES_NAME);
//				Uri probeByNameUri = Uri.withAppendedPath(activeProbesUri,Uri.encode(currentName));
				Uri probeByNameUri = Probes.buildQueryProbeByNameUri(controllerUri, currentName);
				int controllerId = Integer.parseInt(controllerUri.getPathSegments().get(1));

				try {
					cursor = mResolver.query(probeByNameUri, ProbesQuery.PROJECTION, null, null, null);
					if (cursor == null || !cursor.moveToFirst()) {
						Log.d(LOG_TAG, "Probe query did not return an existing probe.");	
						Log.d(LOG_TAG, "Inserting a new one.");	
						// if query fails then what?  We'll need to insert a probe record prior
						// to inserting this data record
						ContentValues values = new ContentValues();
						//values.put(BaseColumns._ID, xxx);  auto-generate key
						values.put(AquaNotesDbContract.Probes.CONTROLLER_ID, controllerId);
						values.put(AquaNotesDbContract.Probes.NAME, currentName);
						Uri insertProbeUri = AquaNotesDbContract.Probes.buildInsertProbeUri(controllerUri);
						mResolver.insert(insertProbeUri, values);
						
						// ugh, now I have to get the record again??
						if (cursor != null) {
							cursor.close();
						}
						cursor = mResolver.query(probeByNameUri, ProbesQuery.PROJECTION, null, null, null);
						if (cursor == null || !cursor.moveToFirst()) {
								// This is a serious error, we just put it in, it must be there.
						}
					} else {
						Log.d(LOG_TAG, "Probe query returned an existing probe.");	
					}
					// grab the probe record #
					parentRecord = cursor.getInt(ProbesQuery._ID);
					resourceID = cursor.getInt(ProbesQuery.RESOURCE_ID);
				} catch (SQLException e) {
					Log.e("Database error", "Couldn't parse the returned probe field", e);	
				}
				finally {
					if (cursor != null) {
						cursor.close();
					}
				} // end of database query for the probe by name

				// Step #2 will be to insert the new records
				ContentValues values2 = new ContentValues();
				//values.put(BaseColumns._ID, xxx);  auto-generate key
//				values2.put(ProbeDataColumns.CONTROLLER_ID, controllerId); // redundant but easier without join capability
//				values2.put(ProbeDataColumns.PROBENAME, currentName); // redundant but easier without join capability
//				values2.put(ProbeDataColumns.DATUM_TYPE, 1); // 1=probe, 0=outlet
				values2.put(AquaNotesDbContract.Data.VALUE, currentValue);
				values2.put(AquaNotesDbContract.Data.TIMESTAMP, timeStamp.getTime());
				values2.put(AquaNotesDbContract.Data.PARENT_ID, parentRecord);
				Uri probeUri = Data.buildInsertProbeDataUri(controllerUri, parentRecord);
				mResolver.insert(probeUri, values2);

				/////////////////////////////////
				currentName="";
				currentDeviceID="";
				currentValue="";
			}
			this.in_probe_tag = false;
		}else if (localName.equalsIgnoreCase("outlet")) {
			if(this.in_outlets_tag & this.in_outlet_tag & (currentName!=null) & (currentDeviceID!=null) & (currentState!=null)) {
				/////////////////////////////////
				// We've got a value which we assume came after a name, so process the name/value pair
				/////////////////////////////////
				// Step 1 is to get the outlet record number so we can link to the proper outlet
				Cursor cursor = null;
				Integer parentRecord = 0;
				Integer resourceID = 0;

//				Uri activeOutletsUri = Uri.withAppendedPath(controllerUri, Probes.TWIG_OUTLETS_DEVICE_ID);
//				Uri outletByDevIdUri = Uri.withAppendedPath(activeOutletsUri,Uri.encode(currentDeviceID));
				Uri outletByDevIdUri = Outlets.buildQueryOutletXByDeviceIdUri(controllerUri, currentDeviceID);
				
				int controllerId = Integer.parseInt(controllerUri.getPathSegments().get(1));

				try {
					cursor = mResolver.query(outletByDevIdUri, ProbesQuery.PROJECTION, null, null, null);
					if (cursor == null || !cursor.moveToFirst()) {
						Log.d(LOG_TAG, "Outlet query did not return an existing outlet.");	
						Log.d(LOG_TAG, "Inserting a new one.");	
						// if query fails then what?  We'll need to insert a probe record prior
						// to inserting this data record
						ContentValues values = new ContentValues();
						//values.put(BaseColumns._ID, xxx);  auto-generate key
						values.put(AquaNotesDbContract.Outlets.CONTROLLER_ID, controllerId);
						values.put(AquaNotesDbContract.Outlets.NAME, currentName);
						if(deviceType.equalsIgnoreCase("AC3")) {
							values.put(AquaNotesDbContract.Outlets.DEVICE_ID, currentName);
						} else {
							values.put(AquaNotesDbContract.Outlets.DEVICE_ID, currentDeviceID);
						}
						Uri insertOutletUri = Outlets.buildInsertOutletUri(controllerUri);
						mResolver.insert(insertOutletUri, values);
						
						// ugh, now I have to get the record again??
						if (cursor != null) {
							cursor.close();
						}
						cursor = mResolver.query(outletByDevIdUri, ProbesQuery.PROJECTION, null, null, null);
						if (cursor == null || !cursor.moveToFirst()) {
								// This is a serious error, we just put it in, it must be there.
						}
					} else {
						Log.d(LOG_TAG, "Probe query returned an existing outlet.");	
					}
					// grab the probe record #
					parentRecord = cursor.getInt(ProbesQuery.CONTROLLER_ID);
					resourceID = cursor.getInt(ProbesQuery.RESOURCE_ID);
					
					
				} catch (SQLException e) {
					Log.e("Database error", "Couldn't parse the returned outlet field", e);	
				}
				finally {
					if (cursor != null) {
						cursor.close();
					}
				} // end of database query for the probe by name

				// Step #2 will be to insert the new records
				ContentValues values2 = new ContentValues();
				
				//values.put(BaseColumns._ID, xxx);  auto-generate key
//				values2.put(ProbeDataColumns.CONTROLLER_ID, controllerId); // redundant but easier without join capability
//				values2.put(ProbeDataColumns.PROBENAME, currentName); // redundant but easier without join capability
//				values2.put(ProbeDataColumns.DATUM_TYPE, 1); // 1=probe, 0=outlet
				values2.put(AquaNotesDbContract.Data.VALUE, currentValue);
				values2.put(AquaNotesDbContract.Data.TIMESTAMP, timeStamp.getTime());
				values2.put(AquaNotesDbContract.Data.PARENT_ID, parentRecord);
				Uri outletUri = Data.buildInsertOutletDataUri(controllerUri, parentRecord);
				mResolver.insert(outletUri, values2);

				/////////////////////////////////
				currentName="";
				currentState="";
				currentDeviceID="";
			}
			this.in_outlet_tag = false;
		}else if (localName.equalsIgnoreCase("value")) {
			this.in_value_tag = false;
			currentValue = builder.toString().trim();
		}else if (localName.equalsIgnoreCase("state")) {
			this.in_state_tag = false;
			currentState = builder.toString().trim();
		}else if (localName.equalsIgnoreCase("deviceID")) {
			this.in_devid_tag = false;
			currentDeviceID = builder.toString().trim();
		} else if (localName.equalsIgnoreCase("name")) {
			this.in_name_tag = false;
			currentName=builder.toString().trim();
		}else if (localName.equalsIgnoreCase("probes")) {
			this.in_probes_tag = false;
		}else if (localName.equalsIgnoreCase("outlets")) {
			this.in_outlets_tag = false;
		}else if (localName.equalsIgnoreCase("serial")) {
			//	<serial>AC4:01405</serial>
			deviceType=currentName=builder.toString().trim().substring(0, 3);
			
			ContentValues values = new ContentValues();
	        values.clear();
			values.put(AquaNotesDbContract.Controllers.MODEL, deviceType);
			try {
				mResolver.update(controllerUri, values, null, null);
			} catch (SQLException e) {
				Log.e(LOG_TAG, "Updating controller type", e);
			}

			this.in_serial_tag = false;
		}else if (localName.equalsIgnoreCase("date")) {
			// <date>04/13/2010 09:43:44</date>
			SimpleDateFormat dateFormater = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
			try {
				timeStamp = dateFormater.parse(builder.toString().trim()); 
			} catch (java.text.ParseException e) {
				Log.e("XML Parser", "Couldn't parse the returned date field", e);
				timeStamp.setTime(0);
			}
			
			// Update the database with the new timestamp
			ContentValues values = new ContentValues();
	        values.clear();
			values.put(AquaNotesDbContract.Controllers.LAST_UPDATED, timeStamp.getTime());
			try {
				mResolver.update(controllerUri, values, null, null);
			} catch (SQLException e) {
				Log.e(LOG_TAG, "Updating controller timestamp", e);
			}

			this.in_date_tag = false;
		}

		builder.setLength(0);    
	}

	/** Gets be called on the following structure:
	 * <tag>characters</tag> */
	@Override
	public void characters(char[] ch, int start, int length)
	throws SAXException {
		super.characters(ch, start, length);
		builder.append(ch, start, length);
	}

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
        int WIFI_URL = 3;
        int WIFI_SSID = 4;
        int USER = 5;
        int PW = 6;
        int LAST_UPDATED = 7;
        int UPDATE_INTERVAL = 8;
        int DB_SAVE_DAYS = 9;
        int CONTROLLER_TYPE = 10;
    }
    
	private interface ProbesQuery {
        String[] PROJECTION = {
        	//  String PROBE_ID = "_id";
        	//  String PROBE_NAME = "probe_name";
        	//  String DEVICE_ID = "device_id";
        	//  String TYPE = "probe_type";
        	//  String RESOURCE_ID = "resource_id";
        	//  String CONTROLLER_ID = "controller_id";
                BaseColumns._ID,
                AquaNotesDbContract.Probes.NAME,
                AquaNotesDbContract.Probes.RESOURCE_ID,
                AquaNotesDbContract.Probes.CONTROLLER_ID,
        };
        
        int _ID = 0;
        int NAME = 1;
        int RESOURCE_ID = 2;
        int CONTROLLER_ID = 3;
    }
	
	private interface DataQuery {
        String[] PROJECTION = {
//              String DATA_ID = "_id";
//              String VALUE = "value";
//              String TIMESTAMP = "timestamp";
//              String PROBE_ID = "probe_id";
                BaseColumns._ID,
                AquaNotesDbContract.Data.TYPE,
                AquaNotesDbContract.Data.VALUE,
                AquaNotesDbContract.Data.TIMESTAMP,
                AquaNotesDbContract.Data.PARENT_ID,
        };
        
        int _ID = 0;
        int TYPE = 1;
        int VALUE = 1;
        int TIMESTAMP = 2;
        int PROBE_ID = 3;
    }

}

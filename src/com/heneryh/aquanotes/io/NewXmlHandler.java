package com.heneryh.aquanotes.io;
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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Abstract class that handles reading and parsing an {@link XmlPullParser} into
 * a set of {@link ContentProviderOperation}. It catches recoverable network
 * exceptions and rethrows them as {@link HandlerException}. Any local
 * {@link ContentProvider} exceptions are considered unrecoverable.
 * <p>
 * This class is only designed to handle simple one-way synchronization.
 */
public class NewXmlHandler {

    public NewXmlHandler() {
    }

    /**
     * Parse the given {@link XmlPullParser}, turning into a series of
     * {@link ContentProviderOperation} that are immediately applied using the
     * given {@link ContentResolver}.
     */
    public static void parseAndStore(InputStream input, Uri controllerUri, DefaultHandler parser)
            throws HandlerException {
    	try {
    		/* Get a SAXParser from the SAXPArserFactory. */
    		SAXParserFactory spf = SAXParserFactory.newInstance();
    		SAXParser sp = spf.newSAXParser();

    		/* Get the XMLReader of the SAXParser we created. */
    		XMLReader xr = sp.getXMLReader();

    		xr.setContentHandler(parser);

    		/* Parse the xml-data from our URL and store in the db. */
    		xr.parse(new InputSource(input));

    		// After parsing, the parse-handler should have populated the database.
    	} catch (HandlerException e) {
    		throw e;
    	} catch (SAXException e) {
    		throw new HandlerException("Problem parsing XML response", e);
    	} catch (ParserConfigurationException e) {
    		throw new HandlerException("Problem parsing XML response", e);
    	} catch (IOException e) {
    		throw new HandlerException("Problem reading response", e);
//    	} catch (RemoteException e) {
//    		// Failed binder transactions aren't recoverable
//    		throw new RuntimeException("Problem applying batch operation", e);
//    	} catch (OperationApplicationException e) {
//    		// Failures like constraint violation aren't recoverable
//    		// TODO: write unit tests to exercise full provider
//    		// TODO: consider catching version checking asserts here, and then
//    		// wrapping around to retry parsing again.
//    		throw new RuntimeException("Problem applying batch operation", e);
    	}
    }
    
    /**
     * General {@link IOException} that indicates a problem occured while
     * parsing or applying an {@link XmlPullParser}.
     */
    public static class HandlerException extends IOException {
        public HandlerException(String message) {
            super(message);
        }

        public HandlerException(String message, Throwable cause) {
            super(message);
            initCause(cause);
        }

        @Override
        public String toString() {
            if (getCause() != null) {
                return getLocalizedMessage() + ": " + getCause();
            } else {
                return getLocalizedMessage();
            }
        }
    }
}


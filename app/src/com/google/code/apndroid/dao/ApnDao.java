/*
 * This file is part of APNdroid.
 *
 * APNdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * APNdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with APNdroid. If not, see <http://www.gnu.org/licenses/>.
 */

package com.google.code.apndroid.dao;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import com.google.code.apndroid.Constants;
import com.google.code.apndroid.model.ApnInfo;
import com.google.code.apndroid.model.ExtendedApnInfo;

import java.util.*;

/**
 * @author Martin Adamek <martin.adamek@gmail.com>
 * @author Dmitry Pavlov <pavlov.dmitry.n@gmail.com>
 */
public final class ApnDao implements ConnectionDao, ApnInformationDao {

//    all available fields from apn table
//    [_id,name,numeric,mcc,mnc,apn,user,server,password,proxy,port,mmsproxy,mmsport,mmsc,authtype,type,current]

    private static final String SUFFIX = "apndroid";
    private static final String ID = "_id";
    private static final String APN = "apn";
    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String PROXY = "proxy";
    private static final String PORT = "port";
    private static final String MMSC = "mmsc";
    private static final String MCC = "mcc";
    private static final String MNC = "mnc";
    private static final String AUTH_TYPE = "authtype";

    // from frameworks/base/core/java/android/provider/Telephony.java
    private static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
    private static final Uri PREFERRED_APN_URI = Uri.parse("content://telephony/carriers/preferapn");
    private static final String PREFER_APN_ID_KEY = "apn_id";
    private static final String DB_LIKE_SUFFIX = "%" + SUFFIX;
    private static final String DB_LIKE_TYPE_SUFFIX = "%" +SUFFIX + "%";

    private static final String[] DB_LIKE_MMS_TYPE_SUFFIX = new String[]{"%mms"+SUFFIX+"%"};    

    private final ContentResolver mContentResolver;
    private int mMmsTarget = Constants.STATE_ON;
    private boolean mDisableAll = false;

    public ApnDao(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    public boolean isDataEnabled() {
        return countDisabledApns() <= 0;
    }

    public boolean isMmsEnabled() {
        return !(countMmsApns() > 0 && countDisabledMmsApns() > 0);
    }

    public boolean setDataEnabled(Context context, boolean enable) {
        return setDataEnabled(context, enable, enable);
    }

    public boolean setDataEnabled(Context context, boolean enableData, boolean enableMms) {
        setMmsEnabled(enableMms);
        if (enableData) {
        	return enableAllInDb();
        } else {
            return disableAllInDb();
        }
    }

    public boolean setMmsEnabled(boolean enable) {
        if (enable) {
            return enableApnList(selectDisabledMmsApns());
        } else {
            final List<ApnInfo> mmsList = selectEnabledMmsApns();
            return mmsList.size() != 0 && disableApnList(mmsList);
        }
    }

    public void setMmsTarget(int mmsTarget) {
        this.mMmsTarget = mmsTarget;
    }

    public void setDisableAllApns(boolean disableAll) {
        this.mDisableAll = disableAll;
    }

    private List<ApnInfo> getEnabledApnsMap() {
        String query;
        boolean disableAll = this.mDisableAll;
        String disableAllQuery = disableAll ? null : "current is not null";
        if (mMmsTarget == Constants.STATE_OFF) {
            query = disableAllQuery;
        } else {
            query = "(not lower(type)='mms' or type is null)";
            if (!disableAll) {
                query += " and " + disableAllQuery;
            }
        }
        return selectApnInfo(query, null);
    }

    private List<ApnInfo> getDisabledApnsMap() {
        return selectApnInfo("apn like ? or type like ?", new String[] { DB_LIKE_SUFFIX, DB_LIKE_TYPE_SUFFIX });
    }

    private List<ApnInfo> selectApnInfo(String whereQuery, String[] whereParams) {
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(CONTENT_URI, new String[] { ID, APN, TYPE }, whereQuery, whereParams, null);

            if (cursor == null) return Collections.emptyList();
            
            return createApnList(cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private List<ExtendedApnInfo> selectExtendedApnInfo(String whereQuery, String[] whereParams) {
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(CONTENT_URI,
                    new String[] { ID, APN, TYPE, NAME, PROXY, PORT, MMSC, MCC, MNC, AUTH_TYPE },
                    whereQuery, whereParams, null);

            if (cursor == null) return Collections.emptyList();

            return createExtendedApnList(cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean enableAllInDb() {
        List<ApnInfo> apns = getDisabledApnsMap();
        return enableApnList(apns);
    }

    /**
     * Creates list of apn dtos from a DB cursor
     *
     * @param mCursor
     *            db cursor with select result set
     * @return list of APN dtos
     */
    private List<ApnInfo> createApnList(Cursor mCursor) {
        List<ApnInfo> result = new ArrayList<ApnInfo>();
        mCursor.moveToFirst();
        while (!mCursor.isAfterLast()) {
            long id = mCursor.getLong(0);
            String apn = mCursor.getString(1);
            String type = mCursor.getString(2);
            result.add(new ApnInfo(id, apn, type));
            mCursor.moveToNext();
        }
        return result;
    }

    private List<ExtendedApnInfo> createExtendedApnList(Cursor cursor) {
        List<ExtendedApnInfo> result = new LinkedList<ExtendedApnInfo>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()){
            ExtendedApnInfo info = new ExtendedApnInfo();
            info.setId(cursor.getLong(0));
            info.setType(cursor.getString(1));
            info.setType(cursor.getString(2));
            info.setName(cursor.getString(3));
            info.setProxy(cursor.getString(4));
            info.setPort(cursor.getString(5));
            info.setMmsc(cursor.getString(6));
            info.setMmc(cursor.getString(7));
            info.setMnc(cursor.getString(8));
            info.setAuthType(cursor.getString(9));
            result.add(info);

            cursor.moveToNext();
        }
        return result;
    }

    /**
     * Tries to disable apn's according to user preferences.
     * 
     * @return {@code true} if one o more apns changed and {@code false} if all APNs did not changed their states
     */
    private boolean disableAllInDb() {
        List<ApnInfo> apns = getEnabledApnsMap();

        // when selected apns is empty
        if (apns.isEmpty()) {
            return countDisabledApns() > 0;
        }

        return disableApnList(apns);
    }

    /**
     * Use this one if you have fresh list of APNs already and you can save one query to DB
     * 
     * @param apns
     *            list of apns data to modify
     * @return {@code true} if switch was successfull and {@code false} otherwise
     */
    private boolean enableApnList(List<ApnInfo> apns) {
        final ContentResolver contentResolver = this.mContentResolver;
        for (ApnInfo apnInfo : apns) {
            ContentValues values = new ContentValues();
            String newApnName = removeSuffix(apnInfo.getApn());
            values.put(APN, newApnName);
            String newApnType = removeComplexSuffix(apnInfo.getType());
            if ("".equals(newApnType)) {
                values.putNull(TYPE);
            } else {
                values.put(TYPE, newApnType);
            }
            contentResolver.update(CONTENT_URI, values, ID + "=?", new String[] { String.valueOf(apnInfo.getId()) });

        }
        return true;// we always return true because in any situation we can
        // reset all apns to initial state
    }

    private boolean disableApnList(List<ApnInfo> apns) {
        final ContentResolver contentResolver = this.mContentResolver;
        for (ApnInfo apnInfo : apns) {
            ContentValues values = new ContentValues();
            String newApnName = addSuffix(apnInfo.getApn());
            values.put(APN, newApnName);
            String newApnType = addComplexSuffix(apnInfo.getType());
            values.put(TYPE, newApnType);
            contentResolver.update(CONTENT_URI, values, ID + "=?", new String[] { String.valueOf(apnInfo.getId()) });
        }
        return true;
    }

    private int countDisabledApns() {
        return executeCountQuery("apn like ? or type like ?", new String[] { DB_LIKE_SUFFIX, DB_LIKE_TYPE_SUFFIX });
    }

    private int countMmsApns() {
        return executeCountQuery("(type like ? or type like '%mms%')" + getCurrentCriteria(), DB_LIKE_MMS_TYPE_SUFFIX);
    }

    private int countDisabledMmsApns() {
        return executeCountQuery("type like ?", DB_LIKE_MMS_TYPE_SUFFIX);
    }

    private int executeCountQuery(String whereQuery, String[] whereParams) {
        Cursor cursor = null;
        try {
            cursor = mContentResolver.query(CONTENT_URI, new String[] { "count(*)" }, whereQuery, whereParams, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                return -1;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private List<ApnInfo> selectDisabledMmsApns() {
        return selectApnInfo("type like ?", DB_LIKE_MMS_TYPE_SUFFIX);
    }

    private List<ApnInfo> selectEnabledMmsApns() {
        return selectApnInfo("type like ? and type not like ?" + getCurrentCriteria(), new String[] { "%mms%", DB_LIKE_MMS_TYPE_SUFFIX[0] });
    }

    private String getCurrentCriteria() {
        return mDisableAll ? "" : " and current is not null";
    }

    public static String addSuffix(String currentName) {
        if (currentName == null) {
            return SUFFIX;
        } else {
            return currentName + SUFFIX;
        }
    }

    public String addComplexSuffix(String complexString){
        if (complexString == null) return SUFFIX;

        StringBuilder builder = new StringBuilder(complexString.length());
        StringTokenizer tokenizer = new StringTokenizer(complexString, ",");
        boolean leaveMmsEnabled = mMmsTarget == Constants.STATE_OFF;
        while (tokenizer.hasMoreTokens()){
            String str = tokenizer.nextToken().trim();
            if (leaveMmsEnabled && "mms".equals(str)){
                builder.append(str);
            }else{
                builder.append(addSuffix(str));
            }
            if (tokenizer.hasMoreTokens()){
                builder.append(",");
            }
        }
        return builder.toString();
    }

    public static String removeSuffix(String currentName) {
        if (currentName == null) {
            return "";
        }
        if (currentName.endsWith(SUFFIX)) {
            return currentName.substring(0, currentName.length() - SUFFIX.length());
        } else {
            return currentName;
        }
    }

    public static String removeComplexSuffix(String complexString){
        if (complexString == null) return "";

        StringBuilder builder = new StringBuilder(complexString.length());
        StringTokenizer tokenizer = new StringTokenizer(complexString, ",");
        while (tokenizer.hasMoreTokens()){
            builder.append(removeSuffix(tokenizer.nextToken().trim()));
            if (tokenizer.hasMoreTokens()){
                builder.append(",");
            }
        }
        return builder.toString();
    }

    @Override
    public List<ExtendedApnInfo> findAllApns() {
        return selectExtendedApnInfo(null, null);
    }

    @Override
    public Long getCurrentActiveApnId() {
        Cursor cursor = mContentResolver.query(PREFERRED_APN_URI, new String[]{ID}, null, null, null);
        cursor.moveToFirst();
        if (!cursor.isAfterLast()){
            return cursor.getLong(0);
        }
        return null;
    }

}
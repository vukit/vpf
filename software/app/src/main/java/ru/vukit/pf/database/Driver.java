package ru.vukit.pf.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class Driver {

    private static Driver INSTANCE = null;

    private final SQLiteDatabase db;

    final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Driver(Context ctx) {
        DataBaseHelper databaseHelper = DataBaseHelper.getInstance(ctx);
        db = databaseHelper.getWritableDatabase();
    }

    public static synchronized Driver getInstance(@Nullable Context ctx) {
        if (ctx == null) {
            return INSTANCE;
        }
        if (INSTANCE == null) {
            INSTANCE = new Driver(ctx);
        }
        return INSTANCE;
    }

    public static final class DataBaseContract {

        private DataBaseContract() {
        }

        static final int DATABASE_VERSION = 1;
        static final String DATABASE_NAME = "pf.db";
        static final String[] SQL_CREATE_TABLE_ARRAY = {Feeders.CREATE_TABLE};
        static final String[] SQL_UPGRADE_TABLE_ARRAY = {Feeders.UPGRADE_TABLE};

        public static final class Feeders implements BaseColumns {

            public final static String TABLE_NAME = "feeders";
            public final static String MAC_ADDRESS = "mac_address";
            public final static String NAME = "name";
            final static String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("
                    + _ID + " INTEGER PRIMARY KEY,"
                    + MAC_ADDRESS + " TEXT,"
                    + NAME + " TEXT"
                    + ");";
            final static String UPGRADE_TABLE = "";
        }

    }

    private static class DataBaseHelper extends SQLiteOpenHelper {

        private static DataBaseHelper DatabaseHelperHolder = null;

        public static DataBaseHelper getInstance(Context context) {
            if (DatabaseHelperHolder == null) {
                DatabaseHelperHolder = new DataBaseHelper(context);
            }
            return DatabaseHelperHolder;
        }

        private DataBaseHelper(Context context) {
            super(context, DataBaseContract.DATABASE_NAME, null, DataBaseContract.DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            for (String table : DataBaseContract.SQL_CREATE_TABLE_ARRAY) db.execSQL(table);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            for (String table : DataBaseContract.SQL_UPGRADE_TABLE_ARRAY) db.execSQL(table);
        }
    }

    public void createFeeder(String macAddress, String name) {
        executor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put(DataBaseContract.Feeders.MAC_ADDRESS, macAddress);
            values.put(DataBaseContract.Feeders.NAME, name);
            db.insert(DataBaseContract.Feeders.TABLE_NAME, null, values);
        });
    }

    public HashMap<String, String> selectFeeder(String macAddress) {
        HashMap<String, String> feeder = null;
        Cursor cursor = db.query(
                DataBaseContract.Feeders.TABLE_NAME,
                null,
                DataBaseContract.Feeders.MAC_ADDRESS + " = ?",
                new String[]{macAddress},
                null,
                null,
                null
        );
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            feeder = new HashMap<>();
            feeder.put("id", cursor.getString(cursor.getColumnIndexOrThrow(DataBaseContract.Feeders._ID)));
            feeder.put("mac_address", cursor.getString(cursor.getColumnIndexOrThrow(DataBaseContract.Feeders.MAC_ADDRESS)));
            feeder.put("name", cursor.getString(cursor.getColumnIndexOrThrow(DataBaseContract.Feeders.NAME)));
        }
        cursor.close();
        return feeder;
    }

    public void updateFeeder(String id, String macAddress, String name) {
        executor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put(DataBaseContract.Feeders.MAC_ADDRESS, macAddress);
            values.put(DataBaseContract.Feeders.NAME, name);
            db.update(DataBaseContract.Feeders.TABLE_NAME, values, DataBaseContract.Feeders._ID + " = ?", new String[]{id});

        });
    }

}

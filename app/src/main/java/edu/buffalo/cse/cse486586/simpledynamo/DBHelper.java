package edu.buffalo.cse.cse486586.simpledynamo;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.content.Context;
import android.content.ContentValues;
import android.util.Log;
/**
 * Created by kamakshishete on 31/03/16. Reference:http://www.androidauthority.com/use-sqlite-store-data-app-599743/
 */
public class DBHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "SQLiteKeyValue.db";
    private static final int DATABASE_VERSION = 1;
    public static final String MESSAGE_TABLE_NAME = "message";
    public static final String MESSAGE_ID = "_id";
    public static final String KEY_FIELD = "key";
    public static final String VALUE_FIELD = "value";
    SQLiteDatabase db;
    public static final String TAG = DBHelper.class.getSimpleName();


    public DBHelper(Context context) {

        super(context, DATABASE_NAME , null, DATABASE_VERSION);
        //db = this.getWritableDatabase();
        Log.v(TAG,"Constructor ");
        db = this.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.v(TAG,"Creating database");
        db.execSQL("CREATE TABLE " + MESSAGE_TABLE_NAME + "(" +
                        KEY_FIELD + " TEXT PRIMARY KEY, " +
                        VALUE_FIELD + " TEXT)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + MESSAGE_TABLE_NAME);
        onCreate(db);
    }


    public boolean insertkey(ContentValues values) {
        Log.v(TAG, "Inserting");
        try {
            SQLiteDatabase db = this.getWritableDatabase();
            //ContentValues contentValues = new ContentValues();
            //contentValues.put(MESSAGE_KEY, values.get("key").toString());
            //contentValues.put(MESSAGE_VALUE, values.get("val").toString());
            db.insertWithOnConflict(MESSAGE_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            return true;
        }catch (Exception e){
            Log.v(TAG,"Exception"+ e.toString());
            Log.e(TAG, Log.getStackTraceString(new Exception()));
            return false;
        }
    }





    public Cursor getMessage(String selection) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery( "SELECT * FROM " + MESSAGE_TABLE_NAME + " WHERE " +
                KEY_FIELD + "=?", new String[] { selection } );
        return res;
    }
    public Cursor getAllMessages() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery( "SELECT * FROM " + MESSAGE_TABLE_NAME, null );
        return res;
    }

    public Cursor getPartitionMessage(String selection) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery( "SELECT * FROM " + MESSAGE_TABLE_NAME + " WHERE " +
                KEY_FIELD + "<=?", new String[] { selection } );
        return res;
    }
    public Integer deleteMessage(String selection) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(MESSAGE_TABLE_NAME,
                KEY_FIELD + " = ? ",
                new String[] { selection });
    }
    public Integer DeleteMessagePartition(String selection) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(MESSAGE_TABLE_NAME,
                KEY_FIELD + " <= ? ",
                new String[] { selection });
    }

    public Integer deleteAllMessage() {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(MESSAGE_TABLE_NAME, null, null);


    }


}

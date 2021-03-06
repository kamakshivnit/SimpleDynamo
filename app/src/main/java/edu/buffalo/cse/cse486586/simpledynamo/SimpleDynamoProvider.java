package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.net.SocketTimeoutException;
import java.io.EOFException;
import java.net.SocketException;
import java.net.InetSocketAddress;
import android.content.SharedPreferences;
import java.util.LinkedHashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;


public class SimpleDynamoProvider extends ContentProvider {
    private static final int SERVER_PORT = 10000;
    private static Uri mUri;
    public static final String KEY_FIELD = "key";
    public static final String VALUE_FIELD = "value";
    private static final String TAG = SimpleDynamoProvider.class.getSimpleName();

    public static MatrixCursor globalcursor;
    private DBHelper dbHelper;
    private SQLiteDatabase db=null;
    public static int myPort = 0;
    public static String portStr = "";
    private static boolean creating=true;
    private static boolean cachetime=false;
    private static int[] ChordRing = {11124, 11112, 11108, 11116, 11120};
    private static String[] ChordRingPortStr = {"5562", "5556", "5554", "5558", "5560"};
    private static String[] ChordRingID = {"", "", "", "", ""};

    private static String NodeId="";
    private static int SuccPort1=-1;
    private static int SuccPort2=-1;
    private static int PredPort1=-1;
    private static int PredPort2=-1;
    private static String SuccNodeId1="";
    private static String SuccNodeId2="";
    private static String PredNodeId1="";
    private static String PredNodeId2="";

    private static boolean restartwaitLoop= false;
    private static boolean querywaitLoop= false;
    private static HashMap<Integer,Integer[]> SuccessorMap=new HashMap<Integer, Integer[]>();
    private static LinkedHashMap cache = new LinkedHashMap();
    private static LinkedHashMap CacheUndeliveredMessages= new LinkedHashMap();


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        Log.v(TAG,"Entered delete() with selection"+selection);
        while(cachetime)
        {

        }

        String message= "delete"+"_"+selection;
        if ( (selection.equals("@")) || (selection.equals("*")) )
        {
            //tbd:unhandled case here!
            int succ=dbHelper.deleteAllMessage();
            Log.v(TAG,"Deleting all messages success:"+ succ);
            if (selection.equals("*") )
            {
                String ports_str=Integer.toString(SuccPort1)+"_"+Integer.toString(SuccPort2)+"_"+Integer.toString(PredPort1)+"_"+Integer.toString(PredPort2);
                //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, "delete",ports_str);
                String result=SendMessageToNodes(message, ports_str,false,0);
            }
            return 0;
        }
        int partitionport=DeterminePartition(selection);
        if(myPort==partitionport) {
            if (!restartwaitLoop) {
                //Node restart is happening
                Log.v(TAG, "putting message in a chache as restart is happening");
                cache.put(selection,"delete");
            } else{
                //can delete message in db directly
                int success = dbHelper.deleteMessage(selection);
                Log.v(TAG, "Deleting success at local:" + success);
            }
            //send only to its successor
            String[] port={Integer.toString(SuccPort1),Integer.toString(SuccPort2)};
            for(int i=0;i<2;i++) {
                String result = SendMessageToNodes(message, port[i], false, 0);
                Log.v(TAG, "Result received in delete:" + message+"_"+result+port[i]);
                String desiredresult = "ACK" + selection;
                if ((result != null) && (result.equals(desiredresult))) {
                    //do nothing
                } else {
                    Log.v(TAG, "Delete at node is not successful,hence caching!");
                    String buffer = message + ":" + desiredresult;
                    synchronized (CacheUndeliveredMessages) {
                        CacheUndeliveredMessages.put(buffer, port[i]);
                    }

                }
            }
        }
        else {
            Integer[] succ=SuccessorMap.get(partitionport);
            String[] port={Integer.toString(partitionport),Integer.toString(succ[0]),Integer.toString(succ[1])};
            for(int i=0;i<3;i++) {
                String result = SendMessageToNodes(message, port[i], false, 0);
                Log.v(TAG, "Result received in delete:" + message+"_"+ result+port[i]);
                String desiredresult = "ACK" + selection;
                if ((result != null) && (result.equals(desiredresult))) {
                    //do nothing
                } else {
                    Log.v(TAG, "Delete at node is not successful,hence caching!");
                    String buffer = message + ":" + desiredresult;
                    synchronized (CacheUndeliveredMessages) {
                        CacheUndeliveredMessages.put(buffer, port[i]);
                    }

                }
            }
        }
        return 0;

    }

    public int deleteReplicate(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        //Log.v(TAG,"Entered deleteReplicate() with selection"+selection);
        int succ;
        if(selection.equals("*"))
        {
            succ=dbHelper.deleteAllMessage();
        }
        else
        {
            succ=dbHelper.deleteMessage(selection);
        }

        //Log.v(TAG,"Deleting success at local:"+ succ);

        return 0;

    }
    public int deleteAll() {
        // TODO Auto-generated method stub
        Log.v(TAG,"Entered deleteAll() ");
        int succ;
        succ=dbHelper.deleteAllMessage();

        Log.v(TAG,"Deleting all messages success:"+ succ);
        return 0;


    }


    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        Log.v(TAG, "Entered insert()");
        while(cachetime)
        {

        }
        String selection = values.get("key").toString();

        String message = "insert" + "_" + values.get("key").toString() + "_" + values.get("value").toString();
        Log.v(TAG, "message is" + message);

        int partitionport = DeterminePartition(selection);
        if (partitionport == myPort) {
            if(!restartwaitLoop)
            {
                //restart is happening
                Log.v(TAG, "putting message in a chache as restart is happening");
                cache.put(values.get("key").toString(),values.get("value").toString());
            }
            else
            {   //can insert in a db directly
                boolean success = dbHelper.insertkey(values);
                Log.v(TAG, "Inserting at local success" + success);
            }

            String[] port={Integer.toString(SuccPort1),Integer.toString(SuccPort2)};
            for(int i=0;i<2;i++) {
                String result = SendMessageToNodes(message, port[i], false, 0);
                Log.v(TAG, "Result received in insert:" + message+" "+result+" "+port[i]);
                String desiredresult = "ACK" + selection;
                if ((result != null) && (result.equals(desiredresult))) {
                    //do nothing
                } else {
                    Log.v(TAG, "Insert at node is not successful,hence caching!");
                    String buffer = message + ":" + desiredresult;
                    synchronized (CacheUndeliveredMessages) {
                        CacheUndeliveredMessages.put(buffer, port[i]);
                    }

                }
            }


        } else {
            //port = partitionport;
            //Log.v(TAG, "Sending Insert to partition port");

            Integer[] succ = SuccessorMap.get(partitionport);
            String[] port={Integer.toString(partitionport),Integer.toString(succ[0]),Integer.toString(succ[1])};
            for(int i=0;i<3;i++) {
                String result = SendMessageToNodes(message, port[i], false, 0);
                Log.v(TAG, "Result received in insert:" + message+" "+result+" "+port[i]);
                String desiredresult = "ACK" + selection;
                if ((result != null) && (result.equals(desiredresult))) {
                    //do nothing
                } else {
                    Log.v(TAG, "Insert at node is not successful,hence caching!");
                    String buffer = message + ":" + desiredresult;
                    synchronized (CacheUndeliveredMessages) {
                        CacheUndeliveredMessages.put(buffer, port[i]);
                    }
                    //tbd:If it is partitionport put it into cachekeyval

                }
            }
        }



        Log.v(TAG, "Returning insert()");
        return null;
    }

   /* private void SendMessageToNodes(String message,String port_str,boolean retry,int count)
    {
        Log.v(TAG, "Enter SendMessageToNodes() for message and port,retry and count:" + message + port_str+retry+count);
        String[] ports=port_str.split("_");
        if (retry) {
            Log.v(TAG, "This is the retry!");
            try {
                Thread.sleep(30);

            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        count++;
        for (int i = 0; i < ports.length; i++) {
            try {
                Log.v(TAG, "Sending message to successor port" + message + ":" + Integer.parseInt(ports[i]));
                Socket socket = new Socket();

                socket.connect(new InetSocketAddress("10.0.2.2", Integer.parseInt(ports[i])), 1500);


                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


                out.println(message);
                String result = in.readLine();
                Log.v(TAG, "result" + result);

                in.close();
                out.close();
                socket.close();

            } catch (SocketTimeoutException e) {
                Log.e(TAG, "SocketTimeoutException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (SocketException e) {
                Log.e(TAG, "EOFException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (EOFException e) {
                Log.e(TAG, "EOFException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (IOException e) {
                Log.e(TAG, "EOFException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }//end of for loop to send message to successors

        Log.v(TAG, "Exit SendMessageToSuccessors() for message and port:" + message + port_str);
    }*/
    private String SendMessageToNodes(String message,String port_str,boolean retry,int count)
    {
        //Log.v(TAG, "Enter SendMessageToNodes() for message and port,retry and count:" + message + port_str+retry+count);
        String[] ports=port_str.split("_");
        if (retry) {
            //Log.v(TAG, "This is the retry!");
            try {
                Thread.sleep(30);

            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        String result="initial";
        count++;
        for (int i = 0; i < ports.length; i++) {
            try {
                //Log.v(TAG, "Sending message to successor port" + message + ":" + Integer.parseInt(ports[i]));
                Socket socket = new Socket();

                socket.connect(new InetSocketAddress("10.0.2.2", Integer.parseInt(ports[i])), 1500);


                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


                out.println(message+"\n");
                out.flush();
                result = in.readLine();
                //Log.v(TAG, "result" + result);

                in.close();
                out.close();
                socket.close();

            } catch (SocketTimeoutException e) {
                Log.e(TAG, "SocketTimeoutException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    result= SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (SocketException e) {
                Log.e(TAG, "EOFException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    result=SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (EOFException e) {
                Log.e(TAG, "EOFException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    result=SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (IOException e) {
                Log.e(TAG, "EOFException in insert()" + Integer.parseInt(ports[i]));
                Log.e(TAG, e.toString());
                if(count<=4)
                {
                    result=SendMessageToNodes( message, ports[i],true, count);
                }
            } catch (Exception e) {
                //Log.e(TAG, e.toString());
            }


        }//end of for loop to send message to successors

        //Log.v(TAG, "Exit SendMessageToSuccessors() for message and port and result:" + message + port_str +result);
        return result;
    }


    public Uri insertReplicate(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        try {
            //Log.v(TAG, "Entered insertReplicate()");

            boolean success = dbHelper.insertkey(values);

            //Log.v(TAG, "Inserting success" + success);
        }catch (Exception e)
        {
            Log.e(TAG, e.toString());
        }
        Log.v(TAG, "Exit insertReplicate()");
        return mUri;

    }


    @Override
    public boolean onCreate() {
        Log.v(TAG, "Entered OnCreate()");
        if (db==null)
        {
            Log.v(TAG, "DB is a null object");
            dbHelper = new DBHelper(this.getContext());
            db = dbHelper.getWritableDatabase();
        }
        else
        {
            Log.v(TAG, "Non null database");
            //dbHelper = new DBHelper(this.getContext());
            db = dbHelper.getWritableDatabase();
            //Logic for recovery


        }

        //logic for recovery
        Log.v(TAG, "Setting restartwaitLoop to false");
        Log.v(TAG, "Setting querywaitloop to false");
        restartwaitLoop=false;
        querywaitLoop=false;
        cache = new LinkedHashMap();

        //start a server thread
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket" + e);
            //return;
        }


        //Getting port of avd
        TelephonyManager tel = (TelephonyManager) this.getContext().getSystemService(Context.TELEPHONY_SERVICE);
        portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = (Integer.parseInt(portStr) * 2);
        Log.v(TAG, "my port is:" + myPort);
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");

        //Get NodeId of this node
        try {
            NodeId=genHash(portStr);
        }catch (Exception e) {
            Log.e(TAG,e.toString());
        }
        Log.v(TAG, "my nodeid is:" + NodeId);

        //fill up ChordRingId structure
        try {
            for (int i = 0; i < 5; i++) {
                ChordRingID[i] = genHash(ChordRingPortStr[i]);
            }
        }catch (Exception e)
        {
            Log.e(TAG,e.toString());
        }

        //Determine successors and predecessors and their ids
        DetermineSuccAndPred();

        SharedPreferences sharedpreference=this.getContext().getSharedPreferences("Start", this.getContext().MODE_PRIVATE);
        String start = sharedpreference.getString("Start", null);
        Log.v(TAG, "start" + start);
        if(start!=null){
            Log.v(TAG, "Restart");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, null, "NodeRecovery");

        }
        else
        {
            Log.v(TAG, "Fresh start");
            SharedPreferences.Editor editor = sharedpreference.edit();
            editor.putString("Start", "Restart");
            editor.commit();
            Log.v(TAG, "Setting restartwaitloop to true");
            Log.v(TAG, "Setting querywaitloop to true");
            restartwaitLoop=true;
            querywaitLoop=true;

        }

        //logs for printing initialization values
        String logmsg= "SuccPort1:"+SuccPort1+" SuccPort2:"+SuccPort2+" PredPort1:"+PredPort1+" PredPort2:"+PredPort2;
        Log.v(TAG, logmsg);
        logmsg= "SuccNodeId1:"+SuccNodeId1+" SuccNodeId2:"+SuccNodeId2+" PredNodeId1:"+PredNodeId1+" PredPort2:"+PredNodeId2;
        Log.v(TAG, logmsg);
        logmsg= "myPort:"+myPort+" portStr:"+portStr;
        Log.v(TAG, logmsg);

        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, null, "CacheDelivery");
        creating=false;

        //Just take data from everyone -- take a hashmap, insert all key values it gets from pred1,pred2, and succ1 and succ2 in it.

        //waitLoop=false;

        //I put this thread sleep here because avd 4 was taking time to get up
        /*try {
            Thread.sleep(5000);
        }catch (Exception e)
        {
            Log.e(TAG,e.toString());
        }*/
        return false;
    }

    @Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection,
                                     String[] selectionArgs, String sortOrder) {
        // TODO Auto-generated method stub
        String[] columnNames = {
                "key", "value"
        };
        MatrixCursor cursor = new MatrixCursor(columnNames);
        Cursor res = null;
        String message = "query" + "_" + selection + "_" + myPort;
        Log.v(TAG, "Entering query() for selection:" + selection);
        if (selection.equals("@")) {
            //get values from all nodes and return latest value
            while(!querywaitLoop)
            {

            }
            res = dbHelper.getAllMessages();
        } else if (selection.equals("*")) {
            //get values from all nodes and return latest value
            Log.v(TAG, "Copying own data");
            res = dbHelper.getAllMessages();
            //copying own data to cursor
            if (res.moveToFirst()) {
                do {
                    String var1 = res.getString(res.getColumnIndex("key"));
                    String var2 = res.getString(res.getColumnIndex("value"));

                    String[] row = {
                            var1, var2
                    };
                    cursor.addRow(row);

                } while (res.moveToNext());
            }
            //res.close();
            Log.v(TAG, "Copying others data");
            //copying others data to cursor
            for (int i = 0; i < 5; i++) {
                if (ChordRing[i] != myPort) {
                    try {

                        /*Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                ChordRing[i]);*/
                        Socket socket = new Socket();

                        socket.connect(new InetSocketAddress("10.0.2.2", ChordRing[i]), 1500);

                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        out.println("globalquery"+"\n");
                        out.flush();
                        String output = in.readLine();
                        //call function which adds output to cursor
                        CursorFromStringGlobal(output, cursor);

                        out.close();
                        in.close();
                        socket.close();

                    } catch (Exception e) {
                        Log.e(TAG, e.toString());

                    }
                }

            }
            res = cursor;


        } //tbd:if the key is present in local cache return it from there
        else {
            //get values from all nodes and return latest value
            int sendport = DeterminePartition(selection);
            /*if (sendport == myPort) {
                res = dbHelper.getMessage(selection);
                Log.v(TAG, "Query successful");

            } else {*/
            //tbd:try merging this code with failure handling code, by writing do while may be

            String[] queryresponses=new String[3];
            Integer succ[]=SuccessorMap.get(sendport);
            int[] ports={sendport,succ[0],succ[1]};
            for (int i = 0; i < 3; i++) {
                try {
                    Log.v(TAG, "sending request to query" + message + ports[i]);
                    Socket socket = new Socket();

                    socket.connect(new InetSocketAddress("10.0.2.2", ports[i]), 1500);

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println(message+"\n");
                    out.flush();
                    String output = in.readLine();

                    Log.v(TAG, "output received:" + output);

                    queryresponses[i] = output;

                   /* if (output.equals("queryresponse")){
                        Log.v(TAG,"null output received:");
                        res=QueryfailureHandler(message,sendport);
                    }
                    else {
                        res = CursorFromString(output);
                    }*/
                    out.close();
                    in.close();
                    socket.close();
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "SocketTimeoutException in query()");
                    Log.e(TAG, e.toString());
                    // Node Failure
                    //res=QueryfailureHandler(message,sendport);
                } catch (SocketException e) {
                    Log.e("ClientThread", "EOFException in query()");
                    Log.e(TAG, e.toString());
                    // Node Failure
                    //res=QueryfailureHandler(message,sendport);
                } catch (EOFException e) {
                    Log.e("ClientThread", "EOFException in query()");
                    Log.e(TAG, e.toString());
                    // Node Failure
                    //res=QueryfailureHandler(message,sendport);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }

            for(int i=0;i<3;i++)
            {
                if((queryresponses[i]==null)||(queryresponses[i].equals("queryresponse")))
                {
                    Log.e(TAG, "This is response from a failed node");
                }
                else
                {
                    Log.e(TAG, "This is response from a correct node");
                    res = CursorFromString(queryresponses[i]);
                    break;
                }


            }

        }
        //to handle a special case:if queried and not present at self,just insert!
        Log.v(TAG, "Exiting query() ");
        return res;

    }
    /*@Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection,
                                     String[] selectionArgs, String sortOrder) {
        // TODO Auto-generated method stub
        String[] columnNames = {
                "key", "value"
        };
        //MatrixCursor cursor = new MatrixCursor(columnNames);
        globalcursor = new MatrixCursor(columnNames);
        Cursor res = null;
        String message = "query" + "_" + selection + "_" + myPort;
        Log.v(TAG, "Entering query() for selection:" + selection);
        if (selection.equals("@")) {
            res = dbHelper.getAllMessages();
        } else if (selection.equals("*")) {
            Log.v(TAG, "Copying own data");
            res = dbHelper.getAllMessages();
            //copying own data to global cursor from cursor res:
            if (res.moveToFirst()) {
                do {
                    String var1 = res.getString(res.getColumnIndex("key"));
                    String var2 = res.getString(res.getColumnIndex("value"));

                    String[] row = {
                            var1, var2
                    };
                    globalcursor.addRow(row);

                } while (res.moveToNext());
            }
            res.close();
            //copying others data to cursor
            Log.v(TAG, "Copying others data");
            Log.v(TAG, "starting new client task for global query-multicasting");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, null, "globalquery");
            while (!querywaitLoop) {

            }
            querywaitLoop = false;
            Log.v(TAG, "Returning cursor!");

            res = globalcursor;


        } else {
            int sendport = DeterminePartition(selection);
            if (sendport == myPort) {
                res = dbHelper.getMessage(selection);
                Log.v(TAG, "Query successful");

            } else {
                //tbd:try merging this code with failure handling code, by writing do while may be
                Log.v(TAG, "starting new client task for query");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, "query", Integer.toString(sendport));
                while (!querywaitLoop) {

                }
                querywaitLoop = false;
                Log.v(TAG, "Returning cursor!");

                res = globalcursor;

            }
            Log.v(TAG, "Exiting query() ");
            return res;

        }
        return res;
    }*/

    private Cursor QueryfailureHandler(String message,int sendport)
    {
        //in query failure handler if node fails ask its succ
        Log.v(TAG,"Entering QueryfailureHandler()");
        Cursor res= null;
        Integer succ[]=SuccessorMap.get(sendport);
        Log.v(TAG,"sending request to query"+message+sendport);
        try {
            Socket socket = new Socket();

            socket.connect(new InetSocketAddress("10.0.2.2", succ[1]), 1500);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(message+"\n");
            out.flush();
            String output = in.readLine();
            Log.v(TAG,"output received:"+output);
            res = CursorFromString(output);
            out.close();
            in.close();
            socket.close();
        }catch (Exception e) {
            Log.e(TAG, "Exception in query()");
            Log.e(TAG, e.toString());

        }
        Log.v(TAG,"Exit QueryfailureHandler()");
        return res;


    }



    private void GlobalQueryHandler(MatrixCursor cursor)
    {
        //get from own
        Log.v(TAG,"Entered GlobalQueryHandler() ");
        Cursor res=dbHelper.getAllMessages();
        //copying to cursor
        if (res.moveToFirst()) {
            do {
                String var1 = res.getString(res.getColumnIndex("key"));
                String var2 = res.getString(res.getColumnIndex("value"));

                String[] row = {
                        var1, var2
                };
                cursor.addRow(row);

            } while (res.moveToNext());
        }
        res.close();
        Log.v(TAG,"starting new client task for global query-multicasting");
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, null, "globalquery");
        Log.v(TAG, "Exit GlobalQueryHandler() ");


    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    public int DeterminePartition(String selection)
    {
        try{
            String key = genHash(selection);
            if ( (key.compareTo(ChordRingID[0])<= 0)|| (key.compareTo(ChordRingID[4]) > 0) )
            {
                //Log.v(TAG,"Partition for selection is"+selection+ChordRing[0]);
                return ChordRing[0];

            }
            for(int i=1;i<5;i++)
            {
                //Log.v(TAG,"Determining partition for"+i);
                if ( (key.compareTo(ChordRingID[i])<= 0) && (key.compareTo(ChordRingID[i-1]) > 0) )
                {
                    //Log.v(TAG,"Partition port for selection is"+selection +ChordRing[i]);
                    return ChordRing[i];
                }

            }


        }catch (Exception e)
        {
            Log.e(TAG,e.toString());
        }
        Log.v(TAG, "Error occured");
        return 0;

    }


    public String queryReply(String input) {
        // TODO Auto-generated method stub

        //String message="query"+"_"+selection+"_"+myPort;
        try {
            Log.v(TAG, "Entering queryReply() for input:" + input);
            String[] inputvalues = input.split("_");
            String selection = inputvalues[1];
            String remoteport = inputvalues[2];

            //Convert cursor to string and send it to the port
            Cursor res = dbHelper.getMessage(selection);
            /*if(res==null)
            {
                Log.v(TAG, "res is null! omg! I am f*cked!");
            }
            else
            {
                Log.v(TAG, "res is not null! i am f*ckin genius!");
            }*/
            String cursorstring = CursorTostring(res);

            //Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(remoteport));
            //PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            //out.println(cursorstring);
            Log.v(TAG, "Exiting queryReply() for input and output"+input+":"+cursorstring);
            return cursorstring;

        }catch (Exception e)
        {
            Log.e(TAG, e.toString());
        }
        return null;


    }


    //write server task
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            while (true) {
                try {
                    //only for ping request type
                    Log.v(TAG, "Entered servertask");


                    Socket socket = serverSocket.accept();

                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out=new PrintWriter(socket.getOutputStream(),true);
                    String input=in.readLine();
                    Log.v(TAG, "ServerTask received message:"+input);
                    //tbd null pointer exception
                    while(creating)
                    {

                    }
                    String[] inputvalues={"none"};
                    if(input!=null) {
                        inputvalues = input.split("_");
                        Log.v(TAG, "SeverTask Request received" + inputvalues[0]);
                    }

                    if (inputvalues[0].equals("insert")){
                        while(cachetime)
                        {

                        }
                        if(!restartwaitLoop)
                        {
                            Log.v(TAG, "Node recovering from a restart!");
                            cache.put(inputvalues[1],inputvalues[2]);
                        }

                        Log.v(TAG, "ServerTask Insert:" + inputvalues[1] + inputvalues[2]);
                        ContentValues contentvalues = new ContentValues();
                        contentvalues.put(KEY_FIELD, inputvalues[1] );
                        contentvalues.put(VALUE_FIELD, inputvalues[2]);

                        insertReplicate(mUri, contentvalues);

                        out.println("ACK" + inputvalues[1]+"\n");
                        out.flush();
                        //if recovery is going on put it in cache and insert these messages at the end of recovery logic
                        //cache. initialize cache at start of recovery logic


                    }
                    else if(inputvalues[0].equals("delete")) {
                        Log.v(TAG, "ServerTask Delete:" + inputvalues[1]);
                        while(cachetime)
                        {

                        }
                        if(!restartwaitLoop)
                        {
                            cache.put(inputvalues[1],"delete");
                        }
                        deleteReplicate(mUri, inputvalues[1], null);
                        out.println("ACK" + inputvalues[1]+"\n");
                        out.flush();
                        //if recovery is going on put it in a different cache and delete those messages present in that cache
                        //initialize cache at start of recovery logic

                    }
                    else if(inputvalues[0].equals("query")){
                        Log.v(TAG, "ServerTask Query:" + inputvalues[1]);
                        if(!querywaitLoop)
                        {
                            out.println("queryresponse"+"\n");
                            out.flush();
                        }
                        else {
                            String cursorstring = queryReply(input);
                            out.println(cursorstring+"\n");
                            out.flush();
                        }
                        //Log.v(TAG, "ServerTask Delete:" + inputvalues[1]);

                    }
                    else if(inputvalues[0].equals("globalquery"))
                    {
                        Log.v(TAG, "Globalquery");
                        //get input as string
                        Cursor res=dbHelper.getAllMessages();
                        String output=CursorTostring(res);
                        out.println(output+"\n");
                        out.flush();


                    }
                    else if(inputvalues[0].equals("queryresponse")) {
                        Log.v(TAG, "ServerTask QueryResponse:"+input);
                        //Construct a Cursor from input and return in global object
                        CursorFromString(input);
                        //cursor=cr;
                        //set waitLoop to true

                    }
                    else if(inputvalues[0].equals("recoverydata")){

                        Log.v(TAG, "ServerTask recoverydata:"+input);
                        String output=RecoveryDataReqHandler(inputvalues[1]);
                        out.println(output+"\n");
                        out.flush();



                    }
                    else if(inputvalues[0].equals("recoveryreplica")){

                        Log.v(TAG, "ServerTask recoveryreplica:"+input);
                        String output=RecoveryDataReqHandler(inputvalues[1]);
                        out.println(output+"\n");
                        out.flush();

                    }
                    else{

                        Log.v(TAG, "didn't match!"+input);
                    }

                   in.close();
                    out.close();
                } catch (IOException e) {
                    Log.e(TAG, "ServerTask IOException");

                }

            }

        }

        protected void onProgressUpdate(String...strings) {


        }


    }



    //write client task
    private class ClientTask extends AsyncTask<String,Void,Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            if (msgs[1].equals("NodeRecovery")) {
                RecoveryLogic();
                return null;

            } else if (msgs[1].equals("CacheDelivery")) {
                SendUndeliveredMessages();
                return null;

            }else {
                Log.v(TAG, "Invalid message received!!");
            }/*else if (msgs[1].equals("globalquery")) {
                for (int i = 0; i < 5; i++) {
                    if (ChordRing[i] != myPort) {
                        try {

                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    ChordRing[i]);
                            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            out.println("globalquery");
                            String output = in.readLine();
                            //call function which adds output to cursor
                            CursorFromStringGlobal(output, globalcursor);
                            socket.close();

                        } catch (Exception e) {
                            Log.e(TAG, e.toString());

                        }
                    }

                }
                //querywaitLoop=true;

            } else if (msgs[1].equals("query")) {
                Log.v(TAG, "sending request to query" + msgs[0] + msgs[2]);
                try {
                    Socket socket = new Socket();

                    socket.connect(new InetSocketAddress("10.0.2.2", Integer.parseInt(msgs[2])), 1500);

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    out.println(msgs[0]);
                    String output = in.readLine();
                    Log.v(TAG, "output received:" + output);
                    CursorFromString(output);
                    out.close();
                    in.close();
                    socket.close();
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "SocketTimeoutException in query()");
                    Log.e(TAG, e.toString());
                    // Node Failure
                    QueryfailureHandler(msgs[0], Integer.parseInt(msgs[2]));
                } catch (SocketException e) {
                    Log.e("ClientThread", "EOFException in query()");
                    Log.e(TAG, e.toString());
                    // Node Failure
                    QueryfailureHandler(msgs[0], Integer.parseInt(msgs[2]));
                } catch (EOFException e) {
                    Log.e("ClientThread", "EOFException in query()");
                    Log.e(TAG, e.toString());
                    // Node Failure
                    QueryfailureHandler(msgs[0], Integer.parseInt(msgs[2]));
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                //querywaitLoop=true;
            }
            else{
                try{
                    Log.v(TAG, "Entered client task for Insert,Delete request");
                    String[] ports = msgs[2].split("_");
                    for (int i = 0; i < ports.length; i++) {
                        Log.v(TAG, "Sending to port" + ports[i]);
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(ports[i]));

                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        Log.v(TAG, "Clienttask:Sending message to port" + msgs[0] + ":" + ports[i]);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        Log.v(TAG, msgs[0]);
                        out.println(msgs[0]);
                        String result = in.readLine();
                        Log.v(TAG, "result" + result);
                        out.close();
                        in.close();
                        socket.close();
                    }


                } catch (NullPointerException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }*/


            return null;

        }


    }

    public void DetermineSuccAndPred() {

        if (myPort==11108) {
            SuccPort1 = 11116;
            SuccPort2 = 11120;
            PredPort1 = 11112;
            PredPort2 = 11124;
        } else if (myPort==11112) {
            SuccPort1 = 11108;
            SuccPort2 = 11116;
            PredPort1 = 11124;
            PredPort2 = 11120;
        } else if (myPort==11116) {
            SuccPort1 = 11120;
            SuccPort2 = 11124;
            PredPort1 = 11108;
            PredPort2 = 11112;
        } else if (myPort==11120) {
            SuccPort1 = 11124;
            SuccPort2 = 11112;
            PredPort1 = 11116;
            PredPort2 = 11108;
        } else if (myPort==11124) {
            SuccPort1 = 11112;
            SuccPort2 = 11108;
            PredPort1 = 11120;
            PredPort2 = 11116;
        }
        //fill successor map
        Integer[] succ=new Integer[]{11116,11120};
        SuccessorMap.put(11108,succ);
        succ=new Integer[]{11108,11116};
        SuccessorMap.put(11112, succ);
        succ=new Integer[]{11120,11124};
        SuccessorMap.put(11116,succ);
        succ=new Integer[]{11124,11112};
        SuccessorMap.put(11120,succ);
        succ=new Integer[]{11112,11108};
        SuccessorMap.put(11124, succ);

        try {
            SuccNodeId1 = genHash(genHash(Integer.toString(SuccPort1 / 2)));
            SuccNodeId2 = genHash(genHash(Integer.toString(SuccPort2 / 2)));
            PredNodeId1 = genHash(genHash(Integer.toString(PredPort1 / 2)));
            PredNodeId2 = genHash(genHash(Integer.toString(PredPort2 / 2)));
        }catch (Exception e)
        {
            Log.e(TAG,e.toString());
        }

    }

    private Cursor CursorFromString(String input){
        Log.v(TAG, "Enter CursorFromString() for input" + input);
        String[] columnNames = {
                "key", "value"
        };
        MatrixCursor cursor=new MatrixCursor(columnNames);
        //globalcursor=new MatrixCursor(columnNames);
        String[] inputvalues=input.split("_");
        String selection=inputvalues[1];
        String value=inputvalues[2];
        String[] row = {
                selection, value
        };
        cursor.addRow(row);
        Log.v(TAG, "Exit CursorFromString() " );
        return cursor;

    }

    private void CursorFromStringGlobal(String input,MatrixCursor cursor){
        String[] inputvalues=input.split("_");
        int i=1;
        while(i<inputvalues.length) {

            String[] row = {
                    inputvalues[i], inputvalues[i+1]
            };
            cursor.addRow(row);
            i=i+2;
        }

    }

    private String CursorTostring(Cursor res) {
        Log.v(TAG, "Entered CursorTostring ");
        String output="queryresponse";

        if (res.moveToFirst()) {
            do {

                String var1 = res.getString(res.getColumnIndex("key"));
                String var2 = res.getString(res.getColumnIndex("value"));
                //Log.v(TAG, "var1and var2:" + var1+ var2);
                output = output + "_" + var1 + "_" + var2;

            } while (res.moveToNext());
        }
        res.close();
        Log.v(TAG, "CursorToString output is:" + output);
        return output;

    }

    private void RecoveryLogic()
    {
        //send them own port and request own and replicas data and store data from the nodes in hashmap. if we get any exception here,exit the function
        //and then insert it all in db
        Log.v(TAG, "Entered RecoveryLogic ");
        HashMap<String,String> recoverydatamap=new HashMap<String, String>();
        //reinitialize cache
        //cache = new LinkedHashMap();
        String reqforowndata="recoverydata"+"_"+myPort;
        int succ[]={SuccPort1,SuccPort2};
        for(int i=0;i<2;i++)
        {
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), succ[i]);

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Log.v(TAG, "RecoveryLogic:Sending message to port" + reqforowndata + ":" + succ[i]);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Log.v(TAG, reqforowndata);
                out.println(reqforowndata+"\n");
                out.flush();
                String data=in.readLine();
                ProcessData(data,recoverydatamap);
                in.close();
                out.close();
                socket.close();
            }catch (Exception e)
            {
                Log.e(TAG,e.toString());

            }
        }

        int pred[]={PredPort1,PredPort2};
        for(int i=0;i<2;i++)
        {
            try {
                String reqforreplicadata="recoveryreplica"+"_"+pred[i];
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), pred[i]);

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Log.v(TAG, "RecoveryLogic:Sending message to port" + reqforreplicadata + ":" + pred[i]);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Log.v(TAG, reqforreplicadata);
                out.println(reqforreplicadata+"\n");
                out.flush();
                String data=in.readLine();
                ProcessData(data,recoverydatamap);
                in.close();
                out.close();
                socket.close();
            }catch (Exception e)
            {
                Log.e(TAG,e.toString());

            }
        }

        RecoverData(recoverydatamap);
        restartwaitLoop=true;
        Log.v(TAG, "Set restartwaitLoop true");

        Log.v(TAG, "Updating entries from cache");
        cachetime=true;
        Set set = cache.entrySet();
        // Get an iterator
        Iterator i = set.iterator();
        // Display elements
        while(i.hasNext()) {
            Map.Entry me = (Map.Entry)i.next();
            System.out.print("key in cache:" + me.getKey());
            String key= (String)me.getKey();
            System.out.println("value in cache" + key);
            String value= (String)me.getValue();
            if(value.equals("delete"))
            {
                Log.v(TAG, "deleting key" + me.getKey());
                dbHelper.deleteMessage(key);
            }
            else
            {
                Log.v(TAG, "Inserting key" + me.getKey());
                ContentValues contentvalues = new ContentValues();
                contentvalues.put(KEY_FIELD, key);
                contentvalues.put(VALUE_FIELD, value);
                boolean success=dbHelper.insertkey(contentvalues);
            }

        }
        cachetime=false;
        Log.v(TAG, "setting querywaitloop to true");
        querywaitLoop=true;
        Log.v(TAG, "Exit RecoveryLogic ");



    }

    private void ProcessData(String data,HashMap<String,String> map)
    {
        //convert the string that was recovered in key val pairs and put it in hashmap
        Log.v(TAG, "Entered ProcessData(): "+data);
        String[] keyvalpairs={};
        if (data!=null)
        {
            keyvalpairs=data.split("_");
        }
        for(int i=1;i<keyvalpairs.length;i++)
        {
            //Log.v(TAG, "Entry is"+keyvalpairs[i]);
            String[] entry=keyvalpairs[i].split(":");
            map.put(entry[0],entry[1]);

        }
        Log.v(TAG, "Exit ProcessData() ");


    }
    private void RecoverData(HashMap<String,String> map)
    {
        //if hashmap is empty just clear the db and insert all entries in db
        Log.v(TAG, "Entered RecoverData() ");
        if (map.size()!=0)
        {
            Log.v(TAG, "Erasing database and inserting values from successor and predecessor");
            //erase own database
            int succ=dbHelper.deleteAllMessage();
            //inserting hashmap values in db

            Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator() ;
            while(iterator.hasNext()){
                Map.Entry<String, String> entry = iterator.next();
                //construct contentvalues
                ContentValues contentvalues = new ContentValues();
                contentvalues.put(KEY_FIELD, entry.getKey() );
                Log.v(TAG, "Inserting key" + entry.getKey());
                contentvalues.put(VALUE_FIELD, entry.getValue());
                boolean success=dbHelper.insertkey(contentvalues);

            }


        }
        Log.v(TAG, "Exit RecoverData() ");


    }

    private String RecoveryDataReqHandler(String input)
    {
        Log.v(TAG, "Entered RecoveryDataReqHandler() for"+input);
        //Determine if it belongs to the partition we want it to be
        int nodeport=Integer.parseInt(input);
        String output="OUTPUT";
        Cursor res= null;

        res=dbHelper.getAllMessages();

        //copying own data to cursor
        if (res.moveToFirst()) {
            do {
                String key = res.getString(res.getColumnIndex("key"));

                int port= DeterminePartition(key);
                if (port==nodeport)
                {
                    String val = res.getString(res.getColumnIndex("value"));
                    output=output+"_"+key+":"+val;
                    //Log.v(TAG, "key and val for recoverydatahandler" + key + val);

                }

            } while (res.moveToNext());
        }
        Log.v(TAG, "Exit RecoveryDataReqHandler() for "+input);
        return output;


    }


    public void SendUndeliveredMessages()
    {
        while(true)
        {
           /* Set set = CacheUndeliveredMessages.entrySet();
            // Get an iterator
            Iterator i = set.iterator();
            // Display elements*/
            try{
                Thread.sleep(20);
            }catch (Exception e)
            {
                Log.e(TAG, e.toString());

            }
            String check="none";
            synchronized (CacheUndeliveredMessages) {
                if (CacheUndeliveredMessages.size() > 0) {
                    Iterator<Map.Entry<String, String>> iter = CacheUndeliveredMessages.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<String, String> entry = iter.next();
                        String buffer = entry.getKey();
                        //System.out.println("value in cache" + buffer);
                        String port = entry.getValue();
                        //System.out.println("delete value in cache" + buffer);
                        String[] inputvalues = buffer.split(":");
                        String message = inputvalues[0];
                        //String[] keyval = message.split("_");
                        //String key = keyval[1];
                        //System.out.println("key value in cache" + key);
                        String desiredresults = inputvalues[1];
                        //System.out.println("desired result in cache" + desiredresults);
                        String result = SendMessageToNodes(message, port, false, 0);
                        //System.out.println("result" + result);
                        if ((result != null) && (result.equals(desiredresults))) {
                            //delete from cache

                            System.out.println(" deleting in cache" + buffer);
                            iter.remove();
                            //check=key;
                            //break;

                        } else {
                            //do nothing
                            break;
                        }
                    }
                }
            }
            //tbd:remove from cackekeyval
            /*if(check.equals("none"))
            {

            }else{

                synchronized (Cachekeyval)
                {
                    Cachekeyval.remove(check);
                    System.out.println(" deleting in cachekeyval" );
                }
            }*/

        }


    }



}

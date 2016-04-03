package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] REMOTE_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    static ServerSocket serverSocket;
    static GroupMessengerProvider gpProvider;
    static Cursor dbCursor = null;
    static Uri providerUri = null;
    static String[] cols = {};
    static int keyCnt = 0;
    static ServerTask server;
    static HashMap<String, MessageObj> priorityMap = new HashMap<String, MessageObj>();
    static HashMap<String, ArrayList<Float>> receivedProposals = new HashMap<String, ArrayList<Float>>();
    private static Integer msgCounter = 0;
    private static final Object countLock = new Object();
    static String thisDevicePort;
    static Float agreedLargestSeqNo = Float.valueOf(0);
    static Float proposedLargestSeqNo = Float.valueOf(0);
    static HashMap<String, Integer> avdStatus = new HashMap<String, Integer>();
    static HashMap<String, Handler> rHandler = new HashMap<String, Handler>();
    static HashMap<String, Boolean> executeFail = new HashMap<String, Boolean>();
    static int aliveAvds = 5;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;
    private static HashMap<String, String> removePorts = new HashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        server = new ServerTask();
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        thisDevicePort = myPort;
        gpProvider = new GroupMessengerProvider();
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(getApplicationContext().getString(R.string.content_uri));
        uriBuilder.scheme("content");
        providerUri = uriBuilder.build();
        cols = new String[]{"key", "value"};

        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            Log.e(TAG, e.getMessage());
            return;
        }

        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        final EditText editText = (EditText) findViewById(R.id.editText1);
        Button sendBtn = (Button) findViewById(R.id.button4);

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf(editText.getText()), myPort, "", "", "msg");
                tv.append(editText.getText() + "\n");
                editText.setText("");
            }
        });

        for (String port : REMOTE_PORTS) {
            avdStatus.put(port, 0);
            rHandler.put(port, new Handler());
        }
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "GroupMessenger Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://edu.buffalo.cse.cse486586.groupmessenger2/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

//        Log.d("Stopped", "Here stopped");
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "GroupMessenger Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://edu.buffalo.cse.cse486586.groupmessenger2/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {

            ServerSocket serverSocket = sockets[0];
            String message = "";
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {

                Socket clientSocket = serverSocket.accept();
                BufferedReader data = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                message = data.readLine() + "";
                String[] messageType = message.split("__##__");
//                Log.d("Data1", String.valueOf(messageType[0].contains("msg")));
//                Log.d("Data2", messageType[1]);

                if (messageType[0].contains("msg")) {
                    synchronized (priorityMap) {

                        String[] messageArr = messageType[1].split("__@@__");
                        String msgFrom = messageArr[0];
                        String msgNo = messageArr[1];
                        String msg = messageArr[2];
                        Float tempLargestSeqNo = Float.valueOf(0);
                        Log.d("ReceivedMsg",msgFrom+"=>"+msg);
                        synchronized (tempLargestSeqNo) {
                            synchronized (msgCounter) {
                                incrementCount();
                                tempLargestSeqNo = (Math.max(Float.valueOf((msgCounter) + "." + thisDevicePort), agreedLargestSeqNo)) + Float.valueOf(1);
                                MessageObj msgObj = new MessageObj(Float.valueOf((tempLargestSeqNo.intValue()) + "." + thisDevicePort), msgFrom, msgNo, msg, false, false, 1);
                                proposePriority(msgFrom, msgNo, msg, String.valueOf(Float.valueOf((tempLargestSeqNo.intValue()) + "." + thisDevicePort)));
                                priorityMap.put(msg.trim(), msgObj);
//                                Log.d("Proposed", msg.trim() + "=>" + String.valueOf(Float.valueOf((tempLargestSeqNo.intValue()) + "." + thisDevicePort)));
                            }
                        }
                        priorityMap = sortByValue(priorityMap);
                    }

                } else if (messageType[0].contains("proposal")) {

                    synchronized (priorityMap) {

                        String[] proposal = messageType[1].split("_");
                        String msgFrom = proposal[0];
                        String msgNo = proposal[1];
                        String msg = proposal[2];
                        String proposedSeqNo = proposal[3];
                        ArrayList<Float> allProposedSeqNo = new ArrayList<Float>();

                        synchronized (receivedProposals) {

                            rHandler.get(msgFrom).removeCallbacksAndMessages(removePorts.get(msgFrom.trim()));

                            if (receivedProposals.containsKey(String.valueOf(msg).trim())) {
                                allProposedSeqNo = receivedProposals.get(String.valueOf(msg).trim()); //proposal[0] is the message number
                            }

                            allProposedSeqNo.add(Float.valueOf(proposedSeqNo));
                            receivedProposals.put(String.valueOf(msg).trim(), allProposedSeqNo);
                            Log.d("ReceivedProposal", msgFrom + "=>" + msg + "=>" + proposedSeqNo);
                        }

                    }
                } else if (messageType[0].contains("agree")) {

                    synchronized (priorityMap) {
                        String[] agree = messageType[1].split("_");
                        String agreeMsg = agree[2];
                        String agreeSeqNo = agree[3];
                        Float tempAgreedLargestSeqNo = Math.max(agreedLargestSeqNo, Float.valueOf(agreeSeqNo));
                        agreedLargestSeqNo = Float.valueOf(tempAgreedLargestSeqNo.intValue() + "." + thisDevicePort);
                        MessageObj deliverMsgObj = priorityMap.get(agreeMsg.trim());
                        deliverMsgObj.status = true;
                        deliverMsgObj.isFailed = false;
                        deliverMsgObj.SeqNo = Float.valueOf(agreeSeqNo);
                        priorityMap.put(agreeMsg.trim(), deliverMsgObj);
                        priorityMap = sortByValue(priorityMap);
                        Log.d("ReceivedAgreement", agree[0] + "=>" + agreeMsg.trim() + "=>" + agreeSeqNo);
                    }

                } else if (messageType[0].contains("failed")) {
                    synchronized (avdStatus) {
                        String failedPort = messageType[1];
                        if (avdStatus.get(failedPort) == 0) {
                            avdStatus.put(failedPort, 1);
                            aliveAvds = 4;
                        }
                    }

                }

                synchronized (priorityMap) {

                    Iterator it = priorityMap.entrySet().iterator();
                    synchronized (it) {
                        while (it.hasNext()) {

                            Map.Entry<String, MessageObj> item = (Map.Entry<String, MessageObj>) it.next();
                            MessageObj msgObj = item.getValue();

                            Log.d("WHile", msgObj.msg + " / " + msgObj.msgFrom + " / " + thisDevicePort + " => " + msgObj.status + " | " + msgObj.isFailed);
                            if (receivedProposals.containsKey(String.valueOf(msgObj.msg).trim()))
                                Log.d("WHile2", msgObj.msg + " / " + msgObj.msgFrom + " / " + thisDevicePort + " => " + avdStatus.get(msgObj.msgFrom) + " | " + receivedProposals.get(String.valueOf(msgObj.msg).trim()).size() + " | " + aliveAvds);


                            if (msgObj.status) {
                                synchronized (it) {
                                    deliverMsg(keyCnt++, msgObj.msg);
                                    Log.d("Delivered", msgObj.msgFrom + "=>" + msgObj.msg);
                                    it.remove();
                                }
                            } else if (avdStatus.get(msgObj.msgFrom) == 1) {
                                Log.d("RemovedNode", msgObj.msgFrom + "=>" + msgObj.msg);
                                it.remove();

                            } else if (!msgObj.status && receivedProposals.containsKey(String.valueOf(msgObj.msg).trim()) && msgObj.msgFrom.equals(thisDevicePort) && receivedProposals.get(String.valueOf(msgObj.msg).trim()).size() >= aliveAvds) {

                                ArrayList<Float> proposedFinalSorted = receivedProposals.get(String.valueOf(msgObj.msg).trim());
                                Collections.sort(proposedFinalSorted); // Sort the arraylist
                                Float maxAgreedSeqNo = (proposedFinalSorted.get(proposedFinalSorted.size() - 1));

                                MessageObj deliverMsgObj = priorityMap.get(msgObj.msg.trim());
                                deliverMsgObj.status = true;
                                deliverMsgObj.SeqNo = Float.valueOf(maxAgreedSeqNo);
                                priorityMap.put(msgObj.msg.trim(), deliverMsgObj);
                                priorityMap = sortByValue(priorityMap);
                                Float tempagreedLargestSeqNo = Math.max(maxAgreedSeqNo, agreedLargestSeqNo);
                                agreedLargestSeqNo = Float.valueOf(tempagreedLargestSeqNo.intValue() + "." + thisDevicePort);
                                agreePriority(thisDevicePort, msgObj.msgNo, msgObj.msg, String.valueOf(maxAgreedSeqNo));
                                it = priorityMap.entrySet().iterator();
                                Log.d("WhileAgreeTrue", msgObj.msgFrom + "=>" + msgObj.msg);
                            } else {
                                break;
                            }
                        }
                    }
                }
                message = "";
                dbCursor = getContentResolver().query(providerUri, cols, "", null, "");

                if (dbCursor != null) {
                    while (dbCursor.moveToNext()) {
                        message += dbCursor.getString(dbCursor.getColumnIndex("key")) + "=>" + dbCursor.getString(dbCursor.getColumnIndex("value")) + " " + thisDevicePort + "\n";
                    }
                }

                publishProgress(message);

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.setText("");
            remoteTextView.append(strReceived);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        }
    }

    private void proposePriority(String msgFrom, String msgNo, String msg, String PriorityStr) {

//        new PriorityTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgFrom, msgNo, msg, PriorityStr);
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgFrom, msgNo, msg, PriorityStr, "propose");
    }

    private void agreePriority(String msgFrom, String msgNo, String msg, String PriorityStr) {

//        new AgreePriorityTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgFrom, msgNo, msg, PriorityStr);
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgFrom, msgNo, msg, PriorityStr, "agree");
    }

    private void notifyFail(String failedPort) {

        new FailedAvdTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, failedPort);

    }

    private class FailedAvdTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {

            try {

                for (int i = 0; i < 5; i++) {

                    if (!params[0].equals(REMOTE_PORTS[i])) {

                        if (avdStatus.get(REMOTE_PORTS[i]) == 0) {

                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORTS[i]));

                            String failedNo = "failed__##__" + params[0];
                            OutputStream sendStream = socket.getOutputStream();
                            sendStream.write(failedNo.getBytes());
                            sendStream.flush();
                            socket.close();
                        }

                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {


        @Override
        protected Void doInBackground(String... msgs) {

//            msgCounter++;
            if (msgs[4] == "msg") {
                // Add Own Proposal for Message
                ArrayList<Float> allProposedSeqNo = new ArrayList<Float>();
                synchronized (allProposedSeqNo) {
                    Float proposedSeqNo = Float.valueOf(0);
                    synchronized (msgCounter) {
                        incrementCount();
                        proposedSeqNo = Float.valueOf((msgCounter) + "." + msgs[1]);
                        allProposedSeqNo.add(proposedSeqNo);
                        synchronized (receivedProposals) {
                            receivedProposals.put(String.valueOf(msgs[0]).trim(), allProposedSeqNo);
                        }
                        synchronized (priorityMap) {
                            MessageObj msgObj = new MessageObj(proposedSeqNo, msgs[1], msgCounter + "", msgs[0], false, false, 1);
                            priorityMap.put(msgs[0].trim(), msgObj);
                        }
                    }
                }
            }

            try {
                if (msgs[4] == "msg") {
                    synchronized (avdStatus) {
                        for (int i = 0; i < 5; i++) {

                            if (!msgs[1].equals(REMOTE_PORTS[i])) {

                                if (avdStatus.get(REMOTE_PORTS[i]) == 0) {

                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(REMOTE_PORTS[i]));

                                    String msgToSend = "msg__##__" + msgs[1] + "__@@__" + msgCounter + "__@@__" + msgs[0] + "\n";

                                    Log.d("SendMessage", msgToSend+" to "+REMOTE_PORTS[i]);

                    /*
                     * TODO: Fill in your client code that sends out a message.
                     */
                                    String rmPort = new String(REMOTE_PORTS[i]);
                                    removePorts.put(REMOTE_PORTS[i], rmPort);
                                    rHandler.get(REMOTE_PORTS[i]).postAtTime(ghostMsg(REMOTE_PORTS[i]), rmPort, SystemClock.uptimeMillis() + 3000);
                                    try {
                                        ObjectOutputStream sendStream = new ObjectOutputStream(socket.getOutputStream());
                                        sendStream.write(msgToSend.getBytes());
                                        sendStream.flush();
                                        socket.close();
                                    } catch (IOException e) {
                                        Log.e("FailedAvd", "ClientTask socket IOException " + REMOTE_PORTS[i]);
                                        notifyFail(REMOTE_PORTS[i]);
                                    }
                                }
                            }
                        }
                    }
                } else if (msgs[4] == "propose") {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(msgs[0]));
                    String proposePNo = "proposal__##__" + thisDevicePort + "_" + msgs[1] + "_" + msgs[2] + "_" + msgs[3];
                    Log.d("SendProposal", proposePNo+" to "+msgs[0]);
                    try {
                        ObjectOutputStream sendStream = new ObjectOutputStream(socket.getOutputStream());
                        sendStream.write(proposePNo.getBytes());
                        sendStream.flush();
                        socket.close();
                    } catch (IOException e) {
                        Log.e("FailedAvd", "ClientTask socket IOException " + msgs[0]);
                        notifyFail(msgs[0]);
                    }
                } else if (msgs[4] == "agree") {

                    synchronized (avdStatus) {
                        for (int i = 0; i < 5; i++) {

                            if (!msgs[0].equals(REMOTE_PORTS[i])) {

                                if (avdStatus.get(REMOTE_PORTS[i]) == 0) {
                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(REMOTE_PORTS[i]));

                                    String proposePNo = "agree__##__" + msgs[0] + "_" + msgs[1] + "_" + msgs[2] + "_" + msgs[3];
                                    Log.d("SendAgreement", proposePNo+" to "+REMOTE_PORTS[i]);
//                                    Log.d("AgreeSentTask", proposePNo);
                                    try {
                                        ObjectOutputStream sendStream = new ObjectOutputStream(socket.getOutputStream());
                                        sendStream.write(proposePNo.getBytes());
                                        sendStream.flush();
                                        socket.close();
                                    } catch (IOException e) {
                                        Log.e("FailedAvd", "ClientTask socket IOException " + REMOTE_PORTS[i]);
                                        notifyFail(REMOTE_PORTS[i]);
                                    }
                                }

                            }
                        }
                    }

                } else if (msgs[4] == "ghost") {
                    for (int i = 0; i < 5; i++) {

                        if (!msgs[1].equals(REMOTE_PORTS[i])) {

                            if (avdStatus.get(REMOTE_PORTS[i]) == 0) {

                                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(REMOTE_PORTS[i]));

                                String msgToSend = "Ghost Msg \n";
                                try {
                                    ObjectOutputStream sendStream = new ObjectOutputStream(socket.getOutputStream());
                                    sendStream.write(msgToSend.getBytes());
                                    sendStream.flush();
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e("FailedAvd", "ClientTask socket IOException " + REMOTE_PORTS[i]);
                                    notifyFail(REMOTE_PORTS[i]);
                                }
                            }
                        }
                    }
                }

            } catch (Exception e) {
                Log.v("SocketTimeout", e.getMessage());
            }


            return null;
        }
    }

    private Runnable ghostMsg(final String remotePort) {

        return new Runnable() {
            @Override
            public void run() {
//                Log.d("GhostMsg", remotePort);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "", "", "", "", "ghost");
            }
        };
    }

    // Call insert of provider through ContentResolver
    public void deliverMsg(int key, String msgStr) {

        ContentValues contentValues = new ContentValues();
        contentValues.put(getApplicationContext().getString(R.string.key), String.valueOf(key));
        contentValues.put(getApplicationContext().getString(R.string.value), String.valueOf(msgStr));

        getContentResolver().insert(
                providerUri,    // assume we already created a Uri object with our provider URI
                contentValues
        );
    }

    public static <K, V extends Comparable<? super V>> HashMap<String, MessageObj>
    sortByValue(HashMap<String, MessageObj> map) {
        List<Map.Entry<String, MessageObj>> list
                = new LinkedList<Map.Entry<String, MessageObj>>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, MessageObj>>() {
            public int compare(Map.Entry<String, MessageObj> o1, Map.Entry<String, MessageObj> o2) {
                return (o1.getValue().SeqNo > o2.getValue().SeqNo) ? 1 : -1;
            }
        });
        LinkedHashMap<String, MessageObj> result = new LinkedHashMap<String, MessageObj>();
        for (Map.Entry<String, MessageObj> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    private synchronized Runnable setAsFail(final String port) {

        return new Runnable() {
            @Override
            public void run() {
                synchronized (avdStatus) {
//                    Log.d("FailedPort",port);
                    if (avdStatus.get(port) == 0)
                        notifyFail(port);
                }
            }
        };

    }


    public void incrementCount() {
        synchronized (countLock) {
            ++msgCounter;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
//        Log.d("Paused","WOw Paused");
    }

}

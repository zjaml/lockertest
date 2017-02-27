package com.example.jin.lockertest;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.util.Objects;

/**
 * Created by JZhao on 2/20/2017.
 */

public class FakeBTClient implements BluetoothClientInterface {
    private final String Tag = "FakeBTClient";
    private final Handler mHandler;
    private final SafeBroadcastReceiver mBluetoothBroadcastReceiver;
    private int mState;

    public FakeBTClient(Handler handler) {
        mState = STATE_NONE;
        mHandler = handler;
        mBluetoothBroadcastReceiver = new SafeBroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Objects.equals(intent.getAction(), BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                    Log.d(Tag, "bluetooth disconnection detected!");
                    // todo: crash report.
                    setState(STATE_NONE);
                }
            }
        };
    }

    @Override
    public int getState() {
        return mState;
    }

    @Override
    public SafeBroadcastReceiver getBluetoothBroadcastReceiver() {
        return mBluetoothBroadcastReceiver;
    }

    void setState(int state) {
        if (mState == STATE_CONNECTED && state != STATE_CONNECTED) {
            // if the state was connected and it changed, notify the caller the connect was lost so that
            // the caller may initiate connect again. we don't want to send noise to the caller because connect is an expensive call.
            mHandler.obtainMessage(Constants.MESSAGE_CONNECTION_LOST, state).sendToTarget();
            Log.d(Tag, "BT Connection Lost");
        }
        if (mState != STATE_CONNECTED && state == STATE_CONNECTED) {
            mHandler.obtainMessage(Constants.MESSAGE_CONNECTED, state).sendToTarget();
            Log.d(Tag, "BT Connection established");
        }
        mState = state;
    }

    @Override
    public boolean connect() {
        AsyncTask<Void, Void, Void> delayed = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Thread.sleep(2000);
                    setState(STATE_CONNECTED);
                    // use following to simulate occasional disconnection.
                    //don't wait too long here as it jams the queue, preventing future AsyncTask from running.
                    //executeOnExecutor helps a bit.
                    Thread.sleep(10000);
                    setState(STATE_NONE);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        //seem to run async tasks sequentially.
//        delayed.execute();
        delayed.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);

        return true;
    }

    @Override
    public void disconnect() {
        setState(STATE_NONE);
    }

    @Override
    public void sendCommand(final String command) {
        Log.d(Tag, String.format("Sending Command: %s", command));
        AsyncTask<Void, Void, Void> delayed = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Thread.sleep(500);
                    //simulate acknowledge.
                    mHandler.obtainMessage(Constants.MESSAGE_INCOMING_MESSAGE, "A").sendToTarget();
                    Thread.sleep(5000);
                    if (command.endsWith("T")) {
                        String box = command.substring(1, 3);
                        //simulate door lock
                        //40% chance of checkin with nothing.
                        String emptyFlag = (Math.random() * 10) > 6 ? "E" : "F";
                        mHandler.obtainMessage(Constants.MESSAGE_INCOMING_MESSAGE, String.format("%s%2s", emptyFlag, box)).sendToTarget();
                    }else if(command.endsWith("R")){
                        String box = command.substring(1, 3);
                        //simulate door lock
                        //40% chance of checkin with nothing.
                        String emptyFlag = (Math.random() * 10) > 6 ? "F" : "E";
                        mHandler.obtainMessage(Constants.MESSAGE_INCOMING_MESSAGE, String.format("%s%2s", emptyFlag, box)).sendToTarget();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        delayed.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
    }
}

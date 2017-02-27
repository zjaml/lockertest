package com.example.jin.lockertest;

/**
 * Created by JZhao on 2/20/2017.
 */

public interface BluetoothClientInterface {
    int STATE_NONE = 0;
    int STATE_CONNECTING = 1;
    int STATE_CONNECTED = 2;

    int getState();
    SafeBroadcastReceiver getBluetoothBroadcastReceiver();
    boolean connect();
    void disconnect();
    void sendCommand(String command);
}

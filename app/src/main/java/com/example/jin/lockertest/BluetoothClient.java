package com.example.jin.lockertest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Created by JZhao on 2/7/2017.
 * This class handles connection management and communication with the android board over Bluetooth
 * Connection Management
 * 1. query the system for paired device, if there's a paired device with the name of the android board,
 * attempt to connect to that device.
 * 2. Provide connect, disconnect method
 * 3. Support state query, state can be one of the following
 * * None
 * * Connecting  -- background thread is working on connecting in a loop.
 * * Connected  -- Connected thread is established and input/output stream is ready to use.
 *
 * 4. support connected/disconnected event
 * Communication
 * event for data sent/received over the bluetooth chanel
 * Issues:
 * How to detect disconnect? not listening to ACTION_ACL_DISCONNECTED for now. The ConnectedThread is
 * constantly read from the input stream, so hope it can pickup exception right after disconnected.
 * <p>
 * How to control reconnect
 * -> Limit retry of reconnect to 1 time only for simplicity
 * -> When disconnected, this class send message to handler, the caller can then recall the connect method
 */

public class BluetoothClient implements BluetoothClientInterface {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "BluetoothClient";

    public static final char DELIMITER = '\n';
    public static final String US_ASCII = "US-ASCII";
    private int mState;
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private String mTargetDeviceName;

    private SafeBroadcastReceiver mBluetoothBroadcastReceiver = null;

    public BluetoothClient(Handler handler, final String targetDeviceName) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = handler;
        mTargetDeviceName = targetDeviceName;
        setState(STATE_NONE);
        mBluetoothBroadcastReceiver = new SafeBroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Objects.equals(intent.getAction(), BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                    Log.d(TAG, "bluetooth disconnection detected!");
                    // todo: crash report.
                    setState(STATE_NONE);
                }
            }
        };
    }

    // define and set state machine so that operations can be controlled base on state,
    // like preventing duplicated connect thread when there's already a connect thread working.
    public synchronized int getState() {
        return mState;
    }

    public SafeBroadcastReceiver getBluetoothBroadcastReceiver() {
        return mBluetoothBroadcastReceiver;
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        if (mState == STATE_CONNECTED && state != STATE_CONNECTED) {
            // if the state was connected and it changed, notify the caller the connect was lost so that
            // the caller may initiate connect again. we don't want to send noise to the caller because connect is an expensive call.
            mHandler.obtainMessage(Constants.MESSAGE_CONNECTION_LOST, state).sendToTarget();
        }
        if (mState != STATE_CONNECTED && state == STATE_CONNECTED) {
            mHandler.obtainMessage(Constants.MESSAGE_CONNECTED, state).sendToTarget();
        }
        mState = state;
    }

    /**
     * try connect with the target device.
     *
     * @return when bluetooth is ready and target device is paired, return true and start connecting asynchronously,
     * otherwise return false to indicate immediate failure.
     */
    public synchronized boolean connect() {
        // try connect if bluetooth is ready and target device is paired.
        if (mAdapter.getState() != BluetoothAdapter.STATE_ON) {
            //try to asynchronously turn on the bluetooth.
            mAdapter.enable();
            //todo: report alert here.
            return false;
        }
        BluetoothDevice device = getTargetDevice();
        if (device == null) {
            //todo: report issue here.
            Log.w(TAG, "not paired with target device: " + mTargetDeviceName);
            return false;
        }
        if (getState() != STATE_CONNECTING) {
            //no need to retry connect if it's already connecting
            disconnect();
            // Start the thread to connect with the given device
            mConnectThread = new ConnectThread(device);
            mConnectThread.start();
        }
        return true;
    }

    public synchronized void disconnect() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * we don't want this method to perform simultaneously which may jam commands.
     * convert the command to byte stream, add delimiter and write to the ConnectedThread OutStream.
     *
     * @param command command to send to device
     * @see ConnectedThread#write(byte[])
     */
    public synchronized void sendCommand(String command) {
        if (getState() != STATE_CONNECTED)
            return;
        command = command + DELIMITER;
        mConnectedThread.write(command.getBytes(Charset.forName("US-ASCII")));
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     */
    private synchronized void startConnectedThread(BluetoothSocket socket) {
        Log.d(TAG, "startConnectedThread");
        disconnect();
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    private synchronized BluetoothDevice getTargetDevice() {
        Set<BluetoothDevice> devices = mAdapter.getBondedDevices();
        BluetoothDevice targetDevice = null;
        for (BluetoothDevice device : devices) {
            if (device.getName().equals(mTargetDeviceName)) {
                targetDevice = device;
            }
        }
        return targetDevice;
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. it keeps trying connecting to the remote device even if it fails until it is told to stop.
     */
    private class ConnectThread extends Thread {
        //        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private boolean mmStopSignal = false;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
        }

        public void run() {
            Log.i(TAG, "BEGIN connecting to device:" + mmDevice.getName());
            //TODO: Crash report.
            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();
            setState(STATE_CONNECTING);
            // Make a connection to the BluetoothSocket
            while (!mmStopSignal) {
                //creaing a new socket every time because if the socket is closed, it will fail at connect() every time,
                //making the infinite attempt infinite failure.
                BluetoothSocket socket = null;
                try {
                    Thread.sleep(1000);
                    // Get a BluetoothSocket for a connection with the
                    // given BluetoothDevice
                    try {
                        socket = mmDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (IOException e) {
                        Log.e(TAG, "attempt to connect to device failed", e);
                        disconnect();
                    }
                    socket.connect();
                } catch (IOException e) {
                    // Close the socket
                    Log.e(TAG, "error occurred at connect ", e);
                    try {
                        socket.close();
                    } catch (IOException e2) {
                        //TODO: crash report
                        Log.e(TAG, "unable to close socket during connection failure", e2);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (socket.isConnected()) {
                    // Start the startConnectedThread thread
                    startConnectedThread(socket);
                    break;
                }
            }
        }

        //signal the connecting loop to stop
        public void cancel() {
            mmStopSignal = true;
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private StringBuilder mmMessageBuffer = new StringBuilder();

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create startConnectedThread thread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
                disconnect();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            setState(STATE_CONNECTED);
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (isConnected()) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    String data = new String(buffer, US_ASCII);
                    for (char ch : data.toCharArray()) {
                        if (ch != DELIMITER) {
                            mmMessageBuffer.append(ch);
                        } else {
                            String message = mmMessageBuffer.toString();
                            // Send the obtained message to caller
                            mHandler.obtainMessage(Constants.MESSAGE_INCOMING_MESSAGE, message)
                                    .sendToTarget();
                            mmMessageBuffer = new StringBuilder();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    // this is not reliable for detecting bluetooth disconnection. Need to listen to system event!
                    disconnect();
                }
            }
        }

        public boolean isConnected() {
            return mmSocket != null && mmSocket.isConnected();
        }

        /**
         * Write bytes buffer to the ConnectedThread OutStream.
         *
         * @param buffer byte array to send via BT.
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during sendCommand", e);
                disconnect();
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}

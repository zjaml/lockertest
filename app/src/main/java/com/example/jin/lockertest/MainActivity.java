package com.example.jin.lockertest;

import android.bluetooth.BluetoothDevice;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {
    TextView doorNumberText, logText;
    Button checkInButton, checkOutButton, doorButton, emptyButton, clearButton;

    private BluetoothClientInterface mBluetoothClient;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_CONNECTION_LOST:
                    // reconnect since connection is lost
                    if (mBluetoothClient != null) {
                        mBluetoothClient.connect();
                    }
                    setUiEnabled(false);
                    break;
                case Constants.MESSAGE_CONNECTED:
                    setUiEnabled(true);
                    break;
                case Constants.MESSAGE_INCOMING_MESSAGE:
                    String message = (String) msg.obj;
                    tvAppend(logText, String.format("Message received: %s",  message.concat("\n")));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        doorNumberText = (TextView) findViewById(R.id.doorNumberText);
        logText = (TextView) findViewById(R.id.logText);
        logText.setMovementMethod(new ScrollingMovementMethod());
        checkInButton = (Button) findViewById(R.id.checkInButton);
        checkOutButton = (Button) findViewById(R.id.checkoutButton);
        doorButton = (Button) findViewById(R.id.doorButton);
        emptyButton = (Button) findViewById(R.id.emptyButton);
        clearButton = (Button) findViewById(R.id.clearButton);

        setUiEnabled(false);

        mBluetoothClient = new FakeBTClient(mHandler);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothClient.disconnect();
        mBluetoothClient.getBluetoothBroadcastReceiver().safeUnregister(this);
        mBluetoothClient = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothClient != null && mBluetoothClient.getState() == BluetoothClient.STATE_NONE) {
            mBluetoothClient.connect();
            mBluetoothClient.getBluetoothBroadcastReceiver()
                    .safeRegister(this, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        }
    }

    public boolean isBtConnected() {
        return mBluetoothClient != null && mBluetoothClient.getState() == BluetoothClientInterface.STATE_CONNECTED;
    }

    public void setUiEnabled(boolean bool) {
        checkInButton.setEnabled(bool);
        checkOutButton.setEnabled(bool);
        doorButton.setEnabled(bool);
        emptyButton.setEnabled(bool);
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }

    private void sendCommand(String commandFormatter) {
        if (isBtConnected()) {
            //trim does the magic when door number is not specified for door/empty command.
            String command = String.format(commandFormatter, doorNumberText.getText()).trim();
            mBluetoothClient.sendCommand(command);
//            serialPort.write(command.getBytes(Charset.forName("ASCII")));
            tvAppend(logText, String.format("\nCommand sent: %s \n", command));
        }
    }

    public void onCheckInClicked(View view) {
        sendCommand("O%2sT");
    }

    public void onCheckOutClicked(View view) {
        sendCommand("O%2sR");
    }

    public void onDoorClicked(View view) {
        sendCommand("D%2s");
    }

    public void onEmptyClicked(View view) {
        sendCommand("E%2s");
    }

    public void onClearClicked(View view) {
        logText.setText("");
    }

    public void onChargeClicked(View view) {
        Log.d("CHARGE", "LOW");
        sendCommand("LOW");
    }

    public void onDischargeClicked(View view) {
        Log.d("CHARGE", "HIGH");
        sendCommand("HIGH");
    }
}



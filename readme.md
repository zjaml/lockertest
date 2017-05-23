# Prerequisite
Android device need to support OTG mode.

# Install
Build source with Android Studio.

# How to use
<img src="images/1.jpg" width="300px">
1. Modify the MainActivity with correct target device name

```
public static final String TARGET_DEVICE_NAME = "Nexus 7";
```

2. Pair the Arduino board with android.
3. The app will try to connect the board once running until it's connected. Watch the logs if the connection does not establish.

## connect the board
* Open the app and connect the board. 
* Grant USB permission to the app.
* The button will be enabled when connection is established.

## Log Area
 show command issued and response from the board.
## Check in Command
Input door number and press C-IN button.
## Check out command
Input door number and press C-OUT button.
## Door state query
* Input door number and press DOOR button.
* if door number is empty, door state query for all boxes will be issued.

## Empty state query
* Input door number and press EMPTY button.
* if door number is empty, state query for all boxes will be issued.

## Charging control
* press CHARGE/DISCHARGE to simulate the LOW/HIGH command, making test easier.

# Separation of BluetoothClient Library
The BluetoothClient library is separated into an independent library in https://github.com/zjaml/BluetoothClient using https://jitpack.io.
Needed to add the library to jitpack repo referencing https://jitpack.io/docs/BUILDING/#gradle-projects.
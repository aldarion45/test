package com.idevicesinc.sweetblue;


import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import com.idevicesinc.sweetblue.utils.Utils_String;


public class P_BleReceiverManager
{

    private final BleManager mManager;
    private int mNativeState;
    private BleReceiver mReceiver;


    P_BleReceiverManager(BleManager mgr)
    {
        mManager = mgr;
        mReceiver = new BleReceiver();
        mManager.getAppContext().registerReceiver(mReceiver, createFilter());
    }

    void onDestroy()
    {
        mManager.getAppContext().unregisterReceiver(mReceiver);
    }

    private static IntentFilter createFilter()
    {
        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        return filter;
    }

    private class BleReceiver extends BroadcastReceiver
    {
        @Override public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            {
                onNativeBleStateChanged(intent);
            }
        }
    }

    private void onNativeBleStateChanged(Intent intent)
    {
        final int previousNativeState = intent.getExtras().getInt(BluetoothAdapter.EXTRA_PREVIOUS_STATE);
        final int newNativeState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

        int logLevel = newNativeState == BluetoothAdapter.ERROR || previousNativeState == BluetoothAdapter.ERROR ? Log.WARN : Log.INFO;
        mManager.getLogger().log(logLevel, Utils_String.concatStrings("previous=", mManager.getLogger().gattBleState(previousNativeState),
                " new=", mManager.getLogger().gattBleState(newNativeState)));

        handleNativeBleStateChange(previousNativeState, newNativeState);
    }

    private void handleNativeBleStateChange(int previousNativeState, int newNativeState)
    {
        BluetoothAdapter bluetoothAdapter = mManager.getNativeAdapter();
        final int adapterState = bluetoothAdapter.getState();

        final boolean hitErrorState = newNativeState == BluetoothAdapter.ERROR;

        if (hitErrorState)
        {
            newNativeState = adapterState;

            if (newNativeState /*still*/ == BluetoothAdapter.ERROR)
            {
                return; // really not sure what we can do better here besides bailing out.
            }
        }
        switch (newNativeState)
        {
            case BleStatuses.STATE_TURNING_OFF:
                if (!mManager.mTaskManager.isCurrent(P_Task_TurnBleOff.class, mManager))
                {
                    // TODO - Implement disconnect all for turning off

                    mManager.mTaskManager.add(new P_Task_TurnBleOff(mManager.mTaskManager));

                    if (mManager.mServer != null)
                    {
                        // TODO - Disconnect all for turning off
                    }
                }
                mManager.mTaskManager.failTask(P_Task_TurnBleOn.class, mManager);
                break;

            case BleStatuses.STATE_OFF:
                // TODO - Release wake lock, when implemented

                // If the current task is turning on, then simply return here
                if (mManager.mTaskManager.isCurrent(P_Task_TurnBleOn.class, mManager))
                {
                    return;
                }

                mManager.mTaskManager.failTask(P_Task_TurnBleOn.class, mManager);

                mManager.mTaskManager.succeedTask(P_Task_TurnBleOff.class, mManager);
                // TODO - Undiscover all devices
                break;

            case BleStatuses.STATE_TURNING_ON:
                if (!mManager.mTaskManager.isCurrent(P_Task_TurnBleOn.class, mManager))
                {
                    mManager.mTaskManager.add(new P_Task_TurnBleOn(mManager.mTaskManager));
                }

                mManager.mTaskManager.failTask(P_Task_TurnBleOff.class, mManager);
                break;

            case BleStatuses.STATE_ON:
                mManager.mTaskManager.failTask(P_Task_TurnBleOff.class, mManager);
                mManager.mTaskManager.succeedTask(P_Task_TurnBleOn.class, mManager);
                break;
        }

        if (previousNativeState == newNativeState)
        {
            return;
        }

        BleManagerState previousState = BleManagerState.get(previousNativeState);
        BleManagerState newState = BleManagerState.get(newNativeState);

        mManager.getLogger().e(previousNativeState + " " + newNativeState + " " + previousState + " " + newState);

        mManager.getNativeStateTracker().update(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, previousState, false, newState, true);
        mManager.getStateTracker().update(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, previousState, false, newState, true);

        // TODO - Implement checks for errors (like being unable to turn off Ble)
        if( previousNativeState != BluetoothAdapter.STATE_ON && newNativeState == BluetoothAdapter.STATE_ON )
        {
            // TODO - Rediscover, and reconnect devices
        }

        if( hitErrorState )
        {
            // TODO - Throw error to errorlistener
        }

        if( previousNativeState == BluetoothAdapter.STATE_TURNING_OFF && newNativeState == BluetoothAdapter.STATE_ON )
        {
            // TODO - Throw error to errorlistener
        }
        else if( previousNativeState == BluetoothAdapter.STATE_TURNING_ON && newNativeState == BluetoothAdapter.STATE_OFF )
        {
            // TODO - Throw error to errorlistener
        }
    }

}
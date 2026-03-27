package com.example.batmondemo;

import android.app.Activity;
import android.hardware.BatteryState;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.InputDevice;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int SONY_VENDOR_ID = 0x054C;
    private static final int[] DUALSHOCK4_PRODUCT_IDS = {
            0x05C4, // CUH-ZCT1
            0x09CC  // CUH-ZCT2
    };
    private static final long REFRESH_INTERVAL_MS = 5_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private InputManager inputManager;
    private TextView deviceInfoView;

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            refreshControllerInfo();
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final InputManager.InputDeviceListener inputDeviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    refreshControllerInfo();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    refreshControllerInfo();
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    refreshControllerInfo();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inputManager = getSystemService(InputManager.class);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        root.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText(R.string.controller_monitor_title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(R.string.controller_monitor_subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        deviceInfoView = new TextView(this);
        deviceInfoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        deviceInfoView.setPadding(0, dpToPx(12), 0, 0);
        deviceInfoView.setText(R.string.loading_controller_info);

        root.addView(titleView);
        root.addView(subtitleView);
        root.addView(deviceInfoView);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(inputDeviceListener, mainHandler);
        }
        refreshControllerInfo();
        mainHandler.removeCallbacks(periodicRefresh);
        mainHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(inputDeviceListener);
        }
        mainHandler.removeCallbacks(periodicRefresh);
    }

    private void refreshControllerInfo() {
        if (deviceInfoView == null) {
            return;
        }
        if (inputManager == null) {
            deviceInfoView.setText(R.string.input_manager_unavailable);
            return;
        }

        int[] inputDeviceIds = inputManager.getInputDeviceIds();
        List<String> ps4Controllers = new ArrayList<>();
        for (int deviceId : inputDeviceIds) {
            InputDevice device = inputManager.getInputDevice(deviceId);
            if (device == null || !isGamepad(device) || !isPs4Controller(device)) {
                continue;
            }
            ps4Controllers.add(formatControllerInfo(device));
        }

        if (ps4Controllers.isEmpty()) {
            deviceInfoView.setText(R.string.no_ps4_controller_connected);
            return;
        }
        deviceInfoView.setText(TextUtils.join("\n\n", ps4Controllers));
    }

    private boolean isGamepad(InputDevice device) {
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                || device.supportsSource(InputDevice.SOURCE_JOYSTICK);
    }

    private boolean isPs4Controller(InputDevice device) {
        int vendorId = device.getVendorId();
        int productId = device.getProductId();
        if (vendorId == SONY_VENDOR_ID && isDualShock4ProductId(productId)) {
            return true;
        }

        String lowerName = device.getName() == null
                ? ""
                : device.getName().toLowerCase(Locale.US);
        if (lowerName.contains("dualshock")) {
            return true;
        }

        // Some DS4 connections expose vendor but miss product id.
        return vendorId == SONY_VENDOR_ID && productId == 0
                && lowerName.contains("wireless controller");
    }

    private boolean isDualShock4ProductId(int productId) {
        for (int knownProductId : DUALSHOCK4_PRODUCT_IDS) {
            if (knownProductId == productId) {
                return true;
            }
        }
        return false;
    }

    private String formatControllerInfo(InputDevice device) {
        StringBuilder builder = new StringBuilder();
        String deviceName = device.getName() == null
                ? getString(R.string.unknown_device_name)
                : device.getName();
        builder.append(getString(R.string.controller_name_line, deviceName));
        builder.append("\n")
                .append(getString(R.string.controller_status_line,
                        getString(R.string.controller_status_connected)));
        builder.append("\n")
                .append(getString(R.string.controller_battery_line, readBatteryPercent(device)));
        builder.append("\n")
                .append(getString(R.string.controller_vendor_product_line,
                        hex4(device.getVendorId()), hex4(device.getProductId())));
        builder.append("\n")
                .append(getString(R.string.controller_device_id_line, device.getId()));
        return builder.toString();
    }

    private String readBatteryPercent(InputDevice device) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return getString(R.string.battery_api_not_supported);
        }
        try {
            BatteryState batteryState = device.getBatteryState();
            if (batteryState == null || !batteryState.isPresent()) {
                return getString(R.string.battery_unavailable);
            }

            float capacity = batteryState.getCapacity();
            if (Float.isNaN(capacity) || capacity < 0f) {
                return getString(R.string.battery_unavailable);
            }

            float normalized = capacity > 1.0f ? capacity : capacity * 100f;
            int percentage = Math.round(normalized);
            percentage = Math.max(0, Math.min(100, percentage));
            return getString(
                    R.string.battery_percentage_format,
                    percentage,
                    batteryStatusLabel(batteryState.getStatus())
            );
        } catch (Exception exception) {
            Log.w(TAG, "Failed to read battery state from InputDevice", exception);
            return getString(R.string.battery_unavailable);
        }
    }

    private String batteryStatusLabel(int status) {
        switch (status) {
            case BatteryState.STATUS_CHARGING:
                return getString(R.string.battery_status_charging);
            case BatteryState.STATUS_DISCHARGING:
                return getString(R.string.battery_status_discharging);
            case BatteryState.STATUS_FULL:
                return getString(R.string.battery_status_full);
            case BatteryState.STATUS_NOT_CHARGING:
                return getString(R.string.battery_status_not_charging);
            default:
                return getString(R.string.battery_status_unknown);
        }
    }

    private String hex4(int value) {
        return String.format(Locale.US, "0x%04X", value & 0xFFFF);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

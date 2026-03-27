package com.example.batmondemo;

import android.app.Activity;
import android.hardware.BatteryState;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.InputDevice;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
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
        titleView.setText("BatMon - PS4 Controller Monitor");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);

        TextView subtitleView = new TextView(this);
        subtitleView.setText("Theo doi tay cam PS4 (DualShock 4) va muc pin hien tai");
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

        deviceInfoView = new TextView(this);
        deviceInfoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        deviceInfoView.setPadding(0, dpToPx(12), 0, 0);
        deviceInfoView.setText("Dang tai thong tin tay cam...");

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
            deviceInfoView.setText("InputManager khong san sang tren thiet bi nay.");
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
            deviceInfoView.setText("Khong phat hien tay cam PS4 (DualShock 4) dang ket noi.");
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
        if (lowerName.contains("dualshock 4") || lowerName.contains("dualshock")) {
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
        builder.append("Ten: ").append(device.getName());
        builder.append("\nTrang thai: Connected");
        builder.append("\nPin: ").append(readBatteryPercent(device));
        builder.append("\nVendor/Product: ")
                .append(hex4(device.getVendorId()))
                .append("/")
                .append(hex4(device.getProductId()));
        builder.append("\nDevice ID: ").append(device.getId());
        return builder.toString();
    }

    private String readBatteryPercent(InputDevice device) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return "Khong ho tro tren API < 31";
        }
        try {
            BatteryState batteryState = device.getBatteryState();
            if (batteryState == null || !batteryState.isPresent()) {
                return "Khong lay duoc pin";
            }

            float capacity = batteryState.getCapacity();
            if (Float.isNaN(capacity) || capacity < 0f) {
                return "Khong lay duoc pin";
            }

            int percentage = Math.round(capacity * 100f);
            if (percentage < 0) {
                percentage = 0;
            } else if (percentage > 100) {
                percentage = 100;
            }
            return percentage + "% (" + batteryStatusLabel(batteryState.getStatus()) + ")";
        } catch (Exception ignored) {
            return "Khong lay duoc pin";
        }
    }

    private String batteryStatusLabel(int status) {
        switch (status) {
            case BatteryState.STATUS_CHARGING:
                return "Charging";
            case BatteryState.STATUS_DISCHARGING:
                return "Discharging";
            case BatteryState.STATUS_FULL:
                return "Full";
            case BatteryState.STATUS_NOT_CHARGING:
                return "Not charging";
            default:
                return "Unknown";
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

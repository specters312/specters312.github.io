package io.github.specters312.intentlab;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class IntentCorpus {
    private IntentCorpus() {}

    static final List<String> COMMON_ACTIONS = Collections.unmodifiableList(Arrays.asList(
            "android.settings.SETTINGS",
            "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
            "android.settings.DEVICE_INFO_SETTINGS",
            "android.settings.USB_SETTINGS",
            "android.settings.TESTING_SETTINGS",
            "android.settings.SHOW_REGULATORY_INFO",
            "android.settings.WIFI_SETTINGS",
            "android.settings.WIFI_IP_SETTINGS",
            "android.settings.BLUETOOTH_SETTINGS",
            "android.settings.WIRELESS_SETTINGS",
            "android.settings.panel.action.INTERNET_CONNECTIVITY",
            "android.settings.panel.action.WIFI",
            "android.settings.NFC_SETTINGS",
            "android.settings.panel.action.NFC",
            "android.settings.AIRPLANE_MODE_SETTINGS",
            "android.settings.NETWORK_OPERATOR_SETTINGS",
            "android.settings.DATA_ROAMING_SETTINGS",
            "android.settings.VPN_SETTINGS",
            "android.settings.CAST_SETTINGS",
            "android.settings.MANAGE_APPLICATIONS_SETTINGS",
            "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
            "android.settings.MANAGE_UNKNOWN_APP_SOURCES",
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            "android.settings.USAGE_ACCESS_SETTINGS",
            "android.settings.action.MANAGE_OVERLAY_PERMISSION",
            "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION",
            "android.settings.action.MANAGE_WRITE_SETTINGS",
            "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS",
            "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS",
            "android.settings.DISPLAY_SETTINGS",
            "android.settings.SOUND_SETTINGS",
            "android.settings.panel.action.VOLUME",
            "android.settings.NOTIFICATION_SETTINGS",
            "android.settings.ZEN_MODE_PRIORITY_SETTINGS",
            "android.settings.AUTO_ROTATE_SETTINGS",
            "android.settings.HOME_SETTINGS",
            "android.settings.CAPTIONING_SETTINGS",
            "android.settings.SECURITY_SETTINGS",
            "android.settings.PRIVACY_SETTINGS",
            "android.settings.LOCATION_SOURCE_SETTINGS",
            "android.settings.BIOMETRIC_ENROLL",
            "com.android.settings.TRUSTED_CREDENTIALS_USER",
            "android.settings.DEVICE_ADMIN_SETTINGS",
            "android.settings.ACCESSIBILITY_SETTINGS",
            "android.settings.INPUT_METHOD_SETTINGS",
            "android.settings.HARD_KEYBOARD_SETTINGS",
            "android.settings.LOCALE_SETTINGS",
            "android.settings.DATE_SETTINGS",
            "android.settings.INTERNAL_STORAGE_SETTINGS",
            "android.settings.MEMORY_CARD_SETTINGS",
            "android.settings.BATTERY_SAVER_SETTINGS",
            "android.settings.ACTION_PRINT_SETTINGS",
            "android.settings.SYNC_SETTINGS"
    ));
}

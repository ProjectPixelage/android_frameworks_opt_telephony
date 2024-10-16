/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.telephony;

import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.TextUtils;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierKeyDownloadMgrTest extends TelephonyTest {

    private static final String LOG_TAG = "CarrierKeyDownloadManager";

    private CarrierKeyDownloadManager mCarrierKeyDM;

    private PersistableBundle mBundle;

    private final String mURL = "http://www.google.com";

    private static final String CERT = "-----BEGIN CERTIFICATE-----\r\nMIIFjzCCBHegAwIBAgIUPxj3SLif82Ky1RlUy8p2EWJCh8MwDQYJKoZIhvcNAQELBQAwgY0xCzAJBgNVBAYTAk5MMRIwEAYDVQQHEwlBbXN0ZXJkYW0xJTAjBgNVBAoTHFZlcml6b24gRW50ZXJwcmlzZSBTb2x1dGlvbnMxEzARBgNVBAsTCkN5YmVydHJ1c3QxLjAsBgNVBAMTJVZlcml6b24gUHVibGljIFN1cmVTZXJ2ZXIgQ0EgRzE0LVNIQTIwHhcNMTcwODE0MTc0MzM4WhcNMTkwODE0MTc0MzM4WjCBmTELMAkGA1UEBhMCVVMxEzARBgNVBAgTCk5ldyBKZXJzZXkxFjAUBgNVBAcTDUJhc2tpbmcgUmlkZ2UxIjAgBgNVBAoTGVZlcml6b24gRGF0YSBTZXJ2aWNlcyBMTEMxHzAdBgNVBAsTFk5ldHdvcmsgU3lzdGVtIFN1cHBvcnQxGDAWBgNVBAMTD3ZpMWx2Lmltc3ZtLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALUQKWTHi4Hjpd1LQwJ87RXa0Rs3rVonvVevliqdUH5BikjhAzvIqwPSXeRQqkaRTFIyp0NKcNqGdjAaHRo43gdHeWSH331sS6CMZDg988gZznskzCqJJo6ii5FuLC8qe2YDsHxT+CefXev2rn6Bj1ei2X74uZsy5KlkBRZfFHtPdK6/EK5TpzrvcXfDyOK1rn8FTno1bQOTAhL39GPcLhdrXV7AN+lu+EBpdCqlTdcoDxsqavi/91MwUIVEzxJmycKloT6OWfU44r7+L5SYYgc88NTaGL/BvCFwHRIa1ZgYSGeAPes45792MGG7tfr/ttAGp9UEwTv2zWTxzWnRP/UCAwEAAaOCAdcwggHTMAwGA1UdEwEB/wQCMAAwTAYDVR0gBEUwQzBBBgkrBgEEAbE+ATIwNDAyBggrBgEFBQcCARYmaHR0cHM6Ly9zZWN1cmUub21uaXJvb3QuY29tL3JlcG9zaXRvcnkwgakGCCsGAQUFBwEBBIGcMIGZMC0GCCsGAQUFBzABhiFodHRwOi8vdnBzc2cxNDIub2NzcC5vbW5pcm9vdC5jb20wMwYIKwYBBQUHMAKGJ2h0dHA6Ly9jYWNlcnQub21uaXJvb3QuY29tL3Zwc3NnMTQyLmNydDAzBggrBgEFBQcwAoYnaHR0cDovL2NhY2VydC5vbW5pcm9vdC5jb20vdnBzc2cxNDIuZGVyMBoGA1UdEQQTMBGCD3ZpMWx2Lmltc3ZtLmNvbTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB8GA1UdIwQYMBaAFOQtu5EBZSYftHo/oxUlpM6MRDM7MD4GA1UdHwQ3MDUwM6AxoC+GLWh0dHA6Ly92cHNzZzE0Mi5jcmwub21uaXJvb3QuY29tL3Zwc3NnMTQyLmNybDAdBgNVHQ4EFgQUv5SaSyNM/yXw1v0N9TNpjsFCaPcwDQYJKoZIhvcNAQELBQADggEBACNJusTULj1KyV4RwiskKfp4wI9Hsz3ESbZS/ijF9D57BQ0UwkELU9r6rEAhsYLUvMq4sDhDbYIdupgP4MBzFnjkKult7VQm5W3nCcuHgXYFAJ9Y1a4OZAo/4hrHj70W9TsQ1ioSMjUT4F8bDUYZI0kcyH8e/+2DaTsLUpHw3L+Keu8PsJVBLnvcKJjWrZD/Bgd6JuaTX2G84i0rY0GJuO9CxLNJa6n61Mz5cqLYIuwKgiVgTA2n71YITyFICOFPFX1vSx35AWvD6aVYblxtC8mpCdF2h4s1iyrpXeji2GCJLwsNVtTtNQ4zWX3Gnq683wzkYZeyOHUyftIgAQZ+HsY=\r\n-----END CERTIFICATE-----";
    private static final long CERT_EXPIRATION = 1565804618000L; //milliseconds since the epoch
    private final String mJsonStr = "{ \"carrier-keys\": [ { \"certificate\": \"" + CERT
            + "\", \"key-type\": \"WLAN\", \"key-identifier\": \"key1=value\", "
            + "\"expiration-date\": 1502577746000 }, { \"certificate\": \""
            + CERT
            + "\", \"key-type\": \"WLAN\", \"key-identifier\": \"key1=value\", "
            + "\"expiration-date\": 1502577746000 }]}";

    private final String mJsonStr1 = "{ \"carrier-keys\": [ { \"public-key\": \"" + CERT
            + "\", \"key-type\": \"WLAN\", \"key-identifier\": \"key1=value\", "
            + "\"expiration-date\": 1502577746000 }, { \"public-key\": \""
            + CERT
            + "\", \"key-type\": \"WLAN\", \"key-identifier\": \"key1=value\", "
            + "\"expiration-date\": 1502577746000 }]}";

    private final String mJsonStr3GppSpec =
            "{ \"carrier-keys\": [ { \"key-identifier\": \"key1=value\", "
                    + "\"public-key\": \"" + CERT + "\"}]}";

    private CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener;

    @Before
    public void setUp() throws Exception {
        logd("CarrierActionAgentTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(mBundle);
        when(mUserManager.isUserUnlocked()).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked()).thenReturn(false);
        // Capture listener to emulate the carrier config change notification used later
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        mCarrierKeyDM = new CarrierKeyDownloadManager(mPhone);
        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                listenerArgumentCaptor.capture());
        mCarrierConfigChangeListener = listenerArgumentCaptor.getAllValues().get(0);
        mConnectivityManager = (ConnectivityManager) mPhone.getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network network = Mockito.mock(Network.class);
        when(mConnectivityManager.getActiveNetwork()).thenReturn(network);
        processAllMessages();
        logd("CarrierActionAgentTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mCarrierKeyDM = null;
        super.tearDown();
    }

    /* Checks if the expiration date is calculated correctly
     * In this case the expiration date should be the next day.
     */
    @Test
    @SmallTest
    public void testExpirationDate1Day() {
        java.security.PublicKey publicKey = null;
        mCarrierKeyDM.mKeyAvailability = 3;
        SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 6);
        Date date = cal.getTime();
        Calendar expectedCal = new GregorianCalendar();
        expectedCal.add(Calendar.DATE, 1);
        String dateExpected = dt.format(expectedCal.getTime());
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("mcc", "mnc", 1,
                "keyIdentifier", publicKey, date, 1);
        when(mPhone.getCarrierInfoForImsiEncryption(anyInt(), anyBoolean()))
                .thenReturn(imsiEncryptionInfo);
        Date expirationDate = new Date(mCarrierKeyDM.getExpirationDate());
        assertEquals(dt.format(expirationDate), dateExpected);
    }

    /**
     * Checks if the expiration date is calculated correctly
     * In this case the expiration date should be within the window (7 to 21 days).
     **/
    @Test
    @SmallTest
    public void testExpirationDate7Day() {
        java.security.PublicKey publicKey = null;
        mCarrierKeyDM.mKeyAvailability = 3;
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, 30);
        Date date = cal.getTime();
        Calendar minExpirationCal = new GregorianCalendar();
        Calendar maxExpirationCal = new GregorianCalendar();
        minExpirationCal.add(Calendar.DATE, 23);
        maxExpirationCal.add(Calendar.DATE, 9);
        Date minExpirationDate = minExpirationCal.getTime();
        Date maxExpirationDate = maxExpirationCal.getTime();
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("mcc", "mnc", 1,
                "keyIdentifier", publicKey, date, 1);
        when(mPhone.getCarrierInfoForImsiEncryption(anyInt(), anyBoolean()))
                .thenReturn(imsiEncryptionInfo);
        Date expirationDate = new Date(mCarrierKeyDM.getExpirationDate());
        assertTrue(expirationDate.before(minExpirationDate));
        assertTrue(expirationDate.after(maxExpirationDate));
    }

    /**
     * Checks if the json is parse correctly.
     * Verify that setCarrierInfoForImsiEncryption is called with the right params
     **/
    @Test
    @SmallTest
    public void testParseJson() {
        Pair<PublicKey, Long> keyInfo = null;
        try {
            keyInfo = CarrierKeyDownloadManager.getKeyInformation(CERT.getBytes());
        } catch (Exception e) {
            fail(LOG_TAG + "exception creating public key");
        }
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("310", "270", 2,
                "key1=value", keyInfo.first, new Date(keyInfo.second), 1);
        String mccMnc = "310270";
        mCarrierKeyDM.parseJsonAndPersistKey(mJsonStr, mccMnc, 1);
        verify(mPhone, times(2)).setCarrierInfoForImsiEncryption(
                (ArgumentMatchers.refEq(imsiEncryptionInfo)));
    }

    /**
     * Checks if the json is parse correctly.
     * Same as testParseJason, except that the test looks for the "public-key" field.
     **/
    @Test
    @SmallTest
    public void testParseJsonPublicKey() {
        Pair<PublicKey, Long> keyInfo = null;
        try {
            keyInfo = CarrierKeyDownloadManager.getKeyInformation(CERT.getBytes());
        } catch (Exception e) {
            fail(LOG_TAG + "exception creating public key");
        }
        PublicKey publicKey = keyInfo.first;
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("310", "270", 2,
                "key1=value", publicKey, new Date(keyInfo.second), 1);
        String mccMnc = "310270";
        mCarrierKeyDM.parseJsonAndPersistKey(mJsonStr1, mccMnc, 1);
        verify(mPhone, times(2)).setCarrierInfoForImsiEncryption(
                (ArgumentMatchers.refEq(imsiEncryptionInfo)));
    }

    public void testParseJsonPublicKey(String mcc, String mnc, int carrierId) {
        Pair<PublicKey, Long> keyInfo = null;
        try {
            keyInfo = CarrierKeyDownloadManager.getKeyInformation(CERT.getBytes());
        } catch (Exception e) {
            fail(LOG_TAG + "exception creating public key");
        }
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo(mcc, mnc, 2,
                "key1=value", keyInfo.first, new Date(keyInfo.second), carrierId);
        String mccMnc = mcc + mnc;
        mCarrierKeyDM.parseJsonAndPersistKey(mJsonStr1, mccMnc, carrierId);
        verify(mPhone, times(2)).setCarrierInfoForImsiEncryption(
                (ArgumentMatchers.refEq(imsiEncryptionInfo)));
    }

    @Test
    public void testParseJsonPublicKeyOfMNO() {
        testParseJsonPublicKey("311", "480", 1839);
    }

    @Test
    public void testParseJsonPublicKeyOfMVNO() {
        testParseJsonPublicKey("311", "480", 2146);
    }

    /**
     * Checks if the json is parse correctly.
     * Since the json is bad, we want to verify that savePublicKey is not called.
     **/
    @Test
    @SmallTest
    public void testParseBadJsonFail() {
        String mccMnc = "310290";
        String badJsonStr = "{badJsonString}";
        mCarrierKeyDM.parseJsonAndPersistKey(badJsonStr, mccMnc, 1);
        verify(mPhone, times(0)).setCarrierInfoForImsiEncryption(any());
    }

    /**
     * Checks if the download is valid.
     * returns true since the mnc/mcc is valid.
     **/
    @Test
    @SmallTest
    public void testIsValidDownload() {
        String currentMccMnc = "310260";
        long currentDownloadId = 1;
        int carrierId = 1;
        // mock downloadId to match
        mCarrierKeyDM.mMccMncForDownload = currentMccMnc;
        mCarrierKeyDM.mDownloadId = currentDownloadId;
        mCarrierKeyDM.mCarrierId = carrierId;

        assertTrue(mCarrierKeyDM.isValidDownload(currentMccMnc, currentDownloadId, carrierId));
    }

    /**
     * Checks if the download is valid.
     * returns false since the mnc/mcc is in-valid.
     **/
    @Test
    @SmallTest
    public void testIsValidDownloadFail() {
        String currentMccMnc = "310260";
        long currentDownloadId = 1;
        int carrierId = 1;

        // mock downloadId to match, mccmnc so it doesn't match
        mCarrierKeyDM.mMccMncForDownload = "310290";
        mCarrierKeyDM.mDownloadId = currentDownloadId;
        mCarrierKeyDM.mCarrierId = carrierId;
        assertFalse(mCarrierKeyDM.isValidDownload(currentMccMnc, currentDownloadId, carrierId));

        // pass in mccmnc to match, and mock shared pref downloadId so it doesn't match
        currentMccMnc = "310290";
        mCarrierKeyDM.mDownloadId = currentDownloadId + 1;
        assertFalse(mCarrierKeyDM.isValidDownload(currentMccMnc, currentDownloadId, carrierId));

        // mccmnc and downloadID matches but carrierId don't matches
        mCarrierKeyDM.mDownloadId = currentDownloadId;
        mCarrierKeyDM.mCarrierId = carrierId + 1;
        assertFalse(mCarrierKeyDM.isValidDownload(currentMccMnc, currentDownloadId, carrierId));
    }

    /**
     * Tests if the key is enabled.
     * tests for all bit-mask value.
     **/
    @Test
    @SmallTest
    public void testIsKeyEnabled() {
        mCarrierKeyDM.mKeyAvailability = 3;
        assertTrue(mCarrierKeyDM.isKeyEnabled(1));
        assertTrue(mCarrierKeyDM.isKeyEnabled(2));
        mCarrierKeyDM.mKeyAvailability = 2;
        assertFalse(mCarrierKeyDM.isKeyEnabled(1));
        assertTrue(mCarrierKeyDM.isKeyEnabled(2));
        mCarrierKeyDM.mKeyAvailability = 1;
        assertTrue(mCarrierKeyDM.isKeyEnabled(1));
        assertFalse(mCarrierKeyDM.isKeyEnabled(2));
    }

    /**
     * Tests sending the ACTION_DOWNLOAD_COMPLETE intent.
     * Verify that the alarm will kick-off the next day.
     **/
    @Test
    @SmallTest
    public void testDownloadComplete() {
        String mccMnc = "310260";
        long downloadId = 1;
        mCarrierKeyDM.mMccMncForDownload = mccMnc;
        mCarrierKeyDM.mDownloadId = downloadId;
        mCarrierKeyDM.mCarrierId = 1;

        SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");
        Calendar expectedCal = new GregorianCalendar();
        expectedCal.add(Calendar.DATE, 1);
        String dateExpected = dt.format(expectedCal.getTime());

        when(mPhone.getOperatorNumeric()).thenReturn("310260");
        when(mPhone.getCarrierId()).thenReturn(1);
        Intent mIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        mIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        mContext.sendBroadcast(mIntent);
        processAllMessages();
        Date expirationDate = new Date(mCarrierKeyDM.getExpirationDate());
        assertEquals(dt.format(expirationDate), dateExpected);
    }

    /**
     * Test notifying the carrier config change from listener.
     * Verify that the right mnc/mcc gets stored in the preferences.
     **/
    @Test
    @SmallTest
    public void testCarrierConfigChangedWithUserUnlocked() {
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        int slotId = mPhone.getPhoneId();
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(slotId);
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
        bundle.putString(CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, mURL);

        when(mPhone.getOperatorNumeric()).thenReturn("310260");
        when(mPhone.getCarrierId()).thenReturn(1);
        mCarrierConfigChangeListener.onCarrierConfigChanged(0 /* slotIndex */,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                1, TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();
        assertEquals("310260", mCarrierKeyDM.mMccMncForDownload);
        assertEquals(1, mCarrierKeyDM.mCarrierId);
    }

    @Test
    @SmallTest
    public void testCarrierConfigChangedWithUserLocked() {
        when(mUserManager.isUserUnlocked()).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked()).thenReturn(true);
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        int slotId = mPhone.getPhoneId();
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(slotId);
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
        bundle.putString(CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, mURL);

        when(mPhone.getOperatorNumeric()).thenReturn("310260");
        when(mPhone.getCarrierId()).thenReturn(1);
        mCarrierConfigChangeListener.onCarrierConfigChanged(0 /* slotIndex */,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                1, TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();
        assertEquals("310260", mCarrierKeyDM.mMccMncForDownload);
        assertEquals(1, mCarrierKeyDM.mCarrierId);
    }

    @Test
    @SmallTest
    public void testUserLockedAfterCarrierConfigChanged() {
        // User is locked at beginning
        when(mUserManager.isUserUnlocked()).thenReturn(false);
        when(mKeyguardManager.isDeviceLocked()).thenReturn(true);
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        int slotId = mPhone.getPhoneId();
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(slotId);
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
        bundle.putString(CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, mURL);

        // Carrier config change received
        when(mPhone.getOperatorNumeric()).thenReturn("310260");
        when(mPhone.getCarrierId()).thenReturn(1);
        mCarrierConfigChangeListener.onCarrierConfigChanged(0 /* slotIndex */,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                1, TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();

        // User unlocked event received
        Intent mIntent = new Intent(Intent.ACTION_USER_PRESENT);
        mContext.sendBroadcast(mIntent);
        when(mUserManager.isUserUnlocked()).thenReturn(true);
        when(mKeyguardManager.isDeviceLocked()).thenReturn(false);
        processAllMessages();

        assertEquals("310260", mCarrierKeyDM.mMccMncForDownload);
        assertEquals(1, mCarrierKeyDM.mCarrierId);
    }

    /**
     * Tests notifying carrier config change from listener with an empty key.
     * Verify that the carrier keys are removed if IMSI_KEY_DOWNLOAD_URL_STRING is null.
     */
    @Test
    @SmallTest
    public void testCarrierConfigChangedEmptyKey() {
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        int slotId = mPhone.getPhoneId();
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(slotId);
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
        bundle.putString(CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, null);

        mCarrierConfigChangeListener.onCarrierConfigChanged(0 /* slotIndex */,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                1, TelephonyManager.UNKNOWN_CARRIER_ID);
        processAllMessages();
        assertTrue(TextUtils.isEmpty(mCarrierKeyDM.mMccMncForDownload));

        verify(mPhone).deleteCarrierInfoForImsiEncryption(1, "");
    }

    /**
     * Tests sending the INTENT_KEY_RENEWAL_ALARM_PREFIX intent.
     * Verify that the right mnc/mcc gets stored in the preferences.
     **/
    @Test
    @SmallTest
    public void testAlarmRenewal() {
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        int slotIndex = SubscriptionManager.getSlotIndex(mPhone.getSubId());
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(slotIndex);
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT, 3);
        bundle.putString(CarrierConfigManager.IMSI_KEY_DOWNLOAD_URL_STRING, mURL);

        when(mPhone.getOperatorNumeric()).thenReturn("310260");
        when(mPhone.getCarrierId()).thenReturn(1);
        Intent mIntent = new Intent("com.android.internal.telephony.carrier_key_download_alarm");
        mIntent.putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, slotIndex);
        mContext.sendBroadcast(mIntent);
        processAllMessages();
        assertEquals("310260", mCarrierKeyDM.mMccMncForDownload);
    }

    /**
     * Checks if the JSON in 3GPP spec format is parsed correctly, and that WLAN is the key type.
     **/
    @Test
    @SmallTest
    public void testParseJson3GppFormat() {
        Pair<PublicKey, Long> keyInfo = null;
        try {
            keyInfo = mCarrierKeyDM.getKeyInformation(CERT.getBytes());
        } catch (Exception e) {
            fail(LOG_TAG + "exception creating public key");
        }
        ImsiEncryptionInfo imsiEncryptionInfo = new ImsiEncryptionInfo("310", "270",
                TelephonyManager.KEY_TYPE_WLAN, "key1=value", keyInfo.first,
                new Date(CERT_EXPIRATION), 1);
        String mccMnc = "310270";
        int carrierId = 1;
        mCarrierKeyDM.parseJsonAndPersistKey(mJsonStr3GppSpec, mccMnc, carrierId);
        verify(mPhone).setCarrierInfoForImsiEncryption(
                (ArgumentMatchers.refEq(imsiEncryptionInfo)));
    }

    /**
     * Checks if certificate string cleaning is working correctly
     */
    @Test
    @SmallTest
    public void testCleanCertString() {
        assertEquals(CarrierKeyDownloadManager
                .cleanCertString("Comments before" + CERT + "Comments after"), CERT);
    }
}
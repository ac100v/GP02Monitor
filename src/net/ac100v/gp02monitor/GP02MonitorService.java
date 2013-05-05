/*
 * Copyright (C) 2011-2013 Yusuke Kikuchi <dev@ac100v.net>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ac100v.gp02monitor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

/**
 * @author yusuke
 *
 */
public class GP02MonitorService extends Service {
	static final int NOTIFY_BATTERY_ICON_ID = 1;	// 電池残量(or接続異常)通知アイコン
	static final int NOTIFY_SIGNAL_ICON_ID = 3;		// 信号強度通知アイコン
	static final int BATTERY_LOW_ICON_ID = 2;			// 電池残量低下通知アイコン
	
	public static final String ACTION = "GP02Monitor status updated";
	
	/** ルータから情報取得するドライバ  */
	HuaweiDriver driver = null;
	
	/** ルータから取得された最新情報 */
	RouterStatus routerStatus = null;
	
	class GP02MonitorBinder extends Binder {
		GP02MonitorService getService() {
			return GP02MonitorService.this;
		}
	}
	
	
	NotificationManager nm;
	
	int updateInterval = 5000;
	boolean hideOnRouterDisconnect = false;
	boolean hideOnWanDisconnect = false;
	boolean notifyBatteryLow = false;		// 電池残量低下を通知
	boolean iconDesignBattery = false;	// 通知アイコンに電池残量を表示
	boolean iconDesignSignal = false;	// 通知アイコンにアンテナレベルを表示
	String targetSSID = "";
	
	int batteryWarnLevel = BATTERY_OK;
	static final int BATTERY_OK = 0;
	static final int BATTERY_LOW = 1;
	static final int BATTERY_CRITICAL = 2;
	
	Thread monitorThread;
	boolean running = false;
	boolean paused = true;
	boolean routerConnected = false;
	
	int lastBatteryStatusIcon = -1;
	int lastSignalStatusIcon = -1;
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
				paused = true;
				// 画面ONで古い情報が出ないようにアイコン表示を消す
				hideNotification();
			} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				paused = false;
				// スリープ中の監視スレッドを起こす
				monitorThread.interrupt();
			} else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				// ネットワーク状態が変わったのでルータ接続状態を更新する
				updateRouterConnectStatus();
			} else if (intent.getAction().equals(ACTION)) {
				updateStatusIcon();
			}
		}
	};
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		
		getApplicationContext().registerReceiver(receiver,
				new IntentFilter(Intent.ACTION_SCREEN_OFF));
		getApplicationContext().registerReceiver(receiver,
				new IntentFilter(Intent.ACTION_SCREEN_ON));
		getApplicationContext().registerReceiver(receiver,
				new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		registerReceiver(receiver, new IntentFilter(ACTION));

		
		// 監視スレッド
		monitorThread = new Thread() {
			@Override
			public synchronized void run() {
				while (running) {
					try {
						if (paused) {
							wait();
						} else {
				    		routerStatus = driver.getRouterStatus();
				    		sendBroadcast(new Intent(ACTION));
				    		sleep(updateInterval);
						}
						
						if (!routerConnected) {
							// ルータ非接続のときは監視を一時停止
							wait();
						}
					} catch (InterruptedException e) {
					}
				}
			}
		};
    	reloadPreferences();
		running = true;
		monitorThread.start();
	}

	@Override
	public void onDestroy() {
		NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		nm.cancelAll();
		nm.cancel(NOTIFY_BATTERY_ICON_ID);
		nm.cancel(NOTIFY_SIGNAL_ICON_ID);
		lastBatteryStatusIcon = -1;
		lastSignalStatusIcon = -1;
		unregisterReceiver(receiver);
		running = false;
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
    	reloadPreferences();
		paused = !isScreenOn();
		hideNotification();
		updateRouterConnectStatus();
		// スリープ中の監視スレッドを起こす
		monitorThread.interrupt();
	}

	private void reloadPreferences() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    	updateInterval = Integer.valueOf(preferences.getString("key_update_interval", "5000"));
    	String s = preferences.getString("key_hide_icon_condition", "always_connect");
    	if (s.equals("wan_disconnect")) {
    		hideOnRouterDisconnect = true;
    		hideOnWanDisconnect = true;
    	} else if (s.equals("router_disconnect")) {
    		hideOnRouterDisconnect = true;
    		hideOnWanDisconnect = false;
    	} else {
    		hideOnRouterDisconnect = false;
    		hideOnWanDisconnect = false;
    	}
    	notifyBatteryLow = preferences.getBoolean("key_notify_battery", true);
    	targetSSID = preferences.getString("key_target_ssid", "");
    	
    	String iconDesign = preferences.getString("key_icon_design", "battery");
    	if (iconDesign.equals("battery")) {
    		iconDesignBattery = true;
    		iconDesignSignal = false;
    	} else if (iconDesign.equals("signal_strength")) {
    		iconDesignBattery = false;
    		iconDesignSignal = true;
    	} else {
    		iconDesignBattery = true;
    		iconDesignSignal = true;
    	}
    	
    	// ルータ別のドライバをロード
    	String routerModel = preferences.getString("key_router_model", "GP02");
    	if (routerModel.equals("GL04P")) {
    		driver = new GL04PDriver();
    	} else {
    		driver = new GP02Driver();
    	}
    	
    	// ルータのアドレスを設定
    	driver.setHostAddress(preferences.getString("key_router_ip_addr", ""));
	}

	/* 画面ONかどうかを取得する
	 * PowerManager.isScreenOn()はAndroid 2.1からのサポート。
	 * isScreenOn()非サポートのバージョンでも動作するようにリフレクションを使っている。
	 * 非サポートのときは、画面ONとして取り扱う。
	 */
	private boolean isScreenOn() {
    	try {
			PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
			java.lang.reflect.Method m = PowerManager.class.getMethod("isScreenOn");
			return (Boolean)m.invoke(pm);
		} catch (Exception e) {
    		return true;
    	}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return new GP02MonitorBinder();
	}
	
	@Override
	public void onRebind(Intent intent) {
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return true;
	}
	
	private void hideNotification() {
		// 画面ONで古い情報が出ないようにアイコン表示を消す
		nm.cancel(NOTIFY_BATTERY_ICON_ID);
		nm.cancel(NOTIFY_SIGNAL_ICON_ID);
		lastBatteryStatusIcon = -1;
		lastSignalStatusIcon = -1;
	}

	/**
	 * ルータ接続状態(routerConnected)を更新する
	 * - ネットワーク接続有無の確認
	 * - SSID確認 (指定されている場合のみ)
	 */
	private void updateRouterConnectStatus() {
		boolean oldStatus = routerConnected;
		WifiManager wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
		if (targetSSID.equals("EmulatorDebugging")) {
			// エミュレータでデバッグするとき、無線接続の有無に関わらずtrueにする
			routerConnected = true;
		} else if (wifiManager == null || wifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
			routerConnected = false;
		} else {
			if (targetSSID.equals("")) {
				routerConnected = true;
			} else {
		    	WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		    	String ssid = wifiInfo.getSSID();
		    	if (ssid == null) {
		    		routerConnected = false;
		    	} else {
		    		routerConnected = ssid.equals(targetSSID);
		    	}
			}
		}
		
		// false→trueに変化したときは監視スレッドのスリープを解除する
		if (!oldStatus && routerConnected) {
			// スリープ中の監視スレッドを起こす
			monitorThread.interrupt();
		}
	}
	
	int getStatusIconSignalStrength(boolean wanConnected, int networkType, int signalStrength)
	{
		final int iconArray[][] = {
				{
					R.drawable.ic_stat_off_sig_error,
					R.drawable.ic_stat_off_sig_0,
					R.drawable.ic_stat_off_sig_1,
					R.drawable.ic_stat_off_sig_2,
					R.drawable.ic_stat_off_sig_3,
				},
				{
					R.drawable.ic_stat_on_3g_sig_0,
					R.drawable.ic_stat_on_3g_sig_0,
					R.drawable.ic_stat_on_3g_sig_1,
					R.drawable.ic_stat_on_3g_sig_2,
					R.drawable.ic_stat_on_3g_sig_3,
				},
				{
					R.drawable.ic_stat_on_lte_sig_0,
					R.drawable.ic_stat_on_lte_sig_0,
					R.drawable.ic_stat_on_lte_sig_1,
					R.drawable.ic_stat_on_lte_sig_2,
					R.drawable.ic_stat_on_lte_sig_3,
				},
		};
		int i, k;
		
		if (!wanConnected) {
			i = 0;
		} else {
			if (networkType == RouterStatus.NETWORK_LTE) {
				i = 2;
			} else {
				i = 1;
			}
		}
		
		if (signalStrength < 0) {
			k = 0;
		} else if (signalStrength > 4) {
			k = 4;
		} else {
			k = signalStrength;
		}
		return iconArray[i][k];
	}
	
	int getStatusIconBattery(boolean wanConnected, int networkType, int batteryLevel, boolean charging)
	{
		final int iconArray[][][] = {
					{
						{
							R.drawable.ic_stat_off_batt_0,
							R.drawable.ic_stat_off_batt_1,
							R.drawable.ic_stat_off_batt_2,
							R.drawable.ic_stat_off_batt_3,
							R.drawable.ic_stat_off_batt_4,
						},
						{
							R.drawable.ic_stat_off_batt_0_charge,
							R.drawable.ic_stat_off_batt_1_charge,
							R.drawable.ic_stat_off_batt_2_charge,
							R.drawable.ic_stat_off_batt_3_charge,
							R.drawable.ic_stat_off_batt_4_charge,
						},
					},
					{
						{
							R.drawable.ic_stat_on_3g_batt_0,
							R.drawable.ic_stat_on_3g_batt_1,
							R.drawable.ic_stat_on_3g_batt_2,
							R.drawable.ic_stat_on_3g_batt_3,
							R.drawable.ic_stat_on_3g_batt_4,
						},
						{
							R.drawable.ic_stat_on_3g_batt_0_charge,
							R.drawable.ic_stat_on_3g_batt_1_charge,
							R.drawable.ic_stat_on_3g_batt_2_charge,
							R.drawable.ic_stat_on_3g_batt_3_charge,
							R.drawable.ic_stat_on_3g_batt_4_charge,
						},
					},
					{
						{
							R.drawable.ic_stat_on_lte_batt_0,
							R.drawable.ic_stat_on_lte_batt_1,
							R.drawable.ic_stat_on_lte_batt_2,
							R.drawable.ic_stat_on_lte_batt_3,
							R.drawable.ic_stat_on_lte_batt_4,
						},
						{
							R.drawable.ic_stat_on_lte_batt_0_charge,
							R.drawable.ic_stat_on_lte_batt_1_charge,
							R.drawable.ic_stat_on_lte_batt_2_charge,
							R.drawable.ic_stat_on_lte_batt_3_charge,
							R.drawable.ic_stat_on_lte_batt_4_charge,
						},
					},
		};
		int i, k;
		
		if (!wanConnected) {
			i = 0;
		} else {
			if (networkType == RouterStatus.NETWORK_LTE) {
				i = 2;
			} else {
				i = 1;
			}
		}
		
		if (batteryLevel < 0) {
			k = 0;
		} else if (batteryLevel > 4) {
			k = 4;
		} else {
			k = batteryLevel;
		}
		return iconArray[i][charging ? 1 : 0][k];
	}
	
	private void updateNotifyIcon(int notifyId, int iconId, String statusString) {
		if (iconId == -1) {
			nm.cancel(notifyId);
		} else {
			Intent intent = new Intent(this, GP02StatusActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
			Notification notification;
			notification = new Notification(iconId, null, System.currentTimeMillis());
			
			pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
			notification.setLatestEventInfo(getApplicationContext(),
					getApplicationContext().getString(R.string.app_name),
	        		statusString,
	                pendingIntent
	                );
	        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
	        // 音を鳴らす
	        // notification.defaults |= Notification.DEFAULT_SOUND;
			nm.notify(notifyId, notification);
		}
	}
	
	public void updateStatusIcon() {
		int iconBattery;
		int iconSignal;
		String statusString = "";
		boolean wanConnected = false;
		boolean routerConnected;
		RouterStatus sts;
		
		if (!running) return;
		
		// 処理中にrouterStatusが更新されても影響ないように、ここでラッチする
		sts = routerStatus;
		if (sts == null) {
			// GP02未検出
			routerConnected = false;
			iconSignal = R.drawable.ic_stat_off_sig_miss;
			iconBattery = -1;
			statusString += String.format(
					getApplicationContext().getString(R.string.router_not_found),
					driver.getRouterName());
		} else {
			routerConnected = true;
			int signalLevel = sts.signalLevel;
			
			wanConnected = (sts.wanStatus == RouterStatus.WAN_CONNECTED);
			
			// 電池残量チェック
			if (updateBatteryWarnLevel(sts.batteryLevel, sts.charging)) {
				notifyBatteryWarnLevel(batteryWarnLevel);
			}
			
			// 圏外表示
			if (signalLevel == 0) {
				statusString += getApplicationContext().getString(R.string.no_service);
			}
			// 電池残量のテキスト追加
			statusString += String.format("%s %d/4 ", getApplicationContext().getString(R.string.battery), sts.batteryLevel);
			if (sts.charging) {
				statusString += getApplicationContext().getString(R.string.charging);
			}
			
			// アイコン
			if (iconDesignBattery) {
				iconBattery = getStatusIconBattery(wanConnected, sts.networkType, sts.batteryLevel, sts.charging);
			} else {
				iconBattery = -1;
			}
			if (iconDesignSignal) {
				iconSignal = getStatusIconSignalStrength(wanConnected, sts.networkType, signalLevel);
			} else {
				iconSignal = -1;
			}
		}
		
		if ((hideOnWanDisconnect && !wanConnected) || (hideOnRouterDisconnect && !routerConnected)) {
			nm.cancel(NOTIFY_BATTERY_ICON_ID);
			nm.cancel(NOTIFY_SIGNAL_ICON_ID);
			lastBatteryStatusIcon = -1;
			lastSignalStatusIcon = -1;
		} else {
			if ((iconBattery != lastBatteryStatusIcon) || (iconSignal != lastSignalStatusIcon)) {
				lastBatteryStatusIcon = iconBattery;
				lastSignalStatusIcon = iconSignal;
				
				updateNotifyIcon(NOTIFY_BATTERY_ICON_ID, iconBattery, statusString);
				updateNotifyIcon(NOTIFY_SIGNAL_ICON_ID, iconSignal, statusString);
			}
		}
	}
	
	private boolean updateBatteryWarnLevel(int batteryLevel, boolean charging) {
		if (!notifyBatteryLow || charging) {
			if (batteryWarnLevel != BATTERY_OK) {
				batteryWarnLevel = BATTERY_OK;
				return true;
			} else {
				return false;
			}
		} else if (batteryWarnLevel < BATTERY_CRITICAL && batteryLevel == 0) {
			batteryWarnLevel = BATTERY_CRITICAL;
			return true;
		} else if (batteryWarnLevel < BATTERY_LOW && batteryLevel == 1) {
			batteryWarnLevel = BATTERY_LOW;
			return true;
		} else if (batteryWarnLevel != BATTERY_OK && batteryLevel >= 2) {
			batteryWarnLevel = BATTERY_OK;
			return true;
		} else {
			return false;
		}
	}
	
	void notifyBatteryWarnLevel(int level) {
		if (level == BATTERY_OK) {
			nm.cancel(BATTERY_LOW_ICON_ID);
		} else {
			int iconId;
			String message;
			
			if (level == BATTERY_LOW) {
				message = String.format(
						getApplicationContext().getString(R.string.battery_low),
						driver.getRouterName());
				iconId = R.drawable.battery_low;
			} else {
				message = String.format(
						getApplicationContext().getString(R.string.battery_critical),
						driver.getRouterName());
				iconId = R.drawable.battery_critical;
			}
			
			Notification notification = new Notification(iconId, message, System.currentTimeMillis());
			Intent intent = new Intent(this, GP02StatusActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
			notification.setLatestEventInfo(getApplicationContext(), message, null, pendingIntent);
	        //notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
			notification.flags |= Notification.FLAG_AUTO_CANCEL;
			nm.notify(BATTERY_LOW_ICON_ID, notification);
		}
	}
}

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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class GP02StatusActivity extends Activity {
	
	ImageView antennaImageView;
	ImageView wanImageView;
	ImageView batteryImageView;
	TextView plmnTextView;
	TextView connectModeTextView;
	TextView wifiUserTextView;
	TextView currentDownloadTextView;
	TextView totalDownloadTextView;
	TextView currentDownloadRateTextView;
	TextView currentUploadTextView;
	TextView totalUploadTextView;
	TextView currentUploadRateTextView;
	TextView currentConnectTimeTextView;
	TextView totalConnectTimeTextView;
	
	Button connectButton;
	Button disconnectButton;
	Button webSetupButton;
	
	private GP02MonitorService gp02MonitorService;
	GP02MonitorReceiver receiver = new GP02MonitorReceiver();
	
	final int MENU_QUIT = 0;
	final int MENU_PREFERENCES = 1;
	final int MENU_ABOUT = 2;
	
	// connection
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			gp02MonitorService = ((GP02MonitorService.GP02MonitorBinder)service).getService();
			updateScreen();
		}
		@Override
		public void onServiceDisconnected(ComponentName className) {
			gp02MonitorService = null;
		}
	};
	
	// receiver
	private class GP02MonitorReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (gp02MonitorService != null) {
				updateScreen();
			}
		}
	};
	
	private void updateScreen() {
		RouterStatus sts = gp02MonitorService.routerStatus;
		
		if (sts == null) {
			updateScreenOffLine();
			return;
		}
		
		updateAntennaImage(sts);
		updateWanImage(sts);
		updateBatteryImage(sts);
		
		plmnTextView.setText(sts.plmnName);
		
		if (sts.connectMode == RouterStatus.CONNECT_MODE_AUTO) {
			connectModeTextView.setText("Auto");
		} else {
			connectModeTextView.setText("Manual");
		}
		
		wifiUserTextView.setText(Integer.toString(sts.currentWifiUser));
		
		currentDownloadTextView.setText(makeBinPrefixedString(sts.currentDownload, "B"));
		totalDownloadTextView.setText(makeBinPrefixedString(sts.totalDownload, "B"));
		currentDownloadRateTextView.setText(makeBinPrefixedString(sts.currentDownloadRate * 8, "bps"));
		currentUploadTextView.setText(makeBinPrefixedString(sts.currentUpload, "B"));
		totalUploadTextView.setText(makeBinPrefixedString(sts.totalUpload, "B"));
		currentUploadRateTextView.setText(makeBinPrefixedString(sts.currentUploadRate * 8, "bps"));
		currentConnectTimeTextView.setText(intToTime(sts.currentConnectTime));
		totalConnectTimeTextView.setText(intToTime(sts.totalConnectTime));
		
		webSetupButton.setEnabled(true);
		
		//t.setText(String.format("%d", sts.signalStrength));
		// 最新状況を取得して表示
	}

	private void updateAntennaImage(RouterStatus sts) {
		final int imgId[] = {
			R.drawable.antenna_0,
			R.drawable.antenna_1,
			R.drawable.antenna_2,
			R.drawable.antenna_3,
			R.drawable.antenna_4
		};
		int index = sts.signalLevel;
		if (index < 0 || index > 4) {
			index = 0;
		}
		// WAN接続中なのに圏外表示になるのを抑制する
		if (sts.wanStatus == RouterStatus.WAN_CONNECTED && index == 0) {
			index = 1;
		}
		antennaImageView.setImageResource(imgId[index]);
	}

	private void updateWanImage(RouterStatus sts) {
		if (sts.wanStatus == RouterStatus.WAN_CONNECTED) {
			if (sts.networkType == RouterStatus.NETWORK_LTE) {
				wanImageView.setImageResource(R.drawable.wan_lte);
			} else {
				wanImageView.setImageResource(R.drawable.wan_3g);
			}
			connectButton.setEnabled(false);
			disconnectButton.setEnabled(true);
		} else if (sts.wanStatus == RouterStatus.WAN_CONNECTING) {
			if (sts.networkType == RouterStatus.NETWORK_LTE) {
				wanImageView.setImageResource(R.drawable.wan_lte_connecting);
			} else {
				wanImageView.setImageResource(R.drawable.wan_3g_connecting);
			}
			((AnimationDrawable)wanImageView.getDrawable()).start();
			connectButton.setEnabled(false);
			disconnectButton.setEnabled(false);
		} else if (sts.wanStatus == RouterStatus.WAN_DISCONNECTING) {
			wanImageView.setImageResource(R.drawable.wan_off);
			connectButton.setEnabled(false);
			disconnectButton.setEnabled(false);
		} else {
			// RouterStatus.WAN_DISCONNECT?
			wanImageView.setImageResource(R.drawable.wan_off);
			connectButton.setEnabled(true);
			disconnectButton.setEnabled(false);
		}
	}
	
	private void updateBatteryImage(RouterStatus sts) {
		final int imgId[][] = {
			new int[] {
				R.drawable.battery_0,
				R.drawable.battery_1,
				R.drawable.battery_2,
				R.drawable.battery_3,
				R.drawable.battery_4
			},
			new int[] {
				R.drawable.battery_charge_0,
				R.drawable.battery_charge_1,
				R.drawable.battery_charge_2,
				R.drawable.battery_charge_3,
				R.drawable.battery_charge_4
			}
		};
		int index = sts.batteryLevel;
		if (index < 0 || index > 4) {
			index = 0;
		}
		batteryImageView.setImageResource(imgId[sts.charging ? 1 : 0][index]);
		if (sts.charging) {
			((AnimationDrawable)batteryImageView.getDrawable()).start();
		}
	}
	
	public void updateScreenOffLine() {
		antennaImageView.setImageResource(R.drawable.wan_off);
		wanImageView.setImageResource(R.drawable.wan_off);
		batteryImageView.setImageResource(R.drawable.wan_off);
		plmnTextView.setText("Not found");
		connectModeTextView.setText("");
		wifiUserTextView.setText("");
		currentDownloadTextView.setText("");
		totalDownloadTextView.setText("");
		currentDownloadRateTextView.setText("");
		currentUploadTextView.setText("");
		totalUploadTextView.setText("");
		currentUploadRateTextView.setText("");
		currentConnectTimeTextView.setText("");
		totalConnectTimeTextView.setText("");
		
		connectButton.setEnabled(false);
		disconnectButton.setEnabled(false);
		webSetupButton.setEnabled(false);
	}
	
	static String makeBinPrefixedString(double d, String suffix) {
		final String prefix[] = {"", "Ki", "Mi", "Gi", "Ti", "Pi"};
		int i = 0;
		while (d > 999.0 && i < 5) {
			d /= 1024.0;
			i++;
		}
		
		if (d == 0.0) {
			return "";
		} else if (i == 0 || d >= 100) {
			return String.format("%d%s%s", (int)(d+0.5), prefix[i], suffix);
		} else {
			return String.format("%.4s%s%s", Double.toString(d), prefix[i], suffix);
		}
	}
	
	static String intToTime(int n) {
		return String.format("%d:%02d", n / 3600, (n / 60) % 60);
	}
	
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	MenuItem quitMenu = menu.add(Menu.NONE, MENU_QUIT, Menu.NONE, R.string.menu_quit);
    	MenuItem prefMenu = menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.menu_pref);
    	MenuItem aboutMenu = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.menu_about);
    	quitMenu.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    	prefMenu.setIcon(android.R.drawable.ic_menu_preferences);
    	aboutMenu.setIcon(android.R.drawable.ic_menu_info_details);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_QUIT:
			// サービスを停止してから自身を終了させる
	    	stopService(new Intent(this, GP02MonitorService.class));
			finish();
			break;
		case MENU_PREFERENCES:
	    	startActivity(new Intent(this, GP02PreferenceActivity.class));
			break;
		case MENU_ABOUT:
			aboutDialog();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status_screen);
        
		// 画面更新用のViewを取得する
        antennaImageView = (ImageView)findViewById(R.id.antennaImageView);
        wanImageView = (ImageView)findViewById(R.id.wanImageView);
		batteryImageView = (ImageView)findViewById(R.id.batteryImageView);
		plmnTextView = (TextView)findViewById(R.id.plmnTextView);
		connectModeTextView = (TextView)findViewById(R.id.connectModeTextView);
		wifiUserTextView = (TextView)findViewById(R.id.wifiUserTextView);
		currentDownloadTextView = (TextView)findViewById(R.id.currentDownloadTextView);
		totalDownloadTextView = (TextView)findViewById(R.id.totalDownloadTextView);
		currentDownloadRateTextView = (TextView)findViewById(R.id.currentDownloadRateTextView);
		currentUploadTextView = (TextView)findViewById(R.id.currentUploadTextView);
		totalUploadTextView = (TextView)findViewById(R.id.totalUploadTextView);
		currentUploadRateTextView = (TextView)findViewById(R.id.currentUploadRateTextView);
		currentConnectTimeTextView = (TextView)findViewById(R.id.currentConnectTimeTextView);
		totalConnectTimeTextView = (TextView)findViewById(R.id.totalConnectTimeTextView);
		
		connectButton = (Button)findViewById(R.id.connectButton);
		disconnectButton = (Button)findViewById(R.id.disconnectButton);
		webSetupButton = (Button)findViewById(R.id.webSetupButton);
        
		// Serviceを起動する
    	Intent serviceIntent = new Intent(GP02StatusActivity.this, GP02MonitorService.class);
		startService(serviceIntent);
		bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
		IntentFilter filter = new IntentFilter(GP02MonitorService.ACTION);
		registerReceiver(receiver, filter);
    }
    
    @Override
	protected void onResume() {
		super.onResume();
        // バージョンアップ後初回起動時にダイアログを表示
    	SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!p.getString("last_version_name", "").equals(getString(R.string.version_name))) {
        	aboutDialog();
        	p.edit().putString("last_version_name", getString(R.string.version_name)).commit();
        }
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (serviceConnection != null) {
			unbindService(serviceConnection);
		}
		if (receiver != null) {
			unregisterReceiver(receiver);
		}
	}
    
	public void onClickConnectButton(View view) {
		gp02MonitorService.driver.connect();
		connectButton.setEnabled(false);
    }
    
	public void onClickDisconnectButton(View view) {
		gp02MonitorService.driver.disconnect();
		disconnectButton.setEnabled(false);
    }
    
    public void onClickWebSetupButton(View view) {
    	try {
    		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(gp02MonitorService.driver.getWebSetupUri()));
    		startActivity(intent);
    	} catch (Exception e) {
    	}
    }
    
    void aboutDialog() {
    	showDialog(0);
    }

	@Override
	protected Dialog onCreateDialog(int id) {
		// Aboutダイアログ
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		return builder
		.setIcon(R.drawable.ic_launcher)
		.setTitle(getApplicationContext().getString(R.string.app_name_version))
		.setMessage(getApplicationContext().getString(R.string.license))
		.setPositiveButton("OK", null).create();
	}
    
}

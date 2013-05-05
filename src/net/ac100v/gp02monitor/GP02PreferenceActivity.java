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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class GP02PreferenceActivity extends PreferenceActivity {
	
    EditTextPreference prefSsid;
    CheckBoxPreference prefRun;

    SharedPreferences.OnSharedPreferenceChangeListener listener =
    		new SharedPreferences.OnSharedPreferenceChangeListener() {
				
				public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
						String key) {
					if (key.equals("key_target_ssid")) {
						updateSsidText();
					}
					if (key.equals("key_update_interval")) {
						updateUpdateInterval();
					}
					if (key.equals("key_hide_icon_condition")) {
						updateHideIconCondition();
					}
					if (key.equals("key_icon_design")) {
						updateIconDesign();
					}
					if (key.equals("key_router_model")) {
						updateRouterModel();
					}
					if (key.equals("key_temp_router_ip_addr")) {
						updateRouterIpAddr();
					}
					
					// 設定を反映させる
					enableService();
				}
			};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
        
        prefSsid = (EditTextPreference)findPreference("key_target_ssid");
        prefRun = (CheckBoxPreference)findPreference("key_run_service");
        
        Preference prefMonSsid = (Preference)findPreference("key_set_target_ssid");
        prefMonSsid.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				String ssid = getCurrentSsid();
				if (ssid != null) {
					prefSsid.setText(ssid);
				}
				return true;
			}
		});
        
		enableService();
        updateSsidText();
        updateUpdateInterval();
        updateHideIconCondition();
        updateIconDesign();
        updateRouterModel();
        updateRouterIpAddr();
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().
		unregisterOnSharedPreferenceChangeListener(listener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().
		registerOnSharedPreferenceChangeListener(listener);
	}

	void enableService() {
    	Intent serviceIntent = new Intent(GP02PreferenceActivity.this, GP02MonitorService.class);
    	startService(serviceIntent);
    }
    
    void updateSsidText() {
        EditTextPreference prefSsid = (EditTextPreference)findPreference("key_target_ssid");
        String ssid = prefSsid.getText();
        if (ssid.equals("")) {
        	prefSsid.setSummary(getApplicationContext().getString(R.string.ssid_not_specified));
        } else {
            prefSsid.setSummary(ssid);
        }
    }
    
    String getCurrentSsid() {
		WifiManager wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
    	if (wifiManager == null) {
    		return null;
    	}
    	WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    	return wifiInfo.getSSID();
    }
    
    void updateUpdateInterval() {
		ListPreference pref;
		pref = (ListPreference)findPreference("key_update_interval");
		if (pref != null) {
			pref.setSummary(pref.getEntry());
		}
    }
    
    void updateHideIconCondition() {
		ListPreference pref;
		pref = (ListPreference)findPreference("key_hide_icon_condition");
		pref.setSummary(pref.getEntry());
    }
    
    void updateIconDesign() {
		ListPreference pref;
		pref = (ListPreference)findPreference("key_icon_design");
		pref.setSummary(pref.getEntry());
    }

    void updateRouterModel() {
		ListPreference pref;
		pref = (ListPreference)findPreference("key_router_model");
		pref.setSummary(pref.getEntry());
    }

	private void updateRouterIpAddr() {
		// IPアドレスを表す正規表現 (d.d.d.d または 空文字列)
		String regex = "^$|^(((\\d)|([1-9]\\d)|(1\\d{1,2})|(2[0-4]\\d)|(25[0-5]))\\.){3}"
				     + "((\\d)|([1-9]\\d)|(1\\d{1,2})|(2[0-4]\\d)|(25[0-5]))$";
		EditTextPreference pref = (EditTextPreference)findPreference("key_temp_router_ip_addr");
    	SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String temp_ip_addr = pref.getText();
		if (temp_ip_addr != null) {
			Matcher m = Pattern.compile(regex).matcher(temp_ip_addr);
			if (m.find()) {
				// 入力されたIPアドレスが正当ならば保存する
				p.edit().putString("key_router_ip_addr", temp_ip_addr).commit();
			} else {
				// 入力不正のときは前回値を書き戻す
				pref.setText(p.getString("key_router_ip_addr", ""));
			}
		}
		// summaryを更新
		String summary = p.getString("key_router_ip_addr", "");
		if (summary.equals("")) {
			summary = "(pocketwifi.home)";
		}
		pref.setSummary(summary);
	}
}
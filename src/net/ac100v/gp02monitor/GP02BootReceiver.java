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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class GP02BootReceiver extends BroadcastReceiver {
	
	// Androidブート時に実行される
	@Override
	public void onReceive(Context context, Intent arg1) {
    	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    	if (preferences.getBoolean("key_run_on_boot", false)) {
    		preferences.edit().putBoolean("key_run_service", true).commit();
    		Intent intent = new Intent(context, GP02MonitorService.class);
    		context.startService(intent);
    	}
	}

}

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

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;

import android.util.Xml;

abstract class HuaweiDriver {
	// currentNetworkType
	static final int NETWORKTYPE_NO_SERVICE = 0;
	static final int NETWORKTYPE_GSM        = 1;
	static final int NETWORKTYPE_GPRS       = 2;
	static final int NETWORKTYPE_EDGE       = 3;
	static final int NETWORKTYPE_WCDMA      = 4;
	static final int NETWORKTYPE_HSDPA      = 5;
	static final int NETWORKTYPE_HSUPA      = 6;
	static final int NETWORKTYPE_HSPA       = 7;
	// connectionStatus
	static final int PPP_CONNECTING    = 900;
	static final int PPP_CONNECTED     = 901;
	static final int PPP_DISCONNECTED  = 902;
	static final int PPP_DISCONNECTING =   3;
	
	// connectMode
	static final int PPP_AUTO      = 0;
	static final int PPP_MANUAL    = 1;
	static final int PPP_ON_DEMAND = 2; // auto?
	
	HashMap<String, String> mapStatus;
	HashMap<String, String> mapConnection;
	HashMap<String, String> mapTraffic;
	HashMap<String, String> mapPlmn;
	
	String hostAddress;
	
	RouterStatus sts;
	
	public abstract String getRouterName();
	
	public void setHostAddress(String address) {
		if (address.equals("")) {
			hostAddress = "pocketwifi.home";
		} else {
			hostAddress = address;
		}
	}
	
	public String getWebSetupUri() {
		return "http://" + hostAddress + "/";
	}
	
	int getIntFromMap(HashMap<String, String> map, String key, int defaultValue) {
		try {
			return Integer.parseInt(map.get(key));
		} catch (Exception e) {
			return defaultValue;
		}
	}
	
	protected RouterStatus interpretRouterStatus() {
		RouterStatus sts = new RouterStatus();
		try {
			int signalLevel;
			if (mapStatus.get("SignalLevel") == null) {
				// SignalLevelを返さないルータに対する救済処置
				// SignalStrengthをSignalLevelに変換する。変換式はGP02のもの。他機種は違うかも
				int signalStrength = getIntFromMap(mapStatus, "SignalStrength", 0);
				signalLevel = signalStrength / 20;
			} else {
				signalLevel = getIntFromMap(mapStatus, "SignalLevel", 0);
			}
			if (signalLevel < 0) {
				sts.signalLevel = 0;
			} else if (signalLevel > 4) {
				sts.signalLevel = 4;
			} else {
				sts.signalLevel = signalLevel;
			}
			
			int batteryLevel = getIntFromMap(mapStatus, "BatteryLevel", 0);
			if (batteryLevel < 0) {
				sts.batteryLevel = 0;
			} else if (batteryLevel > 4) {
				sts.batteryLevel = 4;
			} else {
				sts.batteryLevel = batteryLevel;
			}
			
			int batteryStatus = getIntFromMap(mapStatus, "BatteryStatus", 0);
			sts.charging = (batteryStatus != 0);
			
			int connectionStatus = getIntFromMap(mapStatus, "ConnectionStatus", PPP_DISCONNECTED);
			switch (connectionStatus) {
			case PPP_CONNECTED:
				sts.wanStatus = RouterStatus.WAN_CONNECTED;
				break;
			case PPP_CONNECTING:
				sts.wanStatus = RouterStatus.WAN_CONNECTING;
				break;
			case PPP_DISCONNECTED:
			default:
				sts.wanStatus = RouterStatus.WAN_DISCONNECTED;
				break;
			}
			sts.currentWifiUser = getIntFromMap(mapStatus, "CurrentWifiUser", 0);
			int connectMode = getIntFromMap(mapConnection, "ConnectMode", PPP_ON_DEMAND);
			if (connectMode == PPP_ON_DEMAND) {
				sts.connectMode = RouterStatus.CONNECT_MODE_AUTO;
			} else {
				sts.connectMode = RouterStatus.CONNECT_MODE_MANUAL;
			}
			
			sts.currentConnectTime  = getIntFromMap(mapTraffic, "CurrentConnectTime", 0);
			sts.currentUpload       = getIntFromMap(mapTraffic, "CurrentUpload", 0);
			sts.currentDownload     = getIntFromMap(mapTraffic, "CurrentDownload", 0);
			sts.currentUploadRate   = getIntFromMap(mapTraffic, "CurrentUploadRate", 0);
			sts.currentDownloadRate = getIntFromMap(mapTraffic, "CurrentDownloadRate", 0);
			sts.totalConnectTime    = getIntFromMap(mapTraffic, "TotalConnectTime", 0);
			sts.totalUpload         = getIntFromMap(mapTraffic, "TotalUpload", 0);
			sts.totalDownload       = getIntFromMap(mapTraffic, "TotalDownload", 0);
			
			sts.plmnName = mapPlmn.get("ShortName");
			if (sts.plmnName == null) {
				sts.plmnName = "unknown";
			}
			
			return sts;
		} catch (Exception e) {
			return null;
		}
	}

	
	public RouterStatus getRouterStatus() {
		RouterStatus routerStatus;
		if (!update()) {
			routerStatus = null;
		} else {
			routerStatus = interpretRouterStatus();
		}
		return routerStatus;
	}
	
	public void connect() {
    	sendRequest("dialup/dial", "<Action>1</Action>");
	}
	
	public void disconnect() {
    	sendRequest("dialup/dial", "<Action>0</Action>");
	}
	
	boolean update() {
		if ((mapStatus = getResponse("monitoring/status")) == null) {
			return false;
		}
		if ((mapConnection = getResponse("dialup/connection")) == null) {
			return false;
		}
		if ((mapTraffic = getResponse("monitoring/traffic-statistics")) == null) {
			return false;
		}
		if ((mapPlmn = getResponse("net/current-plmn")) == null) {
			return false;
		}
		
		return true;
	}
	
	protected HashMap<String, String> getResponse(String api) {
		HashMap<String, String> map = new HashMap<String, String>();
		try {
			XmlPullParser parser = sendRequest(api, null);
	    	int eventType = parser.next();
	    	while (eventType != XmlPullParser.END_DOCUMENT) {
	    		if (eventType != XmlPullParser.START_TAG) {
	    			eventType = parser.next();
	    		} else {
	    			String tag = parser.getName();
	    			String text;
	    			eventType = parser.next();
	    			if (eventType == XmlPullParser.TEXT) {
	    				text = parser.getText();
	    				eventType = parser.next();
	    				map.put(tag,  text);
	    			}
	    		}
			}
		} catch (Exception e) {
			return null;
		}
		return map;
	}
	
    protected XmlPullParser sendRequest(String api, String requestXml) {
    	try {
    		// XMLをpostする
    		URL url = new URL("http://" + hostAddress + "/api/" + api);
    		URLConnection connection = url.openConnection();
    		if (requestXml != null) {
    			connection.setDoOutput(true);
    			OutputStream os = connection.getOutputStream();
    			PrintStream ps = new PrintStream(os);
    			ps.print("<?xml version=\"1.0\" encoding=\"utf-8\" ?><request>");
    			ps.print(requestXml);
    			ps.print("</request>");
    			ps.close();
    		}
    		
    		// 応答を受け取る
    		// うまくいくと <?xml version="1.0" encoding="UTF-8"?><response>OK</response> が返ってくる
	    	XmlPullParser parser = Xml.newPullParser();
	    	parser.setInput(connection.getInputStream(), "UTF-8");
	    	return parser;
    	} catch (Exception e) {
        	return null;
    	}
    }
}

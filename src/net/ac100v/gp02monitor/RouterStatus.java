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

public class RouterStatus {
	static final int INIT = 0;
	
	public static final int WAN_DISCONNECTED = 0;
	public static final int WAN_DISCONNECTING = 1;
	public static final int WAN_CONNECTING = 2;
	public static final int WAN_CONNECTED = 3;
	public static final int WAN_UNKNOWN = 99;
	
	public static final int CONNECT_MODE_MANUAL = 0;
	public static final int CONNECT_MODE_AUTO = 1;
	
	public static final int NETWORK_UNKNOWN = 0;
	public static final int NETWORK_3G = 1;
	public static final int NETWORK_LTE = 2;
	
	int signalLevel; /** �M�����x (0:���O 1�`4:�A���e�i�{��) */
	int batteryLevel; /** �o�b�e���[�c�� (0�`4) */
	boolean charging; /** �[�d��� */
	int wanStatus; /** WAN�ڑ���� */
	int networkType; /** �l�b�g���[�N���(3G/LTE) */
	
	String plmnName;
	
	// GP02�ŗL�H
	int currentWifiUser;
	int connectMode;
	
	int currentConnectTime;
	long currentUpload;
	long currentDownload;
	int currentUploadRate;
	int currentDownloadRate;
	int totalConnectTime;
	long totalUpload;
	long totalDownload;
}
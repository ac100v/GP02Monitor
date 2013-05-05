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

public class GP02Driver extends HuaweiDriver {
	@Override
	public final String getRouterName() {
		return "GP02";
	}

	@Override
	protected RouterStatus interpretRouterStatus() {
		try {
			RouterStatus sts;
			// GP02Ç…ÇÕSignalLevelÇ™ñ≥Ç¢ÇÃÇ≈ÅASignalStrengthÇ©ÇÁçÏê¨Ç∑ÇÈ
			int signalStrength = getIntFromMap(mapStatus, "SignalStrength", 0);
			mapStatus.put("SignalLevel", Integer.toString(signalStrength / 20));
			sts = super.interpretRouterStatus();
			if (sts != null) {
				sts.networkType = RouterStatus.NETWORK_3G;
			}
			return sts;
		} catch (Exception e) {
			return null;
		}
	}
}

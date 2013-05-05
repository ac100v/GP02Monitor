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

public class GL04PDriver extends HuaweiDriver {
	@Override
	public final String getRouterName() {
		return "GL04P";
	}

	@Override
	protected RouterStatus interpretRouterStatus() {
		try {
			RouterStatus sts;
			
			sts = super.interpretRouterStatus();
			if (sts != null) {
				int currentNetworkType = getIntFromMap(mapStatus, "CurrentNetworkType", 0);
				if (currentNetworkType == 19) {
					sts.networkType = RouterStatus.NETWORK_LTE;
				} else {
					sts.networkType = RouterStatus.NETWORK_3G;
				}
			}
			return sts;
		} catch (Exception e) {
			return null;
		}
	}

}

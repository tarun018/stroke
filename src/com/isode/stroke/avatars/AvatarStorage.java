/*
 * Copyright (c) 2010-2015 Isode Limited.
 * All rights reserved.
 * See the COPYING file for more information.
 */
/*
 * Copyright (c) 2015 Tarun Gupta.
 * Licensed under the simplified BSD license.
 * See Documentation/Licenses/BSD-simplified.txt for more information.
 */

package com.isode.stroke.avatars;

import com.isode.stroke.base.ByteArray;
import com.isode.stroke.jid.JID;

public interface AvatarStorage {

	public boolean hasAvatar(String hash);
	public void addAvatar(String hash, ByteArray avatar);
	public String getAvatar(String hash);
	public void setAvatarForJID(JID jid, String hash);
	public String getAvatarForJID(JID jid);
}
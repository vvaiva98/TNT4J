/*
 * Copyright 2014 Nastel Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nastel.jkool.tnt4j.core;

import java.util.UUID;

import com.nastel.jkool.tnt4j.utils.Utils;

/**
 * <p>Implements a Message entity .</p>
 *
 * <p>A <code>Message</code> represents a logical message being transported
 * between a set of entities. Message has id, size, content with a set of arguments
 * and age.
 * </p>
 *
 * @see Activity
 * @see Operation
 * @see Trackable
 *
 * @version $Revision: 7 $
 */
public class Message {

	/**
	 * Maximum length of a Message Signature.
	 * @since Revision 20
	 */
	public static final int MAX_SIGNATURE_LENGTH = 36;

	/**
	 * Maximum length of Message Tag.
	 * @since Revision 22
	 */
	public static final int MAX_TAG_LENGTH = 256;


	/**
	 * Maximum length of Message Value.
	 * @since Revision 22
	 */
	public static final int MAX_VALUE_LENGTH = 256;

	private String		signature;
	private int			size;
	private String		tag;
	private String		strData;
	private Object[]	argList;
	private long		messageAge;



	/**
	 * Creates a message object with generated, unique signature.
	 *
	 * @throws NullPointerException if any arguments are <code>null</code>
	 */
	public Message() {
		this(UUID.randomUUID().toString());
	}

	/**
	 * Creates a message object with the specified properties.
	 *
	 * @param signature unique signature identifying message
	 * @throws NullPointerException if any arguments are <code>null</code>
	 * @throws IllegalArgumentException if signature is empty or too long
	 */
	public Message(String signature) {
		setTrackingId(signature);
	}

	/**
	 * Creates a message object with the specified properties.
	 *
	 * @param signature unique signature identifying message
	 * @param msg actual string message associated with this instance
	 * @param args argument list passed along the message
	 * @throws NullPointerException if any arguments are <code>null</code>
	 * @throws IllegalArgumentException if signature is empty or too long
	 */
	public Message(String signature, String msg, Object...args) {
		setTrackingId(signature);
		setMessage(msg, args);
	}

	/**
	 * Gets the message signature, which is the unique identifier for the message.
	 *
	 * @return message signature
	 */
	public String getTrackingId() {
		return signature;
	}

	/**
	 * Sets the message tracking signature, which is the unique identifier for the message.
	 * Could be any string that will uniquely identify this message.
	 *
	 * @param signature unique signature identifying message
	 * @throws NullPointerException if signature is <code>null</code>
	 * @throws IllegalArgumentException if signature is empty or is too long
	 * @see #MAX_SIGNATURE_LENGTH
	 */
	public void setTrackingId(String signature) {
		if (signature == null)
			throw new NullPointerException("signature must be a non-empty string");
		if (signature.length() == 0)
			throw new IllegalArgumentException("signature must be a non-empty string");
		if (signature.length() > MAX_SIGNATURE_LENGTH)
			throw new IllegalArgumentException("signature length must be <= " + MAX_SIGNATURE_LENGTH);
		this.signature = signature;
	}

	/**
	 * Gets the age of the message that the operation applies to.  This is only
	 * relevant for operations whose type is <code>OpType.RECEIVE</code>.
	 * This value represents the time between when the message was sent (put/write)
	 * and received (get/read).
	 *
	 * @return age of message, in microseconds
	 */
	public long getMessageAge() {
		return messageAge;
	}

	/**
	 * Sets the age of the message in microseconds. Age represents the relative
	 * time that the message was idle. Typically this applies to messages that
	 * are sent or received.
	 *
	 * @param messageAge age of message, in microseconds
	 * @throws IllegalArgumentException if messageAge is negative
	 */
	public void setMessageAge(long messageAge) {
		if (messageAge < 0)
			throw new IllegalArgumentException("messageAge must be non-negative");
		this.messageAge = messageAge;
	}

	/**
	 * Gets the message tag, which is a user-defined value to associate with the message.
	 *
	 * @return user-defined message tag
	 */
	public String getTag() {
		return tag;
	}

	/**
	 * Sets the message tag, which is a user-defined value to associate with the message,
	 * truncating if necessary.
	 *
	 * @param tag user-defined message tag
	 * @see #MAX_TAG_LENGTH
	 */
	public void setTag(String tag) {
		if (tag != null) {
			if (tag.length() > MAX_TAG_LENGTH)
				tag = tag.substring(0, MAX_TAG_LENGTH);
			else if (tag.length() == 0)
				tag = null;
		}
		this.tag = tag;
	}


	/**
	 * Get the size of the message.
	 *
	 * @return message size, in bytes
	 */
	public int getSize() {
		return size;
	}

	/**
	 * Set the size of the message.
	 *
	 * @param size message size, in bytes
	 * @throws IllegalArgumentException if size is negative
	 */
	public void setSize(int size) {
		if (size < 0)
			throw new IllegalArgumentException("size must be non-negative");
		this.size = size;
	}


	/**
	 * Gets the current formatted message with formatted arguments
	 *
	 * @return string message data, or <code>null</code> if there is no data
	 */
	public String getMessage() {
		return Utils.format(strData, argList);
	}

	/**
	 * Gets the current non formatted message
	 *
	 * @return string non formatted message, or <code>null</code> if there is no data
	 */
	public String getMessagePattern() {
		return strData;
	}

	/**
	 * Gets the current message argument list
	 *
	 * @return message argument list
	 */
	public Object[] getMessageArgs() {
		return argList;
	}

	/**
	 * Sets the data for the message.  This is usually the message body.
	 *
	 * @param pattern message pattern
	 * @param args list of arguments
	 */
	public void setMessage(String pattern, Object...args) {
		strData = pattern;
		if (strData != null) {
			setSize(strData.length());
		} else setSize(0);
		argList = args;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return 31 + ((signature == null) ? 0 : signature.hashCode());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Message))
			return false;

		final Message other = (Message) obj;

		if (signature == null) {
			if (other.signature != null)
				return false;
		}
		else if (!signature.equals(other.signature)) {
			return false;
		}
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();

		str.append(getClass().getSimpleName()).append("(")
			.append("TrackId:").append(getTrackingId()).append(",")
			.append("Tag:").append(getTag()).append(",")
			.append("Size:").append(getSize()).append(")");

		return str.toString();
	}

}

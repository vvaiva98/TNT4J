/*
 * Copyright 2014-2015 JKOOL, LLC.
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
package com.nastel.jkool.tnt4j.limiter;

import java.util.concurrent.TimeUnit;

/**
 * Implementation if this interface provide limiter measurement and control
 * based on message/second (MPS) and/or bytes/second. 
 *
 * @version $Revision: 1 $
 */
public interface Limiter {
	static final int UNLIMITED = 0;

	/**
	 * Total count of denied limiter requests using {@code tryThrottle()}
	 *
	 * @return Total count of denied limiter requests
	 */
	long getDenyCount();
	
	/**
	 * Get total number of times throttling was invoked with delay (ms)
	 *
	 * @return total number of times throttling was invoked with delay (ms)
	 */
	long getDelayCount();
	
	/**
	 * Get last time in (seconds) blocked to achieve msg/byte rates
	 *
	 * @return number of seconds blocked to achieve msg/byte rates
	 */
	double getLastDelayTime();
	
	/**
	 * Get total time (seconds) blocked to achieve msg/byte rates
	 *
	 * @return total time (seconds) blocked to achieve msg/byte rates
	 */
	double getTotalDelayTime();
	
	/**
	 * Get accumulated byte count since start or last reset
	 *
	 * @return accumulated byte count
	 */
	long getTotalBytes();
	
	/**
	 * Get accumulated message count since start or last reset
	 *
	 * @return Get accumulated byte count
	 */
	long getTotalMsgs();
	
	/**
	 * Get maximum allowed message/second rate
	 *
	 * @return maximum allowed message rate, 0 means unlimited
	 */
	long getMaxMPS();
	
	/**
	 * Get maximum allowed bytes/second rate
	 *
	 * @return maximum allowed byte rate, 0 means unlimited
	 */
	long getMaxBPS();

	/**
	 * Sets maximum limits (0 means unlimited)
	 * 
	 * @param maxMps maximum message/second rate
	 * @param maxBps maximum bytes/second rate
	 * @return same limiter instance
	 */
	Limiter setLimits(int maxMps, int maxBps);

	/**
	 * Get current message/second rate
	 *
	 * @return maximum allowed message rate
	 */
	long getMPS();

	/**
	 * Get current bytes/second rate
	 *
	 * @return maximum allowed bytes rates
	 */
	long getBPS();

	/**
	 * Time since start/reset in milliseconds
	 *
	 * @return Time since start/reset in milliseconds
	 */
	long getAge();

	/**
	 * Get timestamp in (ms) since start/reset of this limiter
	 *
	 * @return timestamp in (ms) since start/reset of this limiter
	 */
	long getStartTime();

	/**
	 * Reset all measured counts and start counting
	 * from reset time.
	 *
	 * @return same limiter instance
	 */
	Limiter reset();
	
	/**
	 * Enable/disable throttling. {@code limiter()} will never block
	 * if limiter is disabled.
	 *
	 * @param flag true to enable limiter, false otherwise
	 * @return same limiter instance
	 */
	Limiter setEnabled(boolean flag);

	/**
	 * Determine if limiter control is enabled
	 *
	 * @return true if enabled, false otherwise
	 */
	boolean isEnabled();
	
	/**
	 * Obtain permit for message/byte chunk.
	 * This call may block to satisfy max limits.
	 * 
	 * @param msgCount message count
	 * @param byteCount byte count
	 * @return time spend to enforce the rates in seconds.
	 */
	double obtain(int msgCount, int byteCount);

	/**
	 * Try to obtain permit for message/byte chunk.
	 * if it can be obtained immediately without blocking.
	 * This is the same as {@code tryObtain(msgCount, byteCount, 0, anyUnit)}
	 *
	 * @param msgCount message count
	 * @param byteCount byte count
	 * @return true if the permit was obtained, false otherwise
	 */
	boolean tryObtain(int msgCount, int byteCount);

	/**
	 * Try to obtain permit for message/byte chunk.
	 * if it can be obtained within maximum timeout time.
	 *
	 * @param msgCount message count
	 * @param byteCount byte count
	 * @param timeout maximum time to wait
	 * @param unit the time unit of the timeout argument
	 * @return true if the permit was obtained, false otherwise
	 */
	boolean tryObtain(int msgCount, int byteCount, long timeout, TimeUnit unit);
}

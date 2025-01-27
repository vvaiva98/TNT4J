/*
 * Copyright 2014-2023 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jkoolcloud.tnt4j;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.jkoolcloud.tnt4j.config.DefaultConfigFactory;
import com.jkoolcloud.tnt4j.config.TrackerConfig;
import com.jkoolcloud.tnt4j.core.*;
import com.jkoolcloud.tnt4j.dump.*;
import com.jkoolcloud.tnt4j.selector.TrackingSelector;
import com.jkoolcloud.tnt4j.sink.*;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.source.SourceType;
import com.jkoolcloud.tnt4j.tracker.*;
import com.jkoolcloud.tnt4j.utils.Useconds;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * <p>
 * {@code TrackingLogger} is a helper class with calls to {@link Tracker} logging interface.
 * </p>
 * Application should use this helper class instead of obtaining a {@link Tracker} logger instance per thread using
 * {@link TrackerFactory}. {@code TrackingLogger} obtains the {@link Tracker} logger instance and stores it in thread
 * local associated for each thread.
 *
 * <p>
 * A {@link TrackingEvent} represents a specific tracking event that application creates for every discrete activity
 * such as JDBC, JMS, SOAP or any other relevant application activity. Application developers must obtain a
 * {@link Tracker} instance via {@link TrackerFactory}, create instances of {@link TrackingEvent} and use {@code log()}
 * calls to report tracking activities, events and log messages.
 *
 * <p>
 * {@link TrackingActivity} {@code start()/stop()} method calls used to mark application activity boundaries.
 * Applications must create instances of {@link TrackingEvent} using {@code TrackingLogger.newEvent()} method to time
 * individual sub-activities and report them using {@code TrackerLogger.tnt()} method call.
 * </p>
 *
 * <p>
 * Instrumenting typical application logic:
 * </p>
 *
 * <pre>
 * {@code 
 * TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(source);
 * TrackingLogger tracker = TrackingLogger.getInstance(config.build()); // register and obtain Tracker logger instance
 * TrackingActivity activity = tracker.newActivity(); // create a new activity instance
 * activity.start(); // start application activity timing
 * TrackingEvent event = tracker.newEvent(OpLevel.INFO, "SQL-SELECT", "SQL customer lookup"); // create a tracking event
 * TrackingEvent jms_event = tracker.newEvent(OpLevel.INFO, OpType.SEND, "JmsSend", "correlator", "Sending Message"); // create a tracking event
 * event.start(); // start timing a tracking event
 * try {
 * 	...
 * 	...
 * 	event.stop(); // stop timing tracking event
 * 	jms_event.start();
 * 	...
 * 	...
 * 	jms_event.stop(); // stop timing tracking event
 * } catch (SQLException e) {
 * 	event.stop(e); // stop timing tracking event and associate an exception
 * 	jms_event.stop(e); // stop timing tracking event and associate an exception
 * 	...
 * } finally {
 * 	activity.stop(); // end activity timing
 * 	activity.tnt(event); // track and trace tracking event within given activity
 * 	activity.tnt(jms_event); // track and trace tracking event within given activity
 * 	tracker.tnt(activity); // report a tracking activity
 * }
 * }
 * </pre>
 *
 * Source may take advantage of {@code TrackingLogger} conditional logging using {@code TrackingLogger.isSet()} based on
 * applications specific tokens. Below is an example of conditional logging:
 *
 * <pre>
 * {@code 
 * TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(source);
 * TrackingLogger tracker = TrackingLogger.getInstance(config.build()); // register and obtain Tracker logger instance
 * TrackingActivity activity = tracker.newActivity(); // create a new activity instance
 * activity.start(); // start application activity timing
 * TrackingEvent event = tracker.newEvent(OpLevel.NOTICE, "SQL-SELECT", "SQL customer lookup"); // create a tracking event
 * TrackingEvent jms_event = tracker.newEvent(OpLevel.NOTICE, OpType.SEND, "JmsSend", "correlator", "Sending Message"); // create a tracking event
 * event.start(); // start timing a tracking event
 * try {
 * 	...
 * 	...
 * 	event.stop(); // stop timing tracking event
 * 	jms_event.start();
 * 	...
 * 	...
 * 	jms_event.stop(); // stop timing tracking event
 * } catch (SQLException e) {
 * 	event.stop(e); // stop timing tracking event and associate an exception
 * 	jms_event.stop(e); // stop timing tracking event and associate an exception
 * 	...
 * } finally {
 * 	activity.stop(); // end activity timing
 *	// conditional logging using isSet() method to check if a given token matches
 *	if (tracker.isSet(OpLevel.INFO, "com.jkoolcloud.appl.corr", "correlator")) {
 *		activity.tnt(event); // track and trace tracking event within given activity
 *		activity.tnt(jms_event); // track and trace tracking event within given activity
 *	}
 * 	tracker.tnt(activity); // report a tracking activity
 * }
 * }
 * </pre>
 *
 * {@code TrackingLogger} provides a capability to simplify and automate application specific dump handling. An
 * application dump is a collection of application's internal metrics that can be used for problem diagnostics. Source
 * must create an instance of {@code DumpProvider} and register it with {@code TrackingLogger} optionally associate it
 * with a given dump destination {@code DumpSink}(where dump is written to). Dumps can be generated using
 * {@code TrackingLogger.dump()} or can be triggered on JVM shutdown using {@code TrackingLogger.dumpOnShutdown(true)}.
 * By default, {@code TrackingLogger} uses file based {@code DefaultDumpSinkFactory} to generate instances of
 * {@code DumpSink}.
 *
 * <pre>
 * {@code
 * // associated dump provider with a default dump destination (file)
 * TrackingLogger.addDumpProvider(new MyDumpProvider());
 * TrackingLogger.dumpOnShutdown(true);
 * ...
 * // associated dump provider with a user define dump file
 * TrackingLogger.addDumpProvider(TrackingLogger.getDumpDestinationFactory().getInstance("my-dump.log"), new MyDumpProvider());
 * TrackingLogger.dumpOnShutdown(true);
 * ...
 * TrackingLogger.dumpState(); // MyDumpProvider will be called when dumpState() is called.
 * }
 * </pre>
 *
 *
 * @see OpLevel
 * @see OpType
 * @see Tracker
 * @see TrackingEvent
 * @see TrackingActivity
 * @see TrackerFactory
 * @see DumpProvider
 * @see DumpSink
 * @see DumpListener
 * @see SinkErrorListener
 *
 * @version $Revision: 22 $
 */
public class TrackingLogger implements Tracker, AutoCloseable {
	private static final String TRACKER_CONFIG = System.getProperty("tnt4j.tracking.logger.config");
	private static final String TRACKER_SOURCE = System.getProperty("tnt4j.tracking.logger.source",
			TrackingLogger.class.getName());

	private static final Map<DumpProvider, List<DumpSink>> DUMP_DEST_TABLE = new ConcurrentHashMap<>(49);
	private static final Map<String, TrackingLogger> TRACKERS = new ConcurrentHashMap<>(128);

	private static final List<DumpProvider> DUMP_PROVIDERS = new ArrayList<>(10);
	private static final List<DumpSink> DUMP_DESTINATIONS = new ArrayList<>(10);
	private static final List<DumpListener> DUMP_LISTENERS = new ArrayList<>(10);

	private static final TrackerFactory defaultTrackerFactory = new DefaultTrackerFactory();

	private static final DumpHook dumpHook = new DumpHook();
	private static final FlushShutdown flushShutdown = new FlushShutdown();
	private static DumpSinkFactory dumpFactory = null;
	private static DumpSink defaultDumpSink = null;
	private static TrackerFactory factory = null;

	private final Tracker logger;
	private final TrackingSelector selector;
	private final String trackerKey;

	static {
		// load configuration and initialize default factories
		initJavaTiming();
		initConfigurationAndFactories();
	}

	/** Cannot instantiate. */
	private TrackingLogger(Tracker trg, String trackeKey) {
		logger = trg;
		selector = logger.getTrackingSelector();
		this.trackerKey = trackeKey;
	}

	private static void initConfigurationAndFactories() {
		TrackerConfig config = DefaultConfigFactory.getInstance()
				.getConfig(TRACKER_SOURCE, SourceType.APPL, TRACKER_CONFIG).build();
		DefaultEventSinkFactory.setDefaultEventSinkFactory(config.getDefaultEvenSinkFactory());
		factory = config.getTrackerFactory();
		dumpFactory = config.getDumpSinkFactory();
		defaultDumpSink = dumpFactory.getInstance();

		initDumps(config, defaultDumpSink);
	}

	private static void initDumps(TrackerConfig config, DumpSink dumpSink) {
		boolean enableDefaultDumpProviders = config.getBoolean("tracker.dump.provider.default",
				Boolean.getBoolean("tnt4j.dump.provider.default"));
		boolean dumpOnVmHook = config.getBoolean("tracker.dump.on.vm.shutdown",
				Boolean.getBoolean("tnt4j.dump.on.vm.shutdown"));
		boolean dumpOnException = config.getBoolean("tracker.dump.on.exception",
				Boolean.getBoolean("tnt4j.dump.on.exception"));
		boolean flushOnVmHook = config.getBoolean("tracker.flush.on.vm.shutdown",
				Boolean.getBoolean("tnt4j.flush.on.vm.shutdown"));

		if (enableDefaultDumpProviders) {
			addDumpProvider(dumpSink, new PropertiesDumpProvider(Utils.VM_NAME));
			addDumpProvider(dumpSink, new MXBeanDumpProvider(Utils.VM_NAME));
			addDumpProvider(dumpSink, new ThreadDumpProvider(Utils.VM_NAME));
			addDumpProvider(dumpSink, new ThreadDeadlockDumpProvider(Utils.VM_NAME));
			addDumpProvider(dumpSink, new LoggerDumpProvider(Utils.VM_NAME));
		}
		if (dumpOnVmHook) {
			dumpOnShutdown(dumpOnVmHook);
		}
		if (dumpOnException) {
			dumpOnUncaughtException();
		}
		flushOnShutdown(flushOnVmHook);
	}

	/**
	 * Check and enable java timing for use by activities
	 */
	private static void initJavaTiming() {
		try {
			ThreadMXBean tmBean = ManagementFactory.getThreadMXBean();
			boolean cpuTimingSupported = tmBean.isCurrentThreadCpuTimeSupported();
			if (cpuTimingSupported) {
				tmBean.setThreadCpuTimeEnabled(cpuTimingSupported);
			}
			boolean contTimingSupported = tmBean.isThreadContentionMonitoringSupported();
			if (contTimingSupported) {
				tmBean.setThreadContentionMonitoringEnabled(contTimingSupported);
			}
		} catch (Throwable exc) {
			exc.printStackTrace();
		}
	}

	private void checkState() {
		if (logger == null) {
			throw new IllegalStateException("tracker not initialized");
		}
	}

	private static void registerTracker(TrackingLogger tracker) {
		TRACKERS.put(tracker.trackerKey, tracker);
	}

	/**
	 * Obtain a list of all registered/active logger instances.
	 *
	 * @return a list of all active tracking logger instances
	 */
	public static List<TrackingLogger> getAllTrackers() {
		return new ArrayList<>(TRACKERS.values());
	}

	/**
	 * Flush all available trackers
	 *
	 */
	public static void flushAll() {
		List<TrackingLogger> trackers = getAllTrackers();
		for (TrackingLogger logger : trackers) {
			try {
				EventSink sink = logger.getEventSink();
				sink.flush();
			} catch (IOException e) {
			}
		}
	}

	/**
	 * Shutdown all available trackers and sinks.
	 */
	public static void shutdownAll() {
		List<TrackingLogger> trackers = getAllTrackers();
		for (TrackingLogger logger : trackers) {
			shutdown(logger);
		}
	}

	/**
	 * Shutdown defined tracking logger.
	 *
	 * @param logger
	 *            tracking logger to shut down
	 */
	public static void shutdown(TrackingLogger logger) {
		try {
			EventSink sink = logger.getEventSink();
			if (sink != null && sink.isOpen()) {
				Utils.close(sink);
			}
			if (sink instanceof IOShutdown) {
				IOShutdown shut = (IOShutdown) sink;
				shut.shutdown(null);
			}
		} catch (IOException | IllegalStateException e) {
		} finally {
			if (logger != null && logger.isOpen()) {
				Utils.close(logger);
			}
		}
	}

	/**
	 * Create an instance of {@code TrackingLogger} logger.
	 *
	 * @param config
	 *            tracking configuration to be used to create a tracker instance
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 */
	protected static TrackingLogger createInstance(TrackerConfig config, String trackerKey) {
		TrackingLogger tracker = new TrackingLogger(
				(factory == null ? defaultTrackerFactory : factory).getInstance(config), trackerKey);
		registerTracker(tracker);
		return tracker;
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger.
	 *
	 * @param config
	 *            tracking configuration to be used to create a tracker instance
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #createInstance(com.jkoolcloud.tnt4j.config.TrackerConfig, String)
	 * @see #buildTrackerKey(com.jkoolcloud.tnt4j.config.TrackerConfig)
	 */
	public static TrackingLogger getInstance(TrackerConfig config) {
		String trackerKey = buildTrackerKey(config);
		TrackingLogger tLogger = findInstance(trackerKey);
		if (tLogger == null) {
			return createInstance(config, trackerKey);
		} else {
			return tLogger;
		}
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger based on source name.
	 *
	 * @param sourceName
	 *            application source name associated with this logger
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #createInstance(com.jkoolcloud.tnt4j.config.TrackerConfig, String)
	 * @see #buildTrackerKey(String, com.jkoolcloud.tnt4j.source.SourceType, java.util.Map)
	 */
	public static TrackingLogger getInstance(String sourceName) {
		String trackerKey = buildTrackerKey(sourceName, null, null);
		TrackingLogger tLogger = findInstance(trackerKey);
		if (tLogger == null) {
			TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(sourceName);
			return createInstance(config.build(), trackerKey);
		} else {
			return tLogger;
		}
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger based on source name and config map.
	 *
	 * @param sourceName
	 *            application source name associated with this logger
	 * @param configMap
	 *            configuration map containing source/properties configuration
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #getInstance(String, com.jkoolcloud.tnt4j.source.SourceType, java.util.Map)
	 */
	public static TrackingLogger getInstance(String sourceName, Map<String, Properties> configMap) {
		return getInstance(sourceName, SourceType.APPL, configMap);
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger based on source name and type.
	 *
	 * @param sourceName
	 *            application source name associated with this logger
	 * @param type
	 *            application source type associated with this logger
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #createInstance(com.jkoolcloud.tnt4j.config.TrackerConfig, String)
	 * @see #buildTrackerKey(String, com.jkoolcloud.tnt4j.source.SourceType, java.util.Map)
	 */
	public static TrackingLogger getInstance(String sourceName, SourceType type) {
		String trackerKey = buildTrackerKey(sourceName, type, null);
		TrackingLogger tLogger = findInstance(trackerKey);
		if (tLogger == null) {
			TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(sourceName, type);
			return createInstance(config.build(), trackerKey);
		} else {
			return tLogger;
		}
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger based on source name, type and config map.
	 *
	 * @param sourceName
	 *            application source name associated with this logger
	 * @param type
	 *            application source type associated with this logger
	 * @param configMap
	 *            configuration map containing source/properties configuration
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #createInstance(com.jkoolcloud.tnt4j.config.TrackerConfig, String)
	 * @see #buildTrackerKey(String, com.jkoolcloud.tnt4j.source.SourceType, java.util.Map)
	 */
	public static TrackingLogger getInstance(String sourceName, SourceType type, Map<String, Properties> configMap) {
		String trackerKey = buildTrackerKey(sourceName, type, configMap);
		TrackingLogger tLogger = findInstance(trackerKey);
		if (tLogger == null) {
			TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(sourceName, type, configMap);
			return createInstance(config.build(), trackerKey);
		} else {
			return tLogger;
		}
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger based on a given class.
	 *
	 * @param clazz
	 *            application class used as source name
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #createInstance(com.jkoolcloud.tnt4j.config.TrackerConfig, String)
	 * @see #buildTrackerKey(String, com.jkoolcloud.tnt4j.source.SourceType, java.util.Map)
	 */
	public static TrackingLogger getInstance(Class<?> clazz) {
		String trackerKey = buildTrackerKey(clazz.getName(), null, null);
		TrackingLogger tLogger = findInstance(trackerKey);
		if (tLogger == null) {
			TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(clazz);
			return createInstance(config.build(), trackerKey);
		} else {
			return tLogger;
		}
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger based on a given class and config map.
	 *
	 * @param clazz
	 *            application class used as source name
	 * @param configMap
	 *            configuration map containing source/properties configuration
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #getInstance(Class, com.jkoolcloud.tnt4j.source.SourceType, java.util.Map)
	 */
	public static TrackingLogger getInstance(Class<?> clazz, Map<String, Properties> configMap) {
		return getInstance(clazz, SourceType.APPL, configMap);
	}

	/**
	 * Obtain an instance of {@code TrackingLogger} logger based on a given class, source type and config map.
	 *
	 * @param clazz
	 *            application class used as source name
	 * @param type
	 *            application source type associated with this logger
	 * @param configMap
	 *            configuration map containing source/properties configuration
	 * @return tracking logger instance
	 *
	 * @see TrackerConfig
	 * @see #createInstance(com.jkoolcloud.tnt4j.config.TrackerConfig, String)
	 * @see #buildTrackerKey(String, com.jkoolcloud.tnt4j.source.SourceType, java.util.Map)
	 */
	public static TrackingLogger getInstance(Class<?> clazz, SourceType type, Map<String, Properties> configMap) {
		String trackerKey = buildTrackerKey(clazz.getName(), type, configMap);
		TrackingLogger tLogger = findInstance(trackerKey);
		if (tLogger == null) {
			TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(clazz, type, configMap);
			return createInstance(config.build(), trackerKey);
		} else {
			return tLogger;
		}
	}

	/**
	 * Finds registered/active logger instance by provided tracker key.
	 * 
	 * @param trackerKey
	 *            tracker key string
	 * @return tracking logger instance
	 */
	protected static TrackingLogger findInstance(String trackerKey) {
		return TRACKERS.get(trackerKey);
	}

	/**
	 * Builds tracker key string from provided application source name, type and tracker configuration.
	 * 
	 * @param sourceName
	 *            application source name associated with this logger
	 * @param type
	 *            application source type associated with this logger
	 * @param configMap
	 *            configuration map containing source/properties configuration
	 * @return tracker key string
	 */
	protected static String buildTrackerKey(String sourceName, SourceType type, Map<String, Properties> configMap) {
		return sourceName + "<>?:L" + (type == null ? SourceType.APPL : type) + "?>><"
				+ (configMap == null ? "no_config_map" : String.valueOf(configMap.hashCode()));
	}

	/**
	 * Builds tracker key string from provided tracker configuration instance.
	 * 
	 * @param config
	 *            tracking configuration to be used to create a tracker instance
	 * @return tracker key string
	 */
	protected static String buildTrackerKey(TrackerConfig config) {
		return "<>?:L" + SourceType.VIRTUAL + "?>><"
				+ (config == null ? "no_config_map" : String.valueOf(config.hashCode()));
	}

	/**
	 * Register a user defined tracker factory. Default is {@code DefaultTrackerFactory}.
	 *
	 * @param fac
	 *            User defined tracker factory
	 * @see TrackerFactory
	 * @see DefaultTrackerFactory
	 */
	public static void setTrackerFactory(TrackerFactory fac) {
		factory = (fac != null ? fac : factory);
	}

	/**
	 * Register a user defined dump destination factory used to generate instances of {@code DumpSink}. Default is
	 * {@code DefaultDumpSinkFactory}.
	 *
	 * @param defFac
	 *            User default dump destination factory
	 * @param defDest
	 *            User default dump destination
	 * @see DumpSink
	 * @see DumpSinkFactory
	 * @see DefaultDumpSinkFactory
	 */
	public static void setDefaultDumpConfig(DumpSinkFactory defFac, DumpSink defDest) {
		dumpFactory = (defFac != null ? defFac : dumpFactory);
		defaultDumpSink = (defDest != null ? defDest : defaultDumpSink);
	}

	/**
	 * Return currently registered dump sink factory. Default is {@code DefaultDumpSinkFactory}.
	 *
	 * @return currently registered dump sink factory
	 * @see DumpSinkFactory
	 * @see DefaultDumpSinkFactory
	 */
	public static DumpSinkFactory getDumpSinkFactory() {
		return dumpFactory;
	}

	/**
	 * Determine of a particular sev/key/value combination is trackable. Use this method to determine if tracking is
	 * enabled/disabled for a specific key/value pair. Example, checking if order id "723772" is trackable:
	 *
	 * {@code logger.isSet(OpLevel.INFO, "orderapp.order.id", "723772");}
	 *
	 * @param sev
	 *            severity of to be checked
	 * @param key
	 *            key associated with tracking activity
	 * @param value
	 *            associated value with a given key
	 *
	 * @return {@code true} of combination is set, {@code false} otherwise
	 * @see OpLevel
	 */
	public boolean isSet(OpLevel sev, Object key, Object value) {
		if (logger != null) {
			return selector.isSet(sev, key, value);
		}
		return false;
	}

	/**
	 * Determine of a particular sev/key is trackable. Use this method to determine if tracking is enabled/disabled for
	 * a specific severity. This call is equivalent to {@code logger.isSet(sev, key, null);}
	 *
	 * @param sev
	 *            severity of to be checked
	 *
	 * @param key
	 *            key to be checked for being trackable
	 *
	 * @return {@code true} of combination is set, {@code false} otherwise
	 * @see OpLevel
	 */
	public boolean isSet(OpLevel sev, Object key) {
		if (logger != null) {
			return selector.isSet(sev, key);
		}
		return false;
	}

	/**
	 * Determine if a particular sev for the registered application name used in {@code TrackingLogger.getInstance()}
	 * call. Use this method to determine if tracking is enabled/disabled for a specific severity. This call is
	 * equivalent to {@code logger.getTracker().getEventSink().isSet(sev)}
	 *
	 * @param sev
	 *            severity of to be checked
	 *
	 * @return {@code true} of combination is set, {@code false} otherwise
	 * @see OpLevel
	 */
	public boolean isSet(OpLevel sev) {
		if (logger != null) {
			return logger.getEventSink().isSet(sev);
		}
		return false;
	}

	/**
	 * Set sev/key/value combination for tracking
	 *
	 * @param sev
	 *            severity of to be checked
	 * @param key
	 *            key associated with tracking activity
	 * @param value
	 *            associated value with a given key
	 *
	 * @see OpLevel
	 */
	public void set(OpLevel sev, Object key, Object value) {
		if (logger != null) {
			selector.set(sev, key, value);
		}
	}

	/**
	 * Set sev/key combination for tracking. This is the same as calling {@code set(sev, key, null)}, where value is
	 * {@code null}.
	 *
	 * @param sev
	 *            severity of to be checked
	 * @param key
	 *            key associated with tracking activity
	 *
	 * @see OpLevel
	 */
	public void set(OpLevel sev, Object key) {
		if (logger != null) {
			selector.set(sev, key);
		}
	}

	/**
	 * Get value associated with a given key from the tracking selector repository.
	 *
	 * @param key
	 *            key associated with tracking activity
	 * @return value for specified key, or {@code null} if key not found
	 */
	public Object get(Object key) {
		if (logger != null) {
			return selector.get(key);
		}
		return null;
	}

	/**
	 * Obtain a list of keys available in the selector
	 *
	 * @return iterator containing all available keys
	 */
	public Iterator<? extends Object> getKeys() {
		if (logger != null) {
			return selector.getKeys();
		}
		return null;
	}

	/**
	 * Close this instance of {@code TrackingLogger}. Existing {@link Tracker} logger (if already opened) is closed and
	 * released.
	 *
	 * @see TrackerConfig
	 */
	@Override
	public void close() {
		if (logger != null) {
			factory.close(logger);
			TRACKERS.remove(this.trackerKey);
		}
	}

	/**
	 * Log a single message with a given severity level and a number of user supplied arguments. Message pattern is
	 * based on the format defined by {@code MessageFormat}. This logging type is more efficient than string
	 * concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.log(OpLevel.DEBUG, "My message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param level
	 *            severity level
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 */
	@Override
	public void log(OpLevel level, String msg, Object... args) {
		checkState();
		logger.log(level, msg, args);
	}

	/**
	 * Log a single DEBUG message and a number of user supplied arguments. Message pattern is based on the format
	 * defined by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.debug("My message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void debug(String msg, Object... args) {
		log(OpLevel.DEBUG, msg, args);
	}

	/**
	 * Log a single TRACE message and a number of user supplied arguments. Message pattern is based on the format
	 * defined by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.trace("My message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void trace(String msg, Object... args) {
		log(OpLevel.TRACE, msg, args);
	}

	/**
	 * Log a single ERROR message and a number of user supplied arguments. Message pattern is based on the format
	 * defined by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.error("My error message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void error(String msg, Object... args) {
		log(OpLevel.ERROR, msg, args);
	}

	/**
	 * Log a single FATAL message and a number of user supplied arguments. Message pattern is based on the format
	 * defined by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.fatal("My error message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void fatal(String msg, Object... args) {
		log(OpLevel.FATAL, msg, args);
	}

	/**
	 * Log a single HALT message and a number of user supplied arguments. Message pattern is based on the format defined
	 * by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.halt("My error message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void halt(String msg, Object... args) {
		log(OpLevel.HALT, msg, args);
	}

	/**
	 * Log a single WARNING message and a number of user supplied arguments. Message pattern is based on the format
	 * defined by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.warn("My message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void warn(String msg, Object... args) {
		log(OpLevel.WARNING, msg, args);
	}

	/**
	 * Log a single INFO message and a number of user supplied arguments. Message pattern is based on the format defined
	 * by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.info("My message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void info(String msg, Object... args) {
		log(OpLevel.INFO, msg, args);
	}

	/**
	 * Log a single NOTICE message and a number of user supplied arguments. Message pattern is based on the format
	 * defined by {@code MessageFormat}. This logging type is more efficient than string concatenation.
	 * 
	 * <pre>
	 * {@code 
	 * logger.notice("My message arg={0}, arg={1}", parm1, parm2);
	 * }
	 * </pre>
	 * 
	 * @param msg
	 *            message or message pattern
	 * @param args
	 *            user defined arguments supplied along side given message
	 * @see OpLevel
	 * @see java.text.MessageFormat
	 */
	public void notice(String msg, Object... args) {
		log(OpLevel.NOTICE, msg, args);
	}

	/**
	 * Report a single tracking activity. Call after instance of {@link TrackingActivity} has been completed using
	 * {@code TrackingActivity.stop()} and {@code TrackingActivity.tnt()} calls.
	 *
	 * @param activity
	 *            tracking activity to be reported
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingActivity
	 */
	@Override
	public void tnt(TrackingActivity activity) {
		if (activity == null) {
			return;
		}
		checkState();
		logger.tnt(activity);
	}

	/**
	 * Report a single tracking event as a single activity. Call after instance of {@link TrackingEvent} has been
	 * completed using {@code TrackingEvent.stop()} call.
	 * 
	 * @param event
	 *            tracking event to be reported as a single activity
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingEvent
	 */
	@Override
	public void tnt(TrackingEvent event) {
		if (event == null) {
			return;
		}
		checkState();
		logger.tnt(event);
	}

	/**
	 * Report a single snapshot.
	 *
	 * @param snapshot
	 *            snapshot to be tracked and logged
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see Snapshot
	 * @see Property
	 */
	@Override
	public void tnt(Snapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		checkState();
		logger.tnt(snapshot);
	}

	/**
	 * Report a single tracking event
	 *
	 * @param severity
	 *            severity level of the reported message
	 * @param opName
	 *            operation name associated with the event message
	 * @param correlator
	 *            event correlator
	 * @param msg
	 *            event text message
	 * @param args
	 *            argument list, exception passed alongside given message
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingActivity
	 * @see OpLevel
	 */
	public void tnt(OpLevel severity, String opName, String correlator, String msg, Object... args) {
		tnt(severity, OpType.CALL, opName, correlator, 0, msg, args);
	}

	/**
	 * Report a single tracking event
	 *
	 * @param severity
	 *            severity level of the reported message
	 * @param opType
	 *            operation type
	 * @param opName
	 *            operation name associated with the event message
	 * @param correlator
	 *            event correlator
	 * @param elapsed
	 *            elapsed time of the event in microseconds.
	 * @param msg
	 *            event text message
	 * @param args
	 *            argument list, exception passed alongside given message
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingActivity
	 * @see OpLevel
	 */
	public void tnt(OpLevel severity, OpType opType, String opName, String correlator, long elapsed, String msg,
			Object... args) {
		tnt(severity, opType, opName, correlator, null, elapsed, msg, args);
	}

	/**
	 * Report a single tracking event
	 *
	 * @param severity
	 *            severity level of the reported message
	 * @param opType
	 *            operation type
	 * @param opName
	 *            operation name associated with the event message
	 * @param correlator
	 *            event correlator
	 * @param tag
	 *            message tag
	 * @param elapsed
	 *            elapsed time of the event in microseconds.
	 * @param msg
	 *            event text message
	 * @param args
	 *            argument list, exception passed alongside given message
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingActivity
	 * @see OpLevel
	 */
	public void tnt(OpLevel severity, OpType opType, String opName, String correlator, String tag, long elapsed,
			String msg, Object... args) {
		checkState();
		TrackingEvent event = logger.newEvent(severity, opType, opName, correlator, tag, msg, args);
		Throwable ex = Utils.getThrowable(args);
		event.stop(ex != null ? OpCompCode.WARNING : OpCompCode.SUCCESS, 0, ex, Useconds.CURRENT.get(), elapsed);
		logger.tnt(event);
	}

	/**
	 * Report a single tracking event using a binary message body
	 *
	 * @param severity
	 *            severity level of the reported message
	 * @param opName
	 *            operation name associated with the event message
	 * @param correlator
	 *            event correlator
	 * @param msg
	 *            event binary message
	 * @param args
	 *            argument list, exception passed alongside given message
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingActivity
	 * @see OpLevel
	 */
	public void tnt(OpLevel severity, String opName, String correlator, byte[] msg, Object... args) {
		tnt(severity, OpType.CALL, opName, correlator, 0, msg, args);
	}

	/**
	 * Report a single tracking event using a binary message body
	 *
	 * @param severity
	 *            severity level of the reported message
	 * @param opType
	 *            operation type
	 * @param opName
	 *            operation name associated with the event message
	 * @param correlator
	 *            event correlator
	 * @param elapsed
	 *            elapsed time of the event in microseconds.
	 * @param msg
	 *            event binary message
	 * @param args
	 *            argument list, exception passed alongside given message
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingActivity
	 * @see OpLevel
	 */
	public void tnt(OpLevel severity, OpType opType, String opName, String correlator, long elapsed, byte[] msg,
			Object... args) {
		tnt(severity, opType, opName, correlator, null, elapsed, msg, args);
	}

	/**
	 * Report a single tracking event using a binary message body
	 *
	 * @param severity
	 *            severity level of the reported message
	 * @param opType
	 *            operation type
	 * @param opName
	 *            operation name associated with the event message
	 * @param correlator
	 *            event correlator
	 * @param tag
	 *            message tag
	 * @param elapsed
	 *            elapsed time of the event in microseconds.
	 * @param msg
	 *            event binary message
	 * @param args
	 *            argument list, exception passed alongside given message
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see TrackingActivity
	 * @see OpLevel
	 */
	public void tnt(OpLevel severity, OpType opType, String opName, String correlator, String tag, long elapsed,
			byte[] msg, Object... args) {
		checkState();
		TrackingEvent event = logger.newEvent(severity, opType, opName, correlator, tag, msg, args);
		Throwable ex = Utils.getThrowable(args);
		event.stop(ex != null ? OpCompCode.WARNING : OpCompCode.SUCCESS, 0, ex, Useconds.CURRENT.get(), elapsed);
		logger.tnt(event);
	}

	@Override
	public Dataset newDataset(String name) {
		checkState();
		return logger.newDataset(name);
	}

	@Override
	public Dataset newDataset(String cat, String name) {
		checkState();
		return logger.newDataset(cat, name);
	}

	@Override
	public LogEntry newLogEntry(String name) {
		checkState();
		return logger.newLogEntry(name);
	}

	@Override
	public LogEntry newLogEntry(String cat, String name) {
		checkState();
		return logger.newLogEntry(cat, name);
	}

	@Override
	public Snapshot newSnapshot(String name) {
		checkState();
		return logger.newSnapshot(name);
	}

	@Override
	public Snapshot newSnapshot(String cat, String name) {
		checkState();
		return logger.newSnapshot(cat, name);
	}

	@Override
	public Snapshot newSnapshot(String cat, String name, OpLevel level) {
		checkState();
		return logger.newSnapshot(cat, name, level);
	}

	@Override
	public Property newProperty(String key, Object val) {
		checkState();
		return logger.newProperty(key, val);
	}

	@Override
	public Property newProperty(String key, Object val, String valType) {
		checkState();
		return logger.newProperty(key, val, valType);
	}

	@Override
	public TrackingActivity newActivity() {
		checkState();
		return logger.newActivity(OpLevel.INFO);
	}

	@Override
	public TrackingActivity newActivity(OpLevel level) {
		checkState();
		return logger.newActivity(level);
	}

	@Override
	public TrackingActivity newActivity(OpLevel level, String name) {
		checkState();
		return logger.newActivity(level, name);
	}

	@Override
	public TrackingActivity newActivity(OpLevel level, String name, String signature) {
		checkState();
		return logger.newActivity(level, name, signature);
	}

	@Override
	public TrackingEvent newEvent(String opName, String msg, Object... args) {
		checkState();
		return logger.newEvent(opName, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, String opName, String correlator, String msg, Object... args) {
		checkState();
		return logger.newEvent(severity, opName, correlator, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, OpType opType, String opName, String correlator, String tag,
			String msg, Object... args) {
		checkState();
		return logger.newEvent(severity, opType, opName, correlator, tag, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, String opName, String correlator, byte[] msg, Object... args) {
		checkState();
		return logger.newEvent(severity, opName, correlator, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, OpType opType, String opName, String correlator, String tag,
			byte[] msg, Object... args) {
		checkState();
		return logger.newEvent(severity, opType, opName, correlator, tag, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, String opName, Collection<String> correlators, String msg,
			Object... args) {
		checkState();
		return logger.newEvent(severity, opName, correlators, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, OpType opType, String opName, Collection<String> correlators,
			Collection<String> tags, String msg, Object... args) {
		checkState();
		return logger.newEvent(severity, opType, opName, correlators, tags, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, String opName, Collection<String> correlators, byte[] msg,
			Object... args) {
		checkState();
		return logger.newEvent(severity, opName, correlators, msg, args);
	}

	@Override
	public TrackingEvent newEvent(OpLevel severity, OpType opType, String opName, Collection<String> correlators,
			Collection<String> tags, byte[] msg, Object... args) {
		checkState();
		return logger.newEvent(severity, opType, opName, correlators, tags, msg, args);
	}

	/**
	 * Returns currently registered {@link Tracker} logger associated with the current thread. {@link Tracker} logger is
	 * associated with the current thread after the register() call. {@link Tracker} logger instance is not thread safe.
	 *
	 * @return {@link Tracker} logger associated with the current thread or {@code null} of non-available.
	 * @see Tracker
	 */
	public Tracker getTracker() {
		return logger;
	}

	/**
	 * Register a tracking filter associated with the tracker. Tracking filter allows consolidation of all conditional
	 * tracking logic into a single class.
	 *
	 * @see TrackingFilter
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 */
	@Override
	public void setTrackingFilter(TrackingFilter filter) {
		checkState();
		logger.setTrackingFilter(filter);
	}

	/**
	 * Add a sink log listener, which is triggered log activities occurs when writing to the event sink.
	 *
	 * @param listener
	 *            user supplied sink log listener
	 * @see SinkErrorListener
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 */
	public void addSinkLogEventListener(SinkLogEventListener listener) {
		checkState();
		logger.getEventSink().addSinkLogEventListener(listener);
	}

	/**
	 * Remove a sink log listener, which is triggered log activities occurs when writing to the event sink.
	 *
	 * @param listener
	 *            user supplied sink log listener
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see SinkErrorListener
	 */
	public void removeSinkLogEventListener(SinkLogEventListener listener) {
		checkState();
		logger.getEventSink().removeSinkLogEventListener(listener);
	}

	/**
	 * Add and register a sink error listener, which is triggered error occurs when writing to the event sink.
	 *
	 * @param listener
	 *            user supplied sink error listener
	 * @see SinkErrorListener
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 */
	public void addSinkErrorListener(SinkErrorListener listener) {
		checkState();
		logger.getEventSink().addSinkErrorListener(listener);
	}

	/**
	 * Remove a sink error listener, which is triggered error occurs when writing to the event sink.
	 *
	 * @param listener
	 *            user supplied sink error listener
	 * @see SinkErrorListener
	 */
	public void removeSinkErrorListener(SinkErrorListener listener) {
		checkState();
		logger.getEventSink().removeSinkErrorListener(listener);
	}

	/**
	 * Add and register a sink filter, which is used to filter out events written to the underlying sink. Sink event
	 * listeners get called every time an event/activity or message is written to the underlying event sink.
	 *
	 * @param filter
	 *            user supplied sink filter
	 * @see SinkEventFilter
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 */
	public void addSinkEventFilter(SinkEventFilter filter) {
		checkState();
		logger.getEventSink().addSinkEventFilter(filter);
	}

	/**
	 * Remove sink filter, which is used to filter out events written to the underlying sink.
	 *
	 * @param filter
	 *            user supplied sink filter
	 * @throws IllegalStateException
	 *             when tracker is not initialized
	 * @see SinkEventFilter
	 */
	public void removeSinkEventFilter(SinkEventFilter filter) {
		checkState();
		logger.getEventSink().removeSinkEventFilter(filter);
	}

	/**
	 * Add and register a dump listener, which is triggered when dump is generated by {@code dump()} call.
	 *
	 * @param lst
	 *            user supplied dump listener
	 * @see DumpListener
	 */
	public static void addDumpListener(DumpListener lst) {
		DUMP_LISTENERS.add(lst);
	}

	/**
	 * Remove a dump listener, which is triggered when dump is generated by {@code dump()} call.
	 *
	 * @param lst
	 *            user supplied dump listener
	 * @see DumpListener
	 */
	public static void removeDumpListener(DumpListener lst) {
		DUMP_LISTENERS.remove(lst);
	}

	/**
	 * Add and register a dump provider. Instances of {@code DumpProvider} provide implementation for underlying classes
	 * that generate application specific dumps. By default, supplied dump provider is associated with a default
	 * {@code DumpSink}.
	 *
	 * @param dp
	 *            user supplied dump provider
	 *
	 * @see DumpProvider
	 * @see DumpSink
	 */
	public static void addDumpProvider(DumpProvider dp) {
		addDumpProvider(defaultDumpSink, dp);
	}

	/**
	 * Add and register a dump provider with a user specified {@code DumpSink}. Instances of {@code DumpProvider}
	 * interface provide implementation for underlying classes that generate application specific dumps. This dump
	 * provider will be triggered for the specified {@code DumpSink} only. Instance of {@code DumpSink} can be created
	 * by {@code DumpDestinationFactory}. By default, {@code PropertiesDumpProvider}, {@code MXBeanDumpProvider},
	 * {@code ThreadDumpProvider}, {@code ThreadDeadlockDumpProvider} are auto registered with {@code FileDumpSink}
	 * during initialization of {@code TrackingLogger} class.
	 *
	 * @param df
	 *            user supplied dump destination associated with dump provider
	 * @param dp
	 *            user supplied dump provider
	 *
	 * @see DumpProvider
	 * @see DumpSink
	 * @see DumpSinkFactory
	 * @see PropertiesDumpProvider
	 * @see MXBeanDumpProvider
	 * @see ThreadDumpProvider
	 * @see ThreadDeadlockDumpProvider
	 */
	public static synchronized void addDumpProvider(DumpSink df, DumpProvider dp) {
		// add to dump->dest table second
		List<DumpSink> destList = DUMP_DEST_TABLE.get(dp);
		if (destList == null) {
			destList = new ArrayList<>(10);
			DUMP_PROVIDERS.add(dp);
		}
		boolean exists = destList.contains(df);
		if (!exists) {
			destList.add(df);
		}
		exists = DUMP_DESTINATIONS.contains(df);
		if (!exists) {
			DUMP_DESTINATIONS.add(df);
		}
		DUMP_DEST_TABLE.putIfAbsent(dp, destList);
	}

	/**
	 * Generate dumps backed by registered {@code DumpProvider} instances written to registered {@code DumpSink}
	 * instances. The method first opens all registered dump destinations and then iterates over all dump providers to
	 * obtain dumps of instance {@code DumpCollection}. Registered instances of {@code DumpListener} are triggered for
	 * before, after, error, complete conditions during this call.
	 *
	 * @see DumpListener
	 * @see DumpCollection
	 * @see DumpProvider
	 * @see DumpSink
	 * @see DumpSinkFactory
	 */
	public static synchronized void dumpState() {
		dumpState(null);
	}

	/**
	 * Generate dumps backed by registered {@code DumpProvider} instances written to registered {@code DumpSink}
	 * instances. The method first opens all registered dump destinations and then iterates over all dump providers to
	 * obtain dumps of instance {@code DumpCollection}. Registered instances of {@code DumpListener} are triggered for
	 * before, after, error, complete conditions during this call.
	 *
	 * @param reason
	 *            reason why dump is generated
	 *
	 * @see DumpListener
	 * @see DumpCollection
	 * @see DumpProvider
	 * @see DumpSink
	 * @see DumpSinkFactory
	 */
	public static synchronized void dumpState(Throwable reason) {
		try {
			openDumpSinks();
			for (DumpProvider dumpProvider : DUMP_PROVIDERS) {
				List<DumpSink> dList = DUMP_DEST_TABLE.get(dumpProvider);
				DumpCollection dump = null;
				Throwable error = reason;
				try {
					dump = dumpProvider.getDump();
					if (dump != null && reason != null) {
						dump.setReason(reason);
					}
					notifyDumpListeners(DumpProvider.DUMP_BEFORE, dumpProvider, dump, dList, reason);
					if (dump != null) {
						for (DumpSink dest : dList) {
							dest.write(dump);
						}
					}
				} catch (Throwable ex) {
					ex.initCause(reason);
					error = ex;
				} finally {
					notifyDumpListeners(DumpProvider.DUMP_AFTER, dumpProvider, dump, dList, error);
				}
			}
		} finally {
			closeDumpSinks();
		}
	}

	/**
	 * Enable/disable VM shutdown hook that will automatically trigger a dump.
	 *
	 * @param flag
	 *            enable/disable VM shutdown hook that triggers a dump
	 */
	public static void dumpOnShutdown(boolean flag) {
		try {
			if (flag) {
				Runtime.getRuntime().addShutdownHook(dumpHook);
			} else {
				Runtime.getRuntime().removeShutdownHook(dumpHook);
			}
		} catch (IllegalStateException exc) { // NOTE: already shutting down
		}
	}

	/**
	 * Adds VM shutdown hook that will automatically flush and shutdown all registered trackers.
	 *
	 * @param flag
	 *            enable/disable flush triggering
	 */
	public static void flushOnShutdown(boolean flag) {
		flushShutdown.setFlush(flag);
		try {
			Runtime.getRuntime().addShutdownHook(flushShutdown);
		} catch (IllegalStateException exc) { // NOTE: already shutting down
		}
	}

	/**
	 * Enable/disable {@code UncaughtExceptionHandler} hook that will automatically trigger a dump on uncaught thread
	 * exceptions for all threads.
	 *
	 */
	public static void dumpOnUncaughtException() {
		Thread.setDefaultUncaughtExceptionHandler(dumpHook);
	}

	private static void openDumpSinks() {
		for (DumpSink dest : DUMP_DESTINATIONS) {
			try {
				dest.open();
			} catch (Throwable ex) {
				notifyDumpListeners(DumpProvider.DUMP_ERROR, dest, null, DUMP_DESTINATIONS, ex);
			}
		}
	}

	private static void closeDumpSinks() {
		try {
			notifyDumpListeners(DumpProvider.DUMP_COMPLETE, Thread.currentThread(), null, DUMP_DESTINATIONS);
		} finally {
			for (DumpSink dest : DUMP_DESTINATIONS) {
				try {
					dest.close();
				} catch (Throwable ex) {
					ArrayList<DumpSink> list = new ArrayList<>(1);
					list.add(dest);
					notifyDumpListeners(DumpProvider.DUMP_ERROR, Thread.currentThread(), null, list, ex);
				}
			}
		}
	}

	private static void notifyDumpListeners(int type, Object source, DumpCollection dump, List<DumpSink> dList) {
		notifyDumpListeners(type, source, dump, dList, null);
	}

	private static void notifyDumpListeners(int type, Object source, DumpCollection dump, List<DumpSink> dList,
			Throwable ex) {
		synchronized (DUMP_LISTENERS) {
			for (DumpListener dls : DUMP_LISTENERS) {
				dls.onDumpEvent(new DumpEvent(source, type, dump, dList, ex));
			}
		}
	}

	@Override
	public TrackingActivity[] getActivityStack() {
		checkState();
		return logger.getActivityStack();
	}

	@Override
	public TrackerConfig getConfiguration() {
		checkState();
		return logger.getConfiguration();
	}

	@Override
	public TrackingActivity getCurrentActivity() {
		checkState();
		return logger.getCurrentActivity();
	}

	@Override
	public TrackingActivity getRootActivity() {
		checkState();
		return logger.getRootActivity();
	}

	@Override
	public EventSink getEventSink() {
		checkState();
		return logger.getEventSink();
	}

	@Override
	public Source getSource() {
		checkState();
		return logger.getSource();
	}

	@Override
	public int getStackSize() {
		checkState();
		return logger.getStackSize();
	}

	@Override
	public StackTraceElement[] getStackTrace() {
		checkState();
		return logger.getStackTrace();
	}

	@Override
	public TrackingSelector getTrackingSelector() {
		checkState();
		return logger.getTrackingSelector();
	}

	@Override
	public boolean isOpen() {
		checkState();
		return logger.isOpen();
	}

	@Override
	public void open() throws IOException {
		checkState();
		logger.open();
	}

	@Override
	public Map<String, Object> getStats() {
		checkState();
		return logger.getStats();
	}

	@Override
	public KeyValueStats getStats(Map<String, Object> stats) {
		checkState();
		return logger.getStats(stats);
	}

	@Override
	public void resetStats() {
		checkState();
		logger.resetStats();
	}

	@Override
	public Tracker setKeepThreadContext(boolean flag) {
		checkState();
		return logger.setKeepThreadContext(flag);
	}

	@Override
	public boolean getKeepThreadContext() {
		checkState();
		return logger.getKeepThreadContext();
	}

	@Override
	public String getId() {
		checkState();
		return logger.getId();
	}

	@Override
	public String newUUID() {
		checkState();
		return logger.newUUID();
	}

	@Override
	public String newUUID(Object obj) {
		checkState();
		return logger.newUUID(obj);
	}

	@Override
	public String toString() {
		return super.toString() + "{logger: " + logger + "}";
	}
}

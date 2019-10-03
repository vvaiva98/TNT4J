/*
 * Copyright 2014-2019 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.sink.impl.jul;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;

import com.jkoolcloud.tnt4j.config.ConfigException;
import com.jkoolcloud.tnt4j.format.DefaultFormatter;
import com.jkoolcloud.tnt4j.format.EventFormatter;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.sink.EventSinkFactory;
import com.jkoolcloud.tnt4j.sink.impl.FileEventSinkFactory;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * <p>
 * Concrete implementation of {@link EventSinkFactory} interface for java unified logging (JUL), which creates instances of {@link JULEventSink}. This
 * factory uses {@link JULEventSink} as the underlying sink provider provider and by default uses JUL XML formatter.
 * </p>
 *
 *
 * @see EventSink
 * @see DefaultFormatter
 * @see JULEventSink
 *
 * @version $Revision: 1 $
 *
 */
public class JULEventSinkFactory extends FileEventSinkFactory {
	public static final int DEF_LOG_COUNT = Integer.getInteger("tnt4j.jul.count", 3);
	public static final int DEF_LOG_SIZE_BYTES = Integer.getInteger("tnt4j.jul.size", 10 * 1024 * 1024);
	public static final String DEF_PATTERN = System.getProperty("tnt4j.jul.pattern", "-%u-%g");
	public static final String DEF_LEVEL = System.getProperty("tnt4j.jul.level", Level.FINE.getName());

	static {
		_loadConfig(System.getProperty("tnt4j.jul.config"));
	}

	String logConfigFile;
	int logCount = DEF_LOG_COUNT;
	int byteLimit = DEF_LOG_SIZE_BYTES;
	Level level = Level.parse(DEF_LEVEL);

	/**
	 * Create a default sink factory with default file name based on current timestamp: yyyy-MM-dd.log.
	 */
	public JULEventSinkFactory() {
	}

	/**
	 * Create a sink factory with a given file name.
	 * 
	 * @param fname
	 *            file name
	 */
	public JULEventSinkFactory(String fname) {
		fileName = fname;
	}

	/**
	 * Create a sink factory with a given file name.
	 * 
	 * @param folder
	 *            directory where all files are created
	 * @param fname
	 *            file name
	 */
	public JULEventSinkFactory(String folder, String fname) {
		super(folder, fname);
	}

	@Override
	public EventSink getEventSink(String name, Properties props) {
		return getEventSink(name, props, new DefaultFormatter());
	}

	@Override
	public EventSink getEventSink(String name, Properties props, EventFormatter frmt) {
		String pattern = (fileName != null) ? fileName : (name + DEF_PATTERN + FILE_SINK_FATORY_LOG_EXT);
		File logDir = FileSystems.getDefault().getPath(logFolder).toFile();
		if (!logDir.exists()) logDir.mkdirs(); 
		pattern = FileSystems.getDefault().getPath(logFolder, pattern).toString();
		return configureSink(new JULEventSink(name, pattern, byteLimit, logCount, append, level, frmt));
	}

	@Override
	public void setConfiguration(Map<String, ?> props) throws ConfigException {
		super.setConfiguration(props);
		logCount = Utils.getInt("LogCount", props, logCount);
		byteLimit = Utils.getInt("ByteLimit", props, byteLimit);
		level = Level.parse(Utils.getString("Level", props, level.getName()));
	}

	private static void _loadConfig(String cfgFile) {
		if (!Utils.isEmpty(cfgFile)) {
			try {
				LogManager.getLogManager().readConfiguration(new FileInputStream(cfgFile));
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
}

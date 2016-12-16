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
package com.jkoolcloud.tnt4j.sink.impl.kafka;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

import com.jkoolcloud.tnt4j.core.KeyValueStats;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.format.EventFormatter;
import com.jkoolcloud.tnt4j.sink.AbstractEventSink;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.tracker.TrackingActivity;
import com.jkoolcloud.tnt4j.tracker.TrackingEvent;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * <p>
 * This class implements {@link EventSink} with Kafka as the underlying sink implementation.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 * @see OpLevel
 * @see EventFormatter
 * @see AbstractEventSink
 * @see KafkaEventSinkFactory
 */
public class KafkaEventSink extends AbstractEventSink {

	Producer<String, String> producer;
	Properties props;
	
	/**
	 * Create a Kafka event sink
	 * 
	 * @param nm event sink name (kafka topic)
	 * @param props properties for kafka sink
	 * @param evf evenr formatter associated with this sink
	 */
	public KafkaEventSink(String nm, Properties props, EventFormatter evf) {
	    super(nm, evf);
	    this.props = props;
    }

	@Override
    public boolean isSet(OpLevel sev) {
	    return true;
    }

	@Override
    public Object getSinkHandle() {
	    return producer;
    }

	@Override
    public boolean isOpen() {
	    return producer != null;
    }

	@Override
    public synchronized void open() throws IOException {
		if (producer == null) {
			producer = new KafkaProducer<String, String>(props);
		}
	}

	@Override
    public synchronized void close() throws IOException {
		producer.close();
		producer = null;
	}

	@Override
	public KeyValueStats getStats(Map<String, Object> stats) {
		super.getStats(stats);
		if (isOpen()) {
			Map<MetricName, ? extends Metric> kMetrics = producer.metrics();
			Set<MetricName> keys = kMetrics.keySet();
			for (MetricName kMetric : keys) {
				stats.put(Utils.qualify(this, kMetric.group() + "/" + kMetric.name()), kMetrics.get(kMetric).value());
			}
		}
		return this;
	}
	
	@Override
    protected void _log(TrackingEvent event) throws Exception {
		producer.send(new ProducerRecord<String, String>(getName(), 
				event.getOperation().getName(), getEventFormatter().format(event)));
	}

	@Override
    protected void _log(TrackingActivity activity) throws Exception {
		producer.send(new ProducerRecord<String, String>(getName(), 
				activity.getName(), getEventFormatter().format(activity)));
    }

	@Override
    protected void _log(Snapshot snapshot) throws Exception {
		producer.send(new ProducerRecord<String, String>(getName(), 
				snapshot.getCategory(), getEventFormatter().format(snapshot)));
    }

	@Override
    protected void _log(long ttl, Source src, OpLevel sev, String msg, Object... args) throws Exception {
		producer.send(new ProducerRecord<String, String>(getName(), getEventFormatter().format(ttl, src, sev, msg, args)));
    }

	@Override
    protected void _write(Object msg, Object... args) throws IOException, InterruptedException {
		producer.send(new ProducerRecord<String, String>(getName(), getEventFormatter().format(msg, args)));
    }
}

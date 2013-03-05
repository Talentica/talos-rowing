/*
 * Copyright (c) 2012 Tal Shalif
 * 
 * This file is part of Talos-Rowing.
 * 
 * Talos-Rowing is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Talos-Rowing is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Talos-Rowing.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.nargila.robostroke.data;

import java.util.concurrent.LinkedBlockingQueue;

import org.nargila.robostroke.RoboStroke;
import org.nargila.robostroke.RoboStrokeEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RecordDataInput extends SensorDataInputBase {
	
	// TODO extend ThreadedQueue instead of Thread
	private class OrientQueueProcessor extends Thread {
		
		private static final int ORIENT_QUEUE_SIZE = 20;

		private LinkedBlockingQueue<DataRecord> orientQueue = new LinkedBlockingQueue<DataRecord>();

		private boolean acceptData = true;
		
		private boolean stop;
		
		public OrientQueueProcessor() {
			super("Orientation Queue Processor");
		}
		
		@Override
		public void run() {
			
			while (!stop) {
				try {
					DataRecord record = orientQueue.take();
					orientationDataSource.pushData(record.timestamp, record.data);
				} catch (InterruptedException e) {
				}
			}
		}
		
		void setAcceptData(boolean acceptData) {
			
			synchronized (orientQueue) {
				this.acceptData = acceptData;
				if (!acceptData) {
					orientQueue.clear();
				}
			}
		}
		
		void add(DataRecord rec) {
			
			synchronized (orientQueue) {
				
				if (acceptData) {
					if (orientQueue.size() > ORIENT_QUEUE_SIZE) {
						logger.warn("orientQueue size overflow: {}",
								ORIENT_QUEUE_SIZE);
						orientQueue.poll();
					}
					orientQueue.offer(rec);
				}
			}
		}
		
		void abort() {
			
			setAcceptData(false);
			
			stop = true;
		}
	}
	
	private static final Logger logger = LoggerFactory.getLogger(RecordDataInput.class);
	
	
	private long currenSeekId;
	private boolean seakable;
	
	protected final RoboStroke roboStroke;
	protected final RoboStrokeEventBus bus;

	private final OrientQueueProcessor orientQueueProcessor = new OrientQueueProcessor();
	
	public RecordDataInput(RoboStroke roboStroke) {
		this.roboStroke = roboStroke;
		this.bus = roboStroke.getBus();
	}

	public void setSeakable(boolean seakable) {
		this.seakable = seakable;
	}
	
	public boolean isSeakable() {
		return seakable;
	}
	
	/**
	 * set play pos
	 * @param pos
	 */	
	public final void setPos(final double pos) {

		if (pos < 0 || pos > 1.0) {
			throw new IllegalArgumentException("pos must be a float between 0 and 1.0");
		}
		
		if (seakable) {
			
			orientQueueProcessor.setAcceptData(false);
			
			onSetPosPending(pos);

			new Thread("deffered seek job") {

				long seekId = ++currenSeekId;				

				@Override
				public void run() {

					try {
						sleep(200);

						if (seekId == currenSeekId) {
							bus.fireEvent(DataRecord.Type.REPLAY_SKIPPED, null);
							onSetPosFinish(pos);
							orientQueueProcessor.setAcceptData(true);
						}
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}.start();
		}
	}
	
	protected void onSetPosPending(double pos) {}	
	protected abstract void onSetPosFinish(double pos);	
	
	public void playRecord(DataRecord record) {
			switch (record.type) {
			case GPS:
				gpsDataSource.pushData(record.timestamp, record.data);		
				break;
			case ACCEL:
				accelerometerDataSource.pushData(record.timestamp, record.data);
				break;
			case ORIENT:
				orientQueueProcessor.add(record);
				break;
			default:				
				if (record.type.isReplayableEvent) {
					if (record.type.isBusEvent && bus != null) {
						bus.fireEvent(record);
					}					
				}
				
				break;
			}
	}

	@Override
	public void start() {
		orientQueueProcessor.start();
	}
	
	@Override
	public void stop() {
		orientQueueProcessor.abort();
	}
	
	public void playRecord(String line) {
		playRecord(line, null);
	}
	
	public void playRecord(String line, String endOfRecMark) {
		
		if (endOfRecMark != null) {
			line = line.substring(0, line.length() - endOfRecMark.length());
		}
		
		String[] vals = line.split(" +");

		if (vals.length < 3) {
			logger.warn("corrupt record line [" + line + "]");
			return;			
		}
		
		DataRecord.Type type;
		
		try {
			type = DataRecord.Type.valueOf(vals[0]);
		} catch (IllegalArgumentException e) {
			logger.warn("unknown record type [" + vals[0] + "]", e);
			return;
		}

		if (type.isParsableEvent) {		  

			DataRecord record = DataRecord.create(type, Long.parseLong(vals[1]), vals[2]);
			
			playRecord(record);
		}
	}

}
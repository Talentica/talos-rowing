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
package org.nargila.robostroke;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.channels.Channels;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nargila.robostroke.common.LocationUtil;
import org.nargila.robostroke.data.DataRecord;
import org.nargila.robostroke.data.FileDataInput;
import org.nargila.robostroke.stroke.RowingSplitMode;
import org.nargila.robostroke.way.DistanceResolver;

public class RoboStrokeTest {

	
	
	private static File inputFile;
	private static File logFile;
	
	private RoboStroke roboStroke;
	private RoboStrokeEventBus bus;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		inputFile = File.createTempFile("RoboStrokeTest-inputData-", ".txt");
//		inputFile.deleteOnExit();

		logFile = File.createTempFile("RoboStrokeTest-outputData-", ".txt");
		
		InputStream in = RoboStrokeTest.class.getResourceAsStream("100m-12-strokes-dataInput.txt");		
		
		try {
		
			FileOutputStream fout = new FileOutputStream(inputFile);
			
			try {
		
				fout.getChannel().transferFrom(Channels.newChannel(in), 0, Long.MAX_VALUE);
			} finally {
				fout.close();
			}
			
		} finally {
			in.close();
		}
		
	}

	@Before
	public void setUp() throws Exception {
		roboStroke = new RoboStroke(new DistanceResolver() {
			
			@Override
			public float calcDistance(double[] loc1, double[] loc2) {
				return (float) LocationUtil.distVincenty(loc1[0], loc1[1], loc2[0], loc2[1]); 
			}
		});
		
		bus = roboStroke.getBus();

	}

	private void start() throws IOException {
		
		roboStroke.setInput(new FileDataInput(null, inputFile));
		
		roboStroke.setDataLogger(logFile);		
	}
	
	@After
	public void tearDown() {
		roboStroke.destroy();
	}
	
	
	@Test
	public void testRowingEvents() throws Exception {
		
		File newEventsFile = File.createTempFile("testRowingEvents", ".txt");
		final Writer newEvents = new OutputStreamWriter(new FileOutputStream(newEventsFile));
				
		BusEventListener listener = new BusEventListener() {
			
			@Override
			public void onBusEvent(DataRecord event) {
				try {
					newEvents.write(String.format("%s %s", event.type, event.dataToString()));
					newEvents.write("\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};

		bus.addBusListener(listener);
		
		DataRecord event = splitRowing(RowingSplitMode.AUTO, false);

		newEvents.close();

		Object[] data = (Object[]) event.data;
		
		float distance = (Float)data[1];
		long splitTime = (Long) data[2];
		long travelTime = (Long) data[3];
		int strokes = (Integer) data[4];
		
		Assert.assertEquals(36272340992L, splitTime, 150000000);
		Assert.assertEquals(114, distance, 1);
		Assert.assertEquals(37000L, travelTime);
		Assert.assertEquals(12, strokes);
		
		
		StringWriter savedEvents = new StringWriter();
		
		String savedEventsName = "100m-12-strokes-events.txt";
		IOUtils.copy(new InputStreamReader(getClass().getResourceAsStream(savedEventsName), "UTF-8"), savedEvents);
		
		Assert.assertEquals("new events " +  newEventsFile + " do not match saved events " + savedEventsName, savedEvents.toString(), FileUtils.readFileToString(newEventsFile));

	}
	

	@Test
	public void testSplitRowingContinuousMode() throws Exception {
		
		BusEventListener listener = new BusEventListener() {
			
			@Override
			public void onBusEvent(DataRecord event) {
				switch (event.type) {
				case ROWING_COUNT:
					if ((Integer)event.data == 12) {
						roboStroke.getParameters().setParam(ParamKeys.PARAM_ROWING_MODE, RowingSplitMode.AUTO + "");
					}

					break;
				}
			}
		};
		
		bus.addBusListener(listener);
		DataRecord event;
		
		try {							
			
			event = splitRowing(RowingSplitMode.CONTINUOUS, false);
		
		} finally {
			bus.removeBusListener(listener);
		}
		
		/* ROWING_STOP 169103868297216 169098851909632 114.224663 36272340992 37000 12 */
		Object[] data = (Object[]) event.data;
		
		float distance = (Float)data[1];
		long splitTime = (Long) data[2];
		long travelTime = (Long) data[3];
		int strokes = (Integer) data[4];
		
		Assert.assertEquals(49861885952L, splitTime, 150000000);
		Assert.assertEquals(125, distance, 1);
		Assert.assertEquals(48000L, travelTime);
		Assert.assertEquals(12, strokes);
		
	}
	

	@Test
	public void testStrokeRate() throws Exception {
		
		final int[] strokeRate = {0, 0}; // count, accum 
		
		
		BusEventListener listener = new BusEventListener() {
			boolean hasPower;
			
			@Override
			public void onBusEvent(DataRecord event) {
				switch (event.type) {
				case STROKE_POWER_END:
					hasPower = (Float)event.data > 0;

					break;
				case STROKE_RATE:
					if (hasPower) {
						strokeRate[0]++;
						strokeRate[1] += (Integer)event.data;
					}
					break;
				}
			}
		};
		
		bus.addBusListener(listener);
		
		try {					
			splitRowing(RowingSplitMode.AUTO, true);
			
			Assert.assertEquals(8, strokeRate[0]);
			Assert.assertEquals(19.25, (strokeRate[1] / (double)strokeRate[0]), 0.25);
			
		} finally {
			bus.removeBusListener(listener);
		}
	}
	
	@Test
	public void testSplitRowing() throws Exception {
		DataRecord event = splitRowing(RowingSplitMode.AUTO, false);
		
		/* ROWING_STOP 169103868297216 169098851909632 114.224663 36272340992 37000 12 */
		Object[] data = (Object[]) event.data;
		
		float distance = (Float)data[1];
		long splitTime = (Long) data[2];
		long travelTime = (Long) data[3];
		int strokes = (Integer) data[4];
		
		Assert.assertEquals(36272340992L, splitTime, 150000000);
		Assert.assertEquals(114, distance, 1);
		Assert.assertEquals(37000L, travelTime);
		Assert.assertEquals(12, strokes);
		
	}
	

	@Test
	public void testSplitRowingStraight() throws Exception {
		DataRecord event = splitRowing(RowingSplitMode.AUTO, true);
		
		Object[] data = (Object[]) event.data;
		
		float distance = (Float)data[1];
		long splitTime = (Long) data[2];
		long travelTime = (Long) data[3];
		int strokes = (Integer) data[4];
		
		Assert.assertEquals(36272340992L, splitTime, 150000000);
		Assert.assertEquals(114, distance, 1);
		Assert.assertEquals(37000L, travelTime);
		Assert.assertEquals(12, strokes);
	}
	
	private DataRecord splitRowing(RowingSplitMode mode, boolean straightMode) throws Exception {
		return splitRowing(mode, straightMode, null);
	}
	
	private DataRecord splitRowing(RowingSplitMode mode, boolean straightMode, final BusEventListener testEventListener) throws Exception {
		
		final AtomicReference<DataRecord> startEvent = new AtomicReference<DataRecord>();
		final AtomicReference<DataRecord> stopEvent = new AtomicReference<DataRecord>();
		
		BusEventListener listener = new BusEventListener() {
			
			@Override
			public void onBusEvent(DataRecord event) {
				System.out.println(event);
				
				if (null != testEventListener) {
					testEventListener.onBusEvent(event);
				}
				
				switch (event.type) {
				case ROWING_START:
					synchronized (startEvent) {
						startEvent.set(event);
						startEvent.notify();
					}
					break;
				case ROWING_STOP:
					synchronized (stopEvent) {
						stopEvent.set(event);
						stopEvent.notify();
					}
					break;
				}
				
				synchronized (startEvent) {
					startEvent.set(event);
					startEvent.notify();
				}
			}
		};
		
		try {
			bus.addBusListener(listener);
			roboStroke.getParameters().setParam(ParamKeys.PARAM_ROWING_STRAIGHT_LINE_MODE, straightMode);
			roboStroke.getParameters().setParam(ParamKeys.PARAM_ROWING_MODE, mode.name());
			start();
			
			synchronized (startEvent) {				
								
				startEvent.wait(20000);
				
				Assert.assertNotNull("no start event after 20 seconds", startEvent.get());
			}
				
			synchronized (stopEvent) {
					
				if (stopEvent.get() == null) {
				
					stopEvent.wait(60000);
					
					Assert.assertNotNull("no stop event after 1 minutes", stopEvent.get());
				}
				
			} 
			
			
			Thread.sleep(1000); // allow enough time for bus to log event to record file
			
		} finally {
			bus.removeBusListener(listener);
		}
		
		return stopEvent.get();
	}
}

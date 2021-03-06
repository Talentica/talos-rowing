/*
 * Copyright (c) 2011 Tal Shalif
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

package com.talentica.rowingapp.ui.graph;

import com.talentica.rowingapp.common.data.DataIdx;
import com.talentica.rowingapp.common.data.SensorDataSink;
import com.talentica.rowingapp.common.data.SensorDataSource;
import com.talentica.rowingapp.common.data.stroke.AppStroke;
import com.talentica.rowingapp.common.filter.LowpassFilter;
import com.talentica.rowingapp.ui.utils.PaintStyle;
import com.talentica.rowingapp.ui.utils.RSCanvas;
import com.talentica.rowingapp.ui.utils.RSPaint;
import com.talentica.rowingapp.ui.utils.RSRect;
import com.talentica.rowingapp.ui.utils.UILiaison;

/**
 * subclass of LineGraphView for setting acceleration specific parameters
 */
public class AccellGraph extends SensorGraphBase  {
	/**
	 * subclass of LineGraphView for setting stroke specific parameters
	 */
	private class RollGraphOverlay implements SensorDataSink {
		private static final double ROLL_PANNEL_DIM_FACTOR = 0.60;

		private static final float Y_RANGE = 10f;

		private final int rollAccumSize = 2;
		private int rollAccumCount;
		private float rollAccum;

		private final LowpassFilter filter = new LowpassFilter(.5f);

		private long rollAccumTimestamp;

		private final MultiXYSeries multySeries;
		private final XYSeries rollSeries;
		private final CyclicArrayXYSeries rollPanelSeries;
		private final RSPaint rollGraphPaint;
		
		private final RSPaint rollBackgroundPaint;

		private final CyclicArrayXYSeries rollSeriesImpl;

		RollGraphOverlay(double xRange, MultiXYSeries multySeries) {

			rollPanelSeries = new CyclicArrayXYSeries(XYSeries.XMode.ROLLING, new XYSeries.Renderer(uiLiaison.createPaint()));
			rollPanelSeries.setxRange(xRange);

			this.multySeries = multySeries;
			
			{
				rollBackgroundPaint = uiLiaison.createPaint();
				rollBackgroundPaint.setStyle(PaintStyle.FILL);
				rollBackgroundPaint.setAntiAlias(false);
				rollBackgroundPaint.setStrokeWidth(0);
			}

			{
				rollGraphPaint = uiLiaison.createPaint(); 
				rollGraphPaint.setStyle(PaintStyle.STROKE);
				rollGraphPaint.setColor(uiLiaison.getYellowColor());
				rollGraphPaint.setAlpha(170);
			}
			
			{
				rollSeriesImpl = new CyclicArrayXYSeries(XYSeries.XMode.ROLLING, new XYSeries.Renderer(rollGraphPaint, null));
				rollSeriesImpl.setIndependantYAxis(true);
				rollSeriesImpl.setyAxisSize(Y_RANGE);
			}

			rollSeries = multySeries.addSeries(rollSeriesImpl);
		}

		void setXRange(double val) {
			rollPanelSeries.setxRange(val);
		}

		void drawRollPanels(RSCanvas canvas, RSRect rect, double xAxisSize) {
			XYSeries ser = rollPanelSeries;

			final int len = ser.getItemCount();

			if (len > 0) {
				final int red = uiLiaison.getRedColor();
				final int green = uiLiaison.getGreenColor();

				final double maxYValue = Y_RANGE / 2;
				final double scaleX = rect.width() / xAxisSize;

				final double minX = multySeries.getMinX();

				double startX = ser.getX(0);
				double stopX;

				for (int i = 1; i < len; ++i, startX = stopX) {
					stopX = ser.getX(i);

					double avgY = Math.min(ser.getY(i), maxYValue);

					int color = avgY > 0 ? green : red;
					int alpha = (int) ((avgY / maxYValue) * 255 * ROLL_PANNEL_DIM_FACTOR);

					rollBackgroundPaint.setColor(color);
					rollBackgroundPaint.setAlpha(Math.min(Math.abs(alpha), 255));

					float left = (float) ((startX - minX) * scaleX);
					float right = (float) (((stopX - minX) * scaleX));

					canvas.drawRect((int)left, rect.top, (int)right, rect.bottom, rollBackgroundPaint);
				}
			}
		}

		void reset() {
			synchronized (multySeries) {
				resetRollAccum();
				rollPanelSeries.clear();
			}
		}

		private void resetRollAccum() {
			rollAccum = 0;
			rollAccumCount = 0;
		}

		@Override
		public void onSensorData(long timestamp, Object value) {
			
			synchronized (multySeries) {
				float[] values = (float[]) value;

				float y = filter
				.filter(new float[] { values[DataIdx.ORIENT_ROLL] })[0];

				rollAccum += y;

				if (rollAccumCount++ == 0) {
					rollAccumTimestamp = timestamp;
				}

				if (rollAccumCount == rollAccumSize) {
					rollPanelSeries.add(rollAccumTimestamp, rollAccum
							/ rollAccumSize);
					resetRollAccum();
				}

				rollSeries.add(timestamp, y);
			}
			
		}
	}

	private static final float Y_SCALE = 8f;
	private final RollGraphOverlay rollGraph;

	public AccellGraph(UILiaison factory, float xRange, AppStroke roboStroke) {
		super(factory, XYSeries.XMode.ROLLING, xRange, Y_SCALE, roboStroke);

		rollGraph = new RollGraphOverlay(xRange, multySeries);
	}

	@Override
	public void setXRange(double val) {
		rollGraph.setXRange(val);
		super.setXRange(val);
	}

	@Override
	protected void drawGraph(RSCanvas canvas, RSRect rect, double xAxisSize,
							 double yAxisSize) {

		rollGraph.drawRollPanels(canvas, rect, xAxisSize);

		super.drawGraph(canvas, rect, xAxisSize, yAxisSize);
	}

	@Override
	public void reset() {
		synchronized (multySeries) {
			rollGraph.reset();

			super.reset();
		}
	}

	@Override
	protected void detachSensors(SensorDataSink lineDataSink) {
		getAccelerationSource().removeSensorDataSink(lineDataSink);
		getOrientationSource().removeSensorDataSink(rollGraph);
	}

	@Override
	protected void attachSensors(SensorDataSink lineDataSink) {
		getAccelerationSource().addSensorDataSink(lineDataSink);
		getOrientationSource().addSensorDataSink(rollGraph);
	}

	protected SensorDataSource getOrientationSource() {
		return roboStroke.getOrientationSource();
	}

	protected SensorDataSource getAccelerationSource() {
		return roboStroke.getAccelerationSource();
	}
}
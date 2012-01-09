package org.nargila.robostroke.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;

public class SwingPaint implements RSPaint {

	boolean antiAlias;
	
	Color color = Color.BLACK;

	Stroke stroke = new BasicStroke(1);

	PaintStyle paintStyle;
	
	public void setARGB(int a, int r, int g, int b) {
		color = new Color(r, g, b, a);		
	}

	public void setAlpha(int abs) {
		setARGB(abs, color.getRed(), color.getGreen(), color.getBlue());
	}

	public void setAntiAlias(boolean antiAlias) {
		this.antiAlias = antiAlias;
	}

	public void setColor(int color) {
		this.color = new Color(color);
	}

	public void setStrokeWidth(float width) {
		this.stroke = new BasicStroke(width);

	}

	public Stroke getStroke() {
		return stroke;
	}
	
	public void setStyle(PaintStyle style) {
		this.paintStyle = style;

	}

}
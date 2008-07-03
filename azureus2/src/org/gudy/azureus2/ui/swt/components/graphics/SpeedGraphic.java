/*
 * File    : SpeedGraphic.java
 * Created : 15 d�c. 2003}
 * By      : Olivier
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.gudy.azureus2.ui.swt.components.graphics;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;

/**
 * @author Olivier
 *
 */
public class SpeedGraphic extends ScaledGraphic implements ParameterListener {    
  
  private static final int	ENTRIES	= 2000;
  
  public static final int COLOR_AVERAGE = 0;
  public static final int COLOR_MAINSPEED = 1;
  public static final int COLOR_OVERHEAD = 2;
  public static final int COLOR_LIMIT = 3;
  public static final int COLOR_OTHERS = 4;
  public static final int COLOR_TRIMMED = 5;
  
  private static final int ALPHA_FOCUS = 200;
  private static final int ALPHA_NOFOCUS = 150;
  
  public Color[] colors = new Color[] {
  	Colors.red, Colors.blues[Colors.BLUES_MIDDARK], Colors.colorInverse, Colors.blue, Colors.grey,
  	Colors.light_grey
  };
  
  private int internalLoop;
  private int graphicsUpdate;
  private Point oldSize;
  
  protected Image bufferImage;
  
  private int nbValues = 0;
  
  private int[][] all_values	= new int[1][ENTRIES];
  private int currentPosition;
  
  private int alpha = 255;
  
  private boolean autoAlpha = false;
  
  
  private SpeedGraphic(Scale scale,ValueFormater formater) {
    super(scale,formater);
    
    currentPosition = 0;
    
    COConfigurationManager.addParameterListener("Graphics Update",this);
    parameterChanged("Graphics Update");
  }
  
  public void initialize(Canvas canvas) {
  	super.initialize(canvas);
  	
  	canvas.addMouseTrackListener(new MouseTrackListener() {
			public void mouseHover(MouseEvent e) {
			}
		
			public void mouseExit(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_NOFOCUS);
				}
			}
		
			public void mouseEnter(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_FOCUS);
				}
			}
		});
  	
  	drawCanvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				if (bufferImage != null && !bufferImage.isDisposed()) {
					Rectangle bounds = bufferImage.getBounds();
					if (bounds.width >= e.width && bounds.height >= e.height) {
						if (alpha != 255) {
							try {
								e.gc.setAlpha(alpha);
						  } catch (Exception ex) {
						  	// Ignore ERROR_NO_GRAPHICS_LIBRARY error or any others
						  }
						}
						e.gc.drawImage(bufferImage, e.x, e.y, e.width, e.height, e.x, e.y,
								e.width, e.height);
					}
				}
			}
		});
  	
  	drawCanvas.addListener(SWT.Resize, new Listener() {
			public void handleEvent(Event event) {
				drawChart(true);
			}
		});
  }
  
  public static SpeedGraphic getInstance() {
    return new SpeedGraphic(new Scale(),new ValueFormater() {
      public String format(int value) {
        return DisplayFormatters.formatByteCountToBase10KBEtcPerSec(value);
      }
    });
  }
  
  public void addIntsValue(int[] new_values) {  	
    try{
    	this_mon.enter();
    
    	if ( all_values.length < new_values.length ){
    		
    		int[][]	new_all_values = new int[new_values.length][];
    		
    		for (int i=0;i<all_values.length;i++){
    		
    			new_all_values[i] = all_values[i];
    		}
    		
    		for (int i=all_values.length;i<new_all_values.length; i++ ){
    			
    			new_all_values[i] = new int[ENTRIES];
    		}
    		
    		all_values = new_all_values;
    	}
    		    
	    for (int i=0;i<new_values.length;i++){
    		
	        all_values[i][currentPosition] = new_values[i];
    	}
	  	
	    currentPosition++;
	    
	    if(nbValues < ENTRIES){
	    	
	      nbValues++;
	    }
	    
	    currentPosition %= ENTRIES;

	    
    }finally{
    	
    	this_mon.exit();
    }
  }
  
  public void addIntValue(int value) {
	  addIntsValue( new int[]{ value });
  }
  
  public void refresh() {
    if(drawCanvas == null || drawCanvas.isDisposed())
      return;
    
    Rectangle bounds = drawCanvas.getClientArea();
    if(bounds.height < 30 || bounds.width  < 100 || bounds.width > 2000 || bounds.height > 2000)
      return;
    
    boolean sizeChanged = (oldSize == null || oldSize.x != bounds.width || oldSize.y != bounds.height);
    oldSize = new Point(bounds.width,bounds.height);
    
    internalLoop++;
    if(internalLoop > graphicsUpdate)
      internalLoop = 0;
    
    
    if(internalLoop == 0 || sizeChanged) {
	    drawChart(sizeChanged);
    }
    
    drawCanvas.redraw();
    drawCanvas.update();
  }
  
  protected void drawChart(boolean sizeChanged) {
		if (drawCanvas == null || drawCanvas.isDisposed() || !drawCanvas.isVisible())
		{
			return;
		}
		GC gcImage = null;
		try
		{
			this_mon.enter();
			
			drawScale(sizeChanged);
			
			if (bufferScale == null || bufferScale.isDisposed())
				return;

			Rectangle bounds = drawCanvas.getClientArea();
			if (bounds.isEmpty())
				return;

			
			//If bufferedImage is not null, dispose it
			if (bufferImage != null && !bufferImage.isDisposed())
				bufferImage.dispose();
			
			bufferImage = new Image(drawCanvas.getDisplay(), bounds);
			gcImage = new GC(bufferImage);
			gcImage.drawImage(bufferScale, 0, 0);
			
			int oldAverage = 0;
			int[] oldTargetValues = new int[all_values.length];
			
			int[] maxs = new int[all_values.length];
			
			for (int x = 0; x < bounds.width - 71; x++)
			{
				int position = currentPosition - x - 1;
				if (position < 0)
				{
					position += 2000;
					if (position < 0)
					{
						position = 0;
					}
				}
				for (int chartIdx = 0; chartIdx < all_values.length; chartIdx++)
				{
					int value = all_values[chartIdx][position];
					if (value > maxs[chartIdx])
					{
						maxs[chartIdx] = value;
					}
				}
			}
			
			int max = maxs[0];
			int max_primary = max;
			
			for (int i = 1; i < maxs.length; i++)
			{
				int m = maxs[i];
				if (i == 1)
				{
					if (max < m)
					{
						max = m;
						max_primary = max;
					}
				} else
				{
					// trim secondary indicators so we don't loose the more important info 
					if (max < m)
					{
						if (m <= 2 * max_primary)
						{
							max = m;
						} else
						{
							max = 2 * max_primary;
							break;
						}
					}
				}
			}
			
			scale.setMax(max);
			int maxHeight = scale.getScaledValue(max);
			
			Color background = colors[COLOR_MAINSPEED]; // Colors.blues[Colors.BLUES_DARKEST];
			Color foreground = colors[COLOR_MAINSPEED]; //Colors.blues[Colors.BLUES_MIDLIGHT];
			
			for (int x = 0; x < bounds.width - 71; x++)
			{
				int position = currentPosition - x - 1;
				if (position < 0)
				{
					position += 2000;
					if (position < 0)
					{
						position = 0;
					}
				}
				int xDraw = bounds.width - 71 - x;
				int height = scale.getScaledValue(all_values[0][position]);

				gcImage.setForeground(background);
				gcImage.setBackground(foreground);
				gcImage.setClipping(xDraw, bounds.height - 1 - height, 1, height);
				gcImage.fillGradientRectangle(xDraw, bounds.height - 1 - maxHeight, 1, maxHeight, true);
				gcImage.setClipping(0, 0, bounds.width, bounds.height);
				if(all_values.length > 1)
				{
					gcImage.setForeground(colors[COLOR_OVERHEAD]);
					height = scale.getScaledValue(all_values[1][position]);
					Utils.drawStriped(gcImage, xDraw, bounds.height - 1 - height, 1, height, 1, currentPosition, false);
				}
				for (int chartIdx = 2; chartIdx < all_values.length; chartIdx++)
				{
					int targetValue = all_values[chartIdx][position];
					int oldTargetValue = oldTargetValues[chartIdx];
					if (x > 1 && (chartIdx == 2 && (targetValue > 0 && oldTargetValue > 0) || (chartIdx > 2 && (targetValue > 0 || oldTargetValue > 0))))
					{
						int trimmed = 0;
						if (targetValue > max)
						{
							targetValue = max;
							trimmed++;
						}
						if (oldTargetValue > max)
						{
							oldTargetValue = max;
							trimmed++;
						}
						if (trimmed < 2 || trimmed == 2 && position % 3 == 0)
						{
							int h1 = bounds.height - scale.getScaledValue(targetValue) - 2;
							int h2 = bounds.height - scale.getScaledValue(oldTargetValue) - 2;
							gcImage.setForeground(chartIdx == 2 ? colors[COLOR_LIMIT] : (trimmed > 0 ? colors[COLOR_TRIMMED] : colors[COLOR_OTHERS]));
							gcImage.drawLine(xDraw, h1, xDraw + 1, h2);
						}
					}
					oldTargetValues[chartIdx] = all_values[chartIdx][position];
				}
				int average = computeAverage(position);
				if (x > 6)
				{
					int h1 = bounds.height - scale.getScaledValue(average) - 2;
					int h2 = bounds.height - scale.getScaledValue(oldAverage) - 2;
					gcImage.setForeground(colors[COLOR_AVERAGE]);
					gcImage.drawLine(xDraw, h1, xDraw + 1, h2);
				}
				oldAverage = average;
			}
			
			if (nbValues > 0)
			{
				int height = bounds.height - scale.getScaledValue(computeAverage(currentPosition - 6)) - 2;
				gcImage.setForeground(colors[COLOR_AVERAGE]);
				gcImage.drawText(formater.format(computeAverage(currentPosition - 6)), bounds.width - 65, height - 12, true);
			}
			
		} catch (Exception e)
		{
			Debug.out("Warning", e);
		} finally
		{
			if (gcImage != null)
			{
				gcImage.dispose();
			}
			this_mon.exit();
		}
	}
  
  protected int computeAverage(int position) {
    long sum = 0;
    for(int i = -5 ; i < 6 ; i++) {
      int pos = position + i;
      pos %= ENTRIES;
      if (pos < 0)
        pos += 2000;
      sum += all_values[0][pos];
    }
    return(int)(sum / 11);
    
  }
  
  public void parameterChanged(String parameter) {
    graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");
  }
  
  public void dispose() {
    super.dispose();
    if(bufferImage != null && ! bufferImage.isDisposed()) {
      bufferImage.dispose();
    }
    COConfigurationManager.removeParameterListener("Graphics Update",this);
  }

    private int getAlpha() {
		return alpha;
	}

	public void setAlpha(int alpha) {
		this.alpha = alpha;
		if (drawCanvas != null && !drawCanvas.isDisposed()) {
			drawCanvas.redraw();
		}
	}

    private boolean isAutoAlpha() {
		return autoAlpha;
	}

    private void setAutoAlpha(boolean autoAlpha) {
		this.autoAlpha = autoAlpha;
		if (autoAlpha) {
			setAlpha(drawCanvas.getDisplay().getCursorControl() == drawCanvas ? ALPHA_FOCUS : ALPHA_NOFOCUS);
		}
	}
	
	public void setLineColors(Color average, Color speed, Color overhead, Color limit, Color others, Color trimmed) {
		if (average != null) {
			colors[COLOR_AVERAGE] = average;
		}
		if (speed != null) {
			colors[COLOR_MAINSPEED] = speed;
		}
		if (overhead != null) {
			colors[COLOR_OVERHEAD] = overhead;
		}
		if (limit != null) {
			colors[COLOR_LIMIT] = limit;
		}
		if (others != null) {
			colors[COLOR_OTHERS] = others;
		}
		if (trimmed != null) {
			colors[COLOR_TRIMMED] = trimmed;
		}
		if (drawCanvas != null && !drawCanvas.isDisposed()) {
			drawCanvas.redraw();
		}
	}
	
	public void setLineColors(Color[] newChangeableColorSet) {
		colors = newChangeableColorSet;
		if (drawCanvas != null && !drawCanvas.isDisposed()) {
			drawCanvas.redraw();
		}
	}
}

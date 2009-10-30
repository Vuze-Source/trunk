/**
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */
package org.gudy.azureus2.ui.swt.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.disk.DiskManagerPiece;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.peer.PEPiece;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.views.utils.CoordinateTransform;

import com.aelitis.azureus.core.peermanager.piecepicker.PiecePicker;

/**
 * @author Aaron Grunthal
 * @create 02.10.2007
 */
public abstract class PieceDistributionView
	extends AbstractIView
	implements IViewExtension
{
	private Composite		comp;
	private Canvas			pieceDistCanvas;
	protected PEPeerManager	pem;
	// list of pieces that the data source has, won't be used if isMe is true
	protected boolean[]		hasPieces;
	// field must be set to true to display data that we know about ourselves
	// instead of remote peers
	protected boolean		isMe		= false;
	private boolean			initialized	= false;
	private Image imgToPaint = null;
	
	/**
	 * implementors of this method must provide an appropriate peer manager and
	 * possibly provide the hasPieces array for pieces the data source has
	 */
	abstract public void dataSourceChanged(Object newDataSource);

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.AbstractIView#getData()
	 */
	public String getData() {
		return "PiecesView.DistributionView.title";
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.AbstractIView#initialize(org.eclipse.swt.widgets.Composite)
	 */
	public void initialize(Composite parent) {
		comp = new Composite(parent,SWT.NONE);
		createPieceDistPanel();
		initialized = true;
		refresh();
	}

	private void createPieceDistPanel() {
		comp.setLayout(new FillLayout());
		//pieceDistComposite = new Composite(parent, SWT.NONE);
		pieceDistCanvas = new Canvas(comp,SWT.NONE);
		pieceDistCanvas.addListener(SWT.Paint, new Listener() {
			public void handleEvent(Event event) {
				if (imgToPaint != null && !imgToPaint.isDisposed()) {
					event.gc.drawImage(imgToPaint, 0, 0);
				}
			}
		});
	}

	private final void updateDistribution()
	{
		if (!initialized || pem == null || comp == null
				|| pem.getPiecePicker() == null || pem.getDiskManager() == null
				|| !comp.isVisible())
			return;
		Rectangle rect = pieceDistCanvas.getBounds();
		if (rect.height <= 0 || rect.width <= 0)
			return;
		
		PiecePicker picker = pem.getPiecePicker();
		
		final int seeds = pem.getNbSeeds() + (pem.isSeeding() ? 1 : 0);
		final int connected = pem.getNbPeers() + seeds + (pem.isSeeding() ? 0 : 1);
		final int upperBound = 1 + (1 << (int) Math.ceil(Math.log(connected + 0.0) / Math.log(2.0)));
		// System.out.println("conn:"+connected+" bound:"+upperBound);
		final int minAvail = (int) picker.getMinAvailability();
		final int maxAvail = picker.getMaxAvailability();
		final int nbPieces = picker.getNumberOfPieces();
		final int[] availabilties = picker.getAvailability();
		final DiskManagerPiece[] dmPieces = pem.getDiskManager().getPieces();
		final PEPiece[] pePieces = pem.getPieces();
		final int[] globalPiecesPerAvailability = new int[upperBound];
		final int[] datasourcePiecesPerAvailability = new int[upperBound];
		
		// me-only stuff
		final boolean[] downloading = new boolean[upperBound];
		
		int avlPeak = 0;
		int avlPeakIdx = -1;
		
		for (int i = 0; i < nbPieces; i++)
		{
			if (availabilties[i] >= upperBound)
				return; // availability and peer lists are OOS, just wait for the next round
			final int newPeak;
			if (avlPeak < (newPeak = ++globalPiecesPerAvailability[availabilties[i]]))
			{
				avlPeak = newPeak;
				avlPeakIdx = availabilties[i];
			}
			if ((isMe && dmPieces[i].isDone()) || (!isMe && hasPieces != null && hasPieces[i]))
				++datasourcePiecesPerAvailability[availabilties[i]];
			if (isMe && pePieces[i] != null)
				downloading[availabilties[i]] = true;
		}
		
		Image img = new Image(comp.getDisplay(),pieceDistCanvas.getBounds());
		
		GC gc = new GC(img);

		try
		{
			int stepWidthX = (int) Math.floor(rect.width / upperBound);
			int barGap = 1;
			int barWidth = stepWidthX - barGap - 1;
			int barFillingWidth = barWidth - 1;
			double stepWidthY = 1.0 * (rect.height - 1) / avlPeak;
			int offsetY = rect.height;
			
			gc.setForeground(Colors.green);
			for (int i = 0; i <= connected; i++)
			{
				Color curColor;
				if (i == 0)
					curColor = Colors.colorError;
				else if (i <= seeds)
					curColor = Colors.green;
				else
					curColor = Colors.blues[Colors.BLUES_DARKEST];

					
				gc.setBackground(curColor);
				gc.setForeground(curColor);
				
				if(globalPiecesPerAvailability[i] == 0)
				{
					gc.setLineWidth(2);
					gc.drawLine(stepWidthX * i, offsetY - 1, stepWidthX * (i + 1) - barGap, offsetY - 1);
				} else
				{
					gc.setLineWidth(1);
					if (downloading[i])
						gc.setLineStyle(SWT.LINE_DASH);
					gc.fillRectangle(stepWidthX * i + 1, offsetY - 1, barFillingWidth, (int) (Math.ceil(stepWidthY * datasourcePiecesPerAvailability[i] - 1) * -1));
					gc.drawRectangle(stepWidthX * i, offsetY, barWidth, (int) (Math.ceil(stepWidthY * globalPiecesPerAvailability[i]) + 1) * -1);
				}

				if(i==minAvail)
				{
					gc.setForeground(Colors.blue);
					gc.drawRectangle(stepWidthX*i+1, offsetY-1, barWidth-2, (int)(Math.ceil(stepWidthY*globalPiecesPerAvailability[i]-1))*-1);
				}
				
				
				gc.setLineStyle(SWT.LINE_SOLID);
			}
			gc.setLineWidth(1);
			
			
			CoordinateTransform t = new CoordinateTransform(rect);
			t.shiftExternal(rect.width,0);
			t.scale(-1.0, 1.0);
			
			String[] boxContent = new String[] {
				MessageText.getString("PiecesView.DistributionView.NoAvl"),
				MessageText.getString("PiecesView.DistributionView.SeedAvl"),
				MessageText.getString("PiecesView.DistributionView.PeerAvl"),
				MessageText.getString("PiecesView.DistributionView.RarestAvl",new String[] {globalPiecesPerAvailability[minAvail]+"",minAvail+""}),
				MessageText.getString("PiecesView.DistributionView."+ (isMe? "weHave" : "theyHave")),
				MessageText.getString("PiecesView.DistributionView.weDownload")				
				};

			int charWidth = gc.getFontMetrics().getAverageCharWidth();
			int charHeight = gc.getFontMetrics().getHeight();
			int maxBoxOffsetY = charHeight + 2;
			int maxBoxWidth = 0;
			int maxBoxOffsetX = 0;
			for (int i = 0; i < boxContent.length; i++)
				maxBoxWidth = Math.max(maxBoxWidth, boxContent[i].length());
			
			maxBoxOffsetX = (maxBoxWidth+5) * charWidth;
			maxBoxWidth = ++maxBoxWidth * charWidth;
			
			
			int boxNum = 1;
			gc.setForeground(Colors.colorError);
			gc.setBackground(Colors.background);
			gc.drawRectangle(t.x(maxBoxOffsetX),t.y(maxBoxOffsetY*boxNum),maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],t.x(maxBoxOffsetX-5),t.y(maxBoxOffsetY*boxNum),true);
			
			boxNum++;
			gc.setForeground(Colors.green);
			gc.setBackground(Colors.background);
			gc.drawRectangle(t.x(maxBoxOffsetX),t.y(maxBoxOffsetY*boxNum),maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],t.x(maxBoxOffsetX-5),t.y(maxBoxOffsetY*boxNum),true);
			
			boxNum++;
			gc.setForeground(Colors.blues[Colors.BLUES_DARKEST]);
			gc.drawRectangle(t.x(maxBoxOffsetX),t.y(maxBoxOffsetY*boxNum),maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],t.x(maxBoxOffsetX-5),t.y(maxBoxOffsetY*boxNum),true);
			
			boxNum++;
			gc.setForeground(Colors.blue);
			gc.drawRectangle(t.x(maxBoxOffsetX),t.y(maxBoxOffsetY*boxNum),maxBoxWidth,charHeight);
			gc.drawString(boxContent[boxNum-1],t.x(maxBoxOffsetX-5),t.y(maxBoxOffsetY*boxNum),true);
			
			boxNum++;
			gc.setForeground(Colors.black);
			gc.setBackground(Colors.black);
			gc.drawRectangle(t.x(maxBoxOffsetX),t.y(maxBoxOffsetY*boxNum),maxBoxWidth,charHeight);
			gc.fillRectangle(t.x(maxBoxOffsetX),t.y(maxBoxOffsetY*boxNum),maxBoxWidth/2,charHeight);
			gc.setForeground(Colors.grey);
			gc.setBackground(Colors.background);
			gc.drawString(boxContent[boxNum-1],t.x(maxBoxOffsetX-5),t.y(maxBoxOffsetY*boxNum),true);
			
			if(isMe)
			{
				boxNum++;
				gc.setForeground(Colors.black);
				gc.setLineStyle(SWT.LINE_DASH);
				gc.drawRectangle(t.x(maxBoxOffsetX),t.y(maxBoxOffsetY*boxNum),maxBoxWidth,charHeight);
				gc.drawString(boxContent[boxNum-1],t.x(maxBoxOffsetX-5),t.y(maxBoxOffsetY*boxNum),true);				
			}
			
			gc.setLineStyle(SWT.LINE_SOLID);
		
		} finally
		{
			gc.dispose();
		}
		
		if (imgToPaint != null) {
			imgToPaint.dispose();
		}
		imgToPaint = img;
		pieceDistCanvas.redraw();
	}
	
	public void refresh() {
		if (!initialized || pem == null)
			return;
		updateDistribution();
		super.refresh();
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.AbstractIView#getComposite()
	 */
	public Composite getComposite() {
		return comp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.gudy.azureus2.ui.swt.views.AbstractIView#delete()
	 */
	public void delete() {
		if (!initialized)
			return;
		initialized = false;
		Utils.disposeSWTObjects(new Object[] { pieceDistCanvas, comp, imgToPaint });
		super.delete();
	}
	
	public Menu getPrivateMenu() {
		return null;
	}
	
	public void viewActivated() {
		updateDistribution();
	}
	
	public void viewDeactivated() {
		Utils.disposeSWTObjects(new Object[] { imgToPaint });
	}
}

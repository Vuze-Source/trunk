package com.aelitis.azureus.ui.swt.views.skin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.ui.swt.ImageRepository;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.shells.GCStringPrinter;
import org.gudy.azureus2.ui.swt.shells.InputShell;

import com.aelitis.azureus.activities.VuzeActivitiesEntry;
import com.aelitis.azureus.buddy.impl.VuzeBuddyManager;
import com.aelitis.azureus.login.NotLoggedInException;
import com.aelitis.azureus.ui.skin.SkinConstants;
import com.aelitis.azureus.ui.swt.UIFunctionsManagerSWT;
import com.aelitis.azureus.ui.swt.UIFunctionsSWT;
import com.aelitis.azureus.ui.swt.buddy.VuzeBuddySWT;
import com.aelitis.azureus.util.LoginInfoManager;

public class AvatarWidget
{
	private Canvas canvas = null;

	private BuddiesViewer viewer = null;

	private Composite parent = null;

	private int highlightBorder = 1;

	private int imageBorder = 1;

	private Point imageSize = null;

	private Point size = null;

	private Point nameAreaSize = null;

	private Rectangle imageBounds = null;

	private Rectangle nameAreaBounds = null;

	private VuzeBuddySWT vuzeBuddy = null;

	private boolean isActivated = false;

	private boolean isSelected = false;

	private boolean nameLinkActive = false;

	private Color textColor = null;

	private Color textLinkColor = null;

	private Color imageBorderColor = null;

	private Rectangle decoratorBounds = null;

	private int alpha = 255;

	private boolean sharedAlready = false;

	private Image image = null;

	private Rectangle sourceImageBounds = null;

	private Menu menu;

	private static Font fontDisplayName;

	public AvatarWidget(BuddiesViewer viewer, Point avatarSize,
			Point avatarImageSize, Point avatarNameSize, VuzeBuddySWT vuzeBuddy) {

		if (null == viewer || null == vuzeBuddy) {
			throw new NullPointerException(
					"The variable 'viewer' and 'vuzeBuddy' can not be null");
		}

		this.viewer = viewer;

		if (null == viewer.getControl() || true == viewer.getControl().isDisposed()) {
			throw new NullPointerException(
					"The given 'viewer' is not properly initialized");
		}

		this.parent = viewer.getControl();
		this.size = avatarSize;
		this.imageSize = avatarImageSize;
		this.nameAreaSize = avatarNameSize;
		this.vuzeBuddy = vuzeBuddy;
		canvas = new Canvas(parent, SWT.NONE);
		canvas.setData("AvatarWidget", this);

		init();
	}

	private void init() {

		final Image removeImage = ImageRepository.getImage("progress_remove");
		final Image add_to_share_Image = ImageRepository.getImage("add_to_share");

		/*
		 * Centers the image and name horizontally
		 */
		imageBounds = new Rectangle((size.x / 2) - (imageSize.x / 2), 4,
				imageSize.x, imageSize.y);

		nameAreaBounds = new Rectangle((size.x / 2) - (nameAreaSize.x / 2),
				imageBounds.y + imageBounds.height, nameAreaSize.x, nameAreaSize.y);

		/*
		 * Position the decorator icons
		 */
		decoratorBounds = new Rectangle(imageBounds.x + imageBounds.width - 13, 0,
				16, 16);

		/*
		 * Get the avatar image and create a default image if none was found
		 */
		image = vuzeBuddy.getAvatarImage();
		if (null == image) {
			image = ImageRepository.getImage("buddy_default_avatar");
		}

		sourceImageBounds = null == image ? null : image.getBounds();

		canvas.addPaintListener(new PaintListener() {

			public void paintControl(PaintEvent e) {

				if (fontDisplayName == null || fontDisplayName.isDisposed()) {
					fontDisplayName = Utils.getFontWithHeight(canvas.getFont(), e.gc, 10);
				}

				try {
					e.gc.setAntialias(SWT.ON);
					e.gc.setTextAntialias(SWT.ON);
					e.gc.setAlpha(alpha);
					e.gc.setInterpolation(SWT.HIGH);
				} catch (Exception ex) {
					// ignore.. some of these may not be avail
				}

				/*
				 * Draw background if the widget is selected
				 */
				if (true == isSelected) {

					e.gc.setBackground(Colors.grey);
					e.gc.setAlpha((int) (alpha * .5));

					Rectangle bounds = canvas.getBounds();
					e.gc.fillRoundRectangle(highlightBorder, highlightBorder,
							bounds.width - (2 * highlightBorder), bounds.height
									- (2 * highlightBorder), 10, 10);
					e.gc.setAlpha(alpha);
					e.gc.setBackground(canvas.getBackground());
				}

				/*
				 * Draw highlight borders if the widget is activated (being hovered over)
				 */
				if (true == isActivated && highlightBorder > 0) {

					e.gc.setForeground(Colors.grey);
					e.gc.setLineWidth(highlightBorder);
					Rectangle bounds = canvas.getBounds();
					e.gc.drawRoundRectangle(highlightBorder, highlightBorder,
							bounds.width - (2 * highlightBorder), bounds.height
									- (2 * highlightBorder), 10, 10);
					e.gc.setForeground(canvas.getForeground());
					e.gc.setLineWidth(1);
				}

				/*
				 * Draw the avatar image
				 */
				if (null == image || image.isDisposed()) {
					/*
					 * Paint nothing if the buddy has no avatar AND the default image is not found,
					 * OR the image has been disposed
					 */
				} else {
					if (true == viewer.isEditMode()) {
						e.gc.setAlpha((int) (alpha * .7));
						/*
						 * Image
						 */
						e.gc.drawImage(image, 0, 0, sourceImageBounds.width,
								sourceImageBounds.height, imageBounds.x, imageBounds.y,
								imageBounds.width, imageBounds.height);
						e.gc.setAlpha(alpha);
						/*
						 * Image border
						 */
						if (imageBorder > 0) {
							e.gc.setForeground(imageBorderColor);
							e.gc.drawRectangle(imageBounds.x - imageBorder, imageBounds.y
									- imageBorder, imageBounds.width + imageBorder,
									imageBounds.height + imageBorder);
							e.gc.setForeground(canvas.getForeground());
						}
					} else {
						/*
						 * Image
						 */
						e.gc.drawImage(image, 0, 0, sourceImageBounds.width,
								sourceImageBounds.height, imageBounds.x, imageBounds.y,
								imageBounds.width, imageBounds.height);
						/*
						 * Image border
						 */
						if (imageBorder > 0) {
							e.gc.setForeground(imageBorderColor);
							e.gc.drawRectangle(imageBounds.x - imageBorder, imageBounds.y
									- imageBorder, imageBounds.width + imageBorder,
									imageBounds.height + imageBorder);
							e.gc.setForeground(canvas.getForeground());
						}
					}
				}
				/*
				 * Draw decorator
				 */
				if (true == viewer.isEditMode()) {
					e.gc.drawImage(removeImage, 0, 0, removeImage.getBounds().width,
							removeImage.getBounds().height, decoratorBounds.x,
							decoratorBounds.y, decoratorBounds.width, decoratorBounds.height);
				} else if (true == viewer.isShareMode() && false == isSharedAlready()) {
					e.gc.drawImage(add_to_share_Image, 0, 0,
							removeImage.getBounds().width, removeImage.getBounds().height,
							decoratorBounds.x, decoratorBounds.y, decoratorBounds.width,
							decoratorBounds.height);
				}

				/*
				 * Draw the buddy display name
				 */

				if (null != textLinkColor && null != textColor) {
					if (true == nameLinkActive && true == isActivated) {
						e.gc.setForeground(textLinkColor);
					} else {
						e.gc.setForeground(textColor);
					}

					/*
					 * The multi-line display of name is disabled for now 
					 */
					//					int flags = SWT.CENTER | SWT.WRAP;
					//					GCStringPrinter stringPrinter = new GCStringPrinter(e.gc,
					//							vuzeBuddy.getDisplayName(), avatarNameBounds, false, true, flags);
					//					stringPrinter.calculateMetrics();
					//
					//					if (stringPrinter.isCutoff()) {
					//						e.gc.setFont(fontDisplayName);
					//						avatarNameBounds.height += 9;
					//						avatarNameBounds.y -= 4;
					//					}
					//					stringPrinter.printString(e.gc, avatarNameBounds, SWT.CENTER);
					//					e.gc.setFont(null);
					e.gc.setFont(fontDisplayName);
					GCStringPrinter.printString(e.gc, vuzeBuddy.getDisplayName(),
							nameAreaBounds, false, true, SWT.CENTER);

				}
			}
		});

		canvas.addMouseTrackListener(new MouseTrackListener() {

			public void mouseHover(MouseEvent e) {

			}

			public void mouseExit(MouseEvent e) {
				isActivated = false;
				canvas.redraw();
			}

			public void mouseEnter(MouseEvent e) {
				if (false == isActivated) {
					isActivated = true;
					canvas.redraw();
				}
			}
		});

		canvas.addMouseListener(new MouseListener() {

			public void mouseUp(MouseEvent e) {
			}

			public void mouseDown(MouseEvent e) {
				if (e.button != 1) {
					return;
				}
				if (true == nameAreaBounds.contains(e.x, e.y)
						&& e.stateMask != SWT.MOD1) {
					doLinkClicked();
				} else if (decoratorBounds.contains(e.x, e.y)) {
					if (true == viewer.isEditMode()) {
						doRemoveBuddy();
					} else if (true == viewer.isShareMode()) {
						doAddBuddyToShare();
					}
				} else {
					if (e.stateMask == SWT.MOD1) {
						viewer.select(vuzeBuddy, !isSelected, true);
					} else {
						viewer.select(vuzeBuddy, !isSelected, false);
					}
					canvas.redraw();
				}
			}

			public void mouseDoubleClick(MouseEvent e) {
			}
		});

		canvas.addMouseMoveListener(new MouseMoveListener() {
			private boolean lastActiveState = false;

			private String lastTooltipText = canvas.getToolTipText();

			public void mouseMove(MouseEvent e) {
				if (e.stateMask == SWT.MOD1) {
					return;
				}

				/*
				 * Optimization employed to minimize how often the tooltip text is updated;
				 * updating too frequently causes the tooltip to 'stick' to the cursor which
				 * can be annoying
				 */
				String tooltipText = "";
				if (decoratorBounds.contains(e.x, e.y)) {
					if (true == viewer.isEditMode()) {
						tooltipText = MessageText.getString("v3.buddies.remove");
					} else if (true == viewer.isShareMode() && false == isSharedAlready()) {
						tooltipText = MessageText.getString("v3.buddies.add.to.share");
					} else {
						tooltipText = getNameTooltip();
					}
				} else {
					tooltipText = getNameTooltip();
				}

				if (false == tooltipText.equals(lastTooltipText)) {
					canvas.setToolTipText(tooltipText);
					lastTooltipText = tooltipText;
				}

				if (true == nameAreaBounds.contains(e.x, e.y)) {
					if (false == lastActiveState) {
						nameLinkActive = true;
						canvas.redraw();
						lastActiveState = true;
					}
				} else {
					if (true == lastActiveState) {
						nameLinkActive = false;
						canvas.redraw();
						lastActiveState = false;
					}
				}

			}
		});

		initMenu();
	}

	private String getNameTooltip() {
		return vuzeBuddy.getDisplayName() + " (" + vuzeBuddy.getLoginID() + ")";
	}

	private void initMenu() {
		menu = new Menu(canvas);
		canvas.setMenu(menu);

		menu.addMenuListener(new MenuListener() {
			boolean bShown = false;

			public void menuHidden(MenuEvent e) {
				bShown = false;

				if (Constants.isOSX) {
					return;
				}

				// Must dispose in an asyncExec, otherwise SWT.Selection doesn't
				// get fired (async workaround provided by Eclipse Bug #87678)
				e.widget.getDisplay().asyncExec(new AERunnable() {
					public void runSupport() {
						if (bShown || menu.isDisposed()) {
							return;
						}
						MenuItem[] items = menu.getItems();
						for (int i = 0; i < items.length; i++) {
							items[i].dispose();
						}
					}
				});
			}

			public void menuShown(MenuEvent e) {
				MenuItem[] items = menu.getItems();
				for (int i = 0; i < items.length; i++) {
					items[i].dispose();
				}

				bShown = true;

				fillMenu(menu);
			}
		});
	}

	protected void fillMenu(Menu menu) {
		MenuItem item;

		item = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(item, "v3.buddy.menu.viewprofile");
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				AvatarWidget aw = (AvatarWidget) canvas.getData("AvatarWidget");
				if (aw != null) {
					aw.doLinkClicked();
				}
			}
		});

		if (Constants.isCVSVersion()) {
			MenuItem itemMenuDebug = new MenuItem(menu, SWT.CASCADE);
			itemMenuDebug.setText("Debug");
			Menu menuCVS = new Menu(menu);
			itemMenuDebug.setMenu(menuCVS);

			item = new MenuItem(menuCVS, SWT.PUSH);
			Messages.setLanguageText(item, "v3.buddy.menu.remove");
			item.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					AvatarWidget aw = (AvatarWidget) canvas.getData("AvatarWidget");
					if (aw != null) {
						doRemoveBuddy();
					}
				}
			});

			item = new MenuItem(menuCVS, SWT.PUSH);
			item.setText("Send Activity Message");
			item.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					if (!LoginInfoManager.getInstance().isLoggedIn()) {
						Utils.openMessageBox(null, SWT.ICON_ERROR, "No",
								"not logged in. no can do");
						return;
					}
					InputShell is = new InputShell("Moo", "Message:");
					String txt = is.open();
					if (txt != null) {
						txt = LoginInfoManager.getInstance().getUserInfo().userName
								+ " says: \n" + txt;
						VuzeActivitiesEntry entry = new VuzeActivitiesEntry(
								SystemTime.getCurrentTime(), txt, "Test");
						System.out.println("sending to " + vuzeBuddy.getDisplayName());
						try {
							vuzeBuddy.sendActivity(entry);
						} catch (NotLoggedInException e1) {
							Debug.out("Shouldn't Happen", e1);
						}
					}
				}
			});
		}
	}

	private void doRemoveBuddy() {
		MessageBox mBox = new MessageBox(parent.getShell(), SWT.ICON_QUESTION
				| SWT.YES | SWT.NO);
		mBox.setMessage("Really delete?");
		if (SWT.NO == mBox.open()) {
			return;
		}
		try {
			VuzeBuddyManager.removeBuddy(vuzeBuddy, true);
		} catch (NotLoggedInException e) {
			// should not happen, unless the user cancelled
			Debug.out(e);
		}
	}

	private void doAddBuddyToShare() {
		viewer.addToShare(this);
		sharedAlready = true;
		canvas.redraw();
		canvas.update();
	}

	public void doHover() {

	}

	public void doClick() {

	}

	public void doMouseEnter() {

	}

	public void doDoubleClick() {

	}

	public void doLinkClicked() {
		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (null != uiFunctions) {
			String url = getVuzeBuddy().getProfileUrl("buddy-bar");
			uiFunctions.viewURL(url, SkinConstants.VIEWID_BROWSER_BROWSE, 0, 0, true,
					true);
		}
	}

	public Control getControl() {
		return canvas;
	}

	public int getBorderWidth() {
		return highlightBorder;
	}

	public void setBorderWidth(int borderWidth) {
		this.highlightBorder = borderWidth;
	}

	public VuzeBuddySWT getVuzeBuddy() {
		return vuzeBuddy;
	}

	public boolean isSelected() {
		return isSelected;
	}

	public void setSelected(boolean isSelected) {
		this.isSelected = isSelected;
	}

	public void refreshVisual() {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (null != canvas && false == canvas.isDisposed()) {
					canvas.redraw();
				}
			}
		});

	}

	public Color getTextColor() {
		return textColor;
	}

	public void setTextColor(Color textColor) {
		this.textColor = textColor;
	}

	public Color getTextLinkColor() {
		return textLinkColor;
	}

	public void setTextLinkColor(Color textLinkColor) {
		this.textLinkColor = textLinkColor;
	}

	public void dispose(boolean animate) {
		if (null != canvas && false == canvas.isDisposed()) {
			if (true == animate) {
				parent.getDisplay().asyncExec(new AERunnable() {

					public void runSupport() {

						/*
						 * KN: TODO: disposal check is still not complete since it could still happen
						 * between the .isDisposed() check and the .redraw() or .update() calls.
						 */
						while (alpha > 20 && false == canvas.isDisposed()) {
							alpha -= 30;
							canvas.redraw();
							canvas.update();

							try {
								Thread.sleep(50);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}

						if (false == canvas.isDisposed()) {
							canvas.dispose();
							parent.layout(true);
						}
					}
				});
			} else {
				if (false == canvas.isDisposed()) {
					canvas.dispose();
					parent.layout(true);
				}
			}

		}
	}

	public boolean isSharedAlready() {
		return sharedAlready;
	}

	public void setSharedAlready(boolean sharedAlready) {
		this.sharedAlready = sharedAlready;
		refreshVisual();
	}

	public void setVuzeBuddy(VuzeBuddySWT vuzeBuddy) {
		if (null != vuzeBuddy) {
			this.vuzeBuddy = vuzeBuddy;

			/*
			 * Resets the image and image bounds since this is the only info cached;
			 * all other info is asked for on-demand so no need to update them 
			 */
			image = vuzeBuddy.getAvatarImage();
			if (null == image) {
				image = ImageRepository.getImage("buddy_default_avatar");
			}
			sourceImageBounds = null == image ? null : image.getBounds();
			refreshVisual();
		}
	}

	public Point getAvatarImageSize() {
		return imageSize;
	}

	public void setAvatarImageSize(Point avatarImageSize) {
		this.imageSize = avatarImageSize;
	}

	public Point getAvatarNameSize() {
		return nameAreaSize;
	}

	public void setAvatarNameSize(Point avatarNameSize) {
		this.nameAreaSize = avatarNameSize;
	}

	public Image getAvatarImage() {
		return image;
	}

	public void setAvatarImage(Image avatarImage) {
		this.image = avatarImage;
	}

	public Color getImageBorderColor() {
		return imageBorderColor;
	}

	public void setImageBorderColor(Color imageBorderColor) {
		this.imageBorderColor = imageBorderColor;
	}

	public int getAvatarImageBorder() {
		return imageBorder;
	}

	public void setAvatarImageBorder(int avatarImageBorder) {
		this.imageBorder = avatarImageBorder;
	}

	public int getImageBorder() {
		return imageBorder;
	}

	public void setImageBorder(int imageBorder) {
		this.imageBorder = imageBorder;
	}
}

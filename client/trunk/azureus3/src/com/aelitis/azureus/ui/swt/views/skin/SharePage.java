package com.aelitis.azureus.ui.swt.views.skin;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.ui.swt.ImageRepository;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;

import com.aelitis.azureus.buddy.VuzeBuddy;
import com.aelitis.azureus.buddy.impl.VuzeBuddyManager;
import com.aelitis.azureus.core.messenger.ClientMessageContext;
import com.aelitis.azureus.core.torrent.PlatformTorrentUtils;
import com.aelitis.azureus.ui.swt.browser.BrowserContext;
import com.aelitis.azureus.ui.swt.browser.listener.AbstractBuddyPageListener;
import com.aelitis.azureus.ui.swt.buddy.VuzeBuddySWT;
import com.aelitis.azureus.util.Constants;

public class SharePage
	extends AbstractDetailPage
{

	public static final String PAGE_ID = "share.page";

	private Composite content;

	private StackLayout stackLayout;

	private Composite firstPanel = null;

	private Composite browserPanel = null;

	private Label shareMessage;

	private Label buddyListDescription;

	private Label addBuddyLabel;

	private StyledText buddyList;

	private Composite inviteePanel;

	private StyledText inviteeList;

	private Composite contentDetail;

	private Button addBuddyButton;

	private Button sendNowButton;

	private Button cancelButton;

	private Label buddyImage;

	private Label commentLabel;

	private Text commentText;

	private Browser browser = null;

	private ClientMessageContext context = null;

	private List selectedBuddies = new ArrayList();

	private Map confirmationResponse = null;

	private DownloadManager dm = null;

	public SharePage(DetailPanel detailPanel) {
		super(detailPanel, PAGE_ID);
	}

	public void createControls(Composite parent) {
		content = new Composite(parent, SWT.NONE);

		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;
		content.setLayout(stackLayout);

		createFirstPanel();
		createBrowserPanel();
	}

	private void createFirstPanel() {
		firstPanel = new Composite(content, SWT.NONE);
		//		firstPanel.setBackground(ColorCache.getColor(parent.getDisplay(), 12, 30, 67));
		firstPanel.setLayout(new FormLayout());
		//		firstPanel.setBackground(Colors.black);

		shareMessage = new Label(firstPanel, SWT.NONE);
		shareMessage.setText("Share this content...");
		shareMessage.setForeground(Colors.white);

		buddyListDescription = new Label(firstPanel, SWT.NONE);
		buddyListDescription.setText("Selected buddies");
		buddyListDescription.setForeground(Colors.white);

		buddyList = new StyledText(firstPanel, SWT.BORDER);
		buddyList.setForeground(Colors.red);
		//============		
		inviteePanel = new Composite(firstPanel, SWT.BORDER);
		FormLayout fLayout = new FormLayout();
		fLayout.marginTop = 0;
		fLayout.marginBottom = 0;

		inviteePanel.setLayout(fLayout);

		inviteeList = new StyledText(inviteePanel, SWT.BORDER);
		inviteeList.setForeground(Colors.yellow);

		addBuddyLabel = new Label(inviteePanel, SWT.NONE | SWT.WRAP | SWT.RIGHT);
		addBuddyLabel.setText("Invite more buddies to share with");
		addBuddyLabel.setForeground(Colors.white);

		addBuddyButton = new Button(inviteePanel, SWT.PUSH);
		addBuddyButton.setText("Add Buddy");

		FormData inviteePanelData = new FormData();
		inviteePanelData.top = new FormAttachment(buddyList, 10);
		inviteePanelData.left = new FormAttachment(buddyList, 0, SWT.LEFT);
		inviteePanelData.right = new FormAttachment(buddyList, 0, SWT.RIGHT);
		inviteePanelData.height = 125;
		inviteePanel.setLayoutData(inviteePanelData);

		FormData inviteeListData = new FormData();
		inviteeListData.top = new FormAttachment(0, 0);
		inviteeListData.left = new FormAttachment(0, 0);
		inviteeListData.right = new FormAttachment(100, 0);
		inviteeListData.height = 75;
		inviteeList.setLayoutData(inviteeListData);

		FormData addBuddyButtonData = new FormData();
		addBuddyButtonData.top = new FormAttachment(inviteeList, 8);
		addBuddyButtonData.right = new FormAttachment(inviteeList, -8, SWT.RIGHT);
		addBuddyButton.setLayoutData(addBuddyButtonData);

		FormData addBuddyLabelData = new FormData();
		addBuddyLabelData.top = new FormAttachment(inviteeList, 8);
		addBuddyLabelData.right = new FormAttachment(addBuddyButton, -8);
		addBuddyLabelData.left = new FormAttachment(inviteeList, 0, SWT.LEFT);
		addBuddyLabel.setLayoutData(addBuddyLabelData);

		//==============

		contentDetail = new Composite(firstPanel, SWT.BORDER);

		sendNowButton = new Button(firstPanel, SWT.PUSH);
		sendNowButton.setText("Send Now");

		cancelButton = new Button(firstPanel, SWT.PUSH);
		cancelButton.setText("&Cancel");

		FormData shareMessageData = new FormData();
		shareMessageData.top = new FormAttachment(0, 8);
		shareMessageData.left = new FormAttachment(0, 8);
		shareMessageData.right = new FormAttachment(100, -8);
		shareMessage.setLayoutData(shareMessageData);

		FormData buddyListDescriptionData = new FormData();
		buddyListDescriptionData.top = new FormAttachment(shareMessage, 8);
		buddyListDescriptionData.left = new FormAttachment(buddyList, 0, SWT.LEFT);
		buddyListDescription.setLayoutData(buddyListDescriptionData);

		FormData buddyListData = new FormData();
		buddyListData.top = new FormAttachment(buddyListDescription, 0);
		buddyListData.left = new FormAttachment(0, 30);
		buddyListData.width = 200;
		buddyListData.height = 150;
		buddyList.setLayoutData(buddyListData);

		FormData contentDetailData = new FormData();
		contentDetailData.top = new FormAttachment(buddyList, 0, SWT.TOP);
		contentDetailData.left = new FormAttachment(buddyList, 30);
		contentDetailData.right = new FormAttachment(100, -8);
		contentDetailData.bottom = new FormAttachment(inviteePanel, 0, SWT.BOTTOM);
		contentDetail.setLayoutData(contentDetailData);

		FormData sendNowButtonData = new FormData();
		sendNowButtonData.top = new FormAttachment(contentDetail, 8);
		sendNowButtonData.right = new FormAttachment(contentDetail, 0, SWT.RIGHT);
		sendNowButton.setLayoutData(sendNowButtonData);

		FormData cancelButtonData = new FormData();
		cancelButtonData.right = new FormAttachment(sendNowButton, -8);
		cancelButtonData.top = new FormAttachment(contentDetail, 8);
		cancelButton.setLayoutData(cancelButtonData);

		FormLayout detailLayout = new FormLayout();
		detailLayout.marginWidth = 8;
		detailLayout.marginHeight = 8;
		contentDetail.setLayout(detailLayout);

		buddyImage = new Label(contentDetail, SWT.NONE);
		FormData buddyImageData = new FormData();
		buddyImageData.top = new FormAttachment(0, 8);
		buddyImageData.left = new FormAttachment(0, 8);
		buddyImageData.width = 100;
		buddyImageData.height = 100;
		buddyImage.setLayoutData(buddyImageData);

		commentLabel = new Label(contentDetail, SWT.NONE);
		commentLabel.setText("Optional message:");
		commentLabel.setForeground(Colors.white);
		FormData commentLabelData = new FormData();
		commentLabelData.top = new FormAttachment(buddyImage, 16);
		commentLabelData.left = new FormAttachment(0, 8);
		commentLabel.setLayoutData(commentLabelData);

		commentText = new Text(contentDetail, SWT.BORDER);
		FormData commentTextData = new FormData();
		commentTextData.top = new FormAttachment(commentLabel, 16);
		commentTextData.left = new FormAttachment(0, 8);
		commentTextData.right = new FormAttachment(100, -8);
		commentTextData.bottom = new FormAttachment(100, -8);
		commentText.setLayoutData(commentTextData);

		stackLayout.topControl = firstPanel;
		content.layout();

		hookListeners();

	}

	private void createBrowserPanel() {
		browserPanel = new Composite(content, SWT.NONE);
		FillLayout fLayout = new FillLayout();
		browserPanel.setLayout(fLayout);
		browser = new Browser(browserPanel, SWT.NONE);
		String url = Constants.URL_PREFIX + "share.start";
		browser.setUrl(url);

		/*
		 * Add the appropriate messaging listeners
		 */
		getMessageContext().addMessageListener(
				new AbstractBuddyPageListener(browser) {

					public void handleCancel() {
						System.out.println("'Cancel' called from share->invite buddy page");//KN: sysout
						activateFirstPanel();
					}

					public void handleClose() {
						System.out.println("'Close' called from share->invite buddy page");//KN: sysout
						activateFirstPanel();
					}

					public void handleBuddyInvites() {

						Utils.execSWTThread(new AERunnable() {
							public void runSupport() {
								inviteeList.setText("");
								for (Iterator iterator = getInvitedBuddies().iterator(); iterator.hasNext();) {
									VuzeBuddy buddy = (VuzeBuddy) iterator.next();
									inviteeList.append(buddy.getDisplayName() + "\n");
									System.out.println("Invited budy displayName: "
											+ buddy.getDisplayName() + " loginID: "
											+ buddy.getLoginID());//KN:
								}
								if (true == inviteeList.getCharCount() > 0) {
									addBuddyButton.setText("Add or Remove Buddy");
								} else {
									addBuddyButton.setText("Add Buddy");
								}
								inviteePanel.layout();
							}
						});

					}

					public void handleEmailInvites() {
						Utils.execSWTThread(new AERunnable() {
							public void runSupport() {
								for (Iterator iterator = getInvitedEmails().iterator(); iterator.hasNext();) {
									inviteeList.append(iterator.next() + "\n");//KN:
								}

								if (true == inviteeList.getCharCount() > 0) {
									addBuddyButton.setText("Add or Remove Buddy");
								} else {
									addBuddyButton.setText("Add Buddy");
								}

								inviteePanel.layout();
							}
						});

					}

					public void handleInviteConfirm() {
						confirmationResponse = getConfirmationResponse();

					}
				});
	}

	private void activateFirstPanel() {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				stackLayout.topControl = firstPanel;
				content.layout();
			}
		});

	}

	private void hookListeners() {

		addBuddyButton.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {
				stackLayout.topControl = browserPanel;
				content.layout();

			}

			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}
		});

		sendNowButton.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {
				//				String dummyBuddies

				getMessageContext().executeInBrowser("sendSharingBuddies('kkkkkkk')");

				getMessageContext().executeInBrowser("shareSubmit()");

				VuzeBuddy[] buddies = (VuzeBuddy[]) selectedBuddies.toArray(new VuzeBuddy[selectedBuddies.size()]);
				VuzeBuddyManager.inviteWithShare(confirmationResponse,
						getDownloadManager(), "share message goes here", buddies);

			}

			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}
		});

		cancelButton.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {

				/*
				 * Tell the browser that we're canceling so it can re-initialize it's states
				 */
				getMessageContext().executeInBrowser("initialize()");

				ButtonBar buttonBar = (ButtonBar) SkinViewManager.get(ButtonBar.class);
				if (null != buttonBar) {
					buttonBar.setActiveMode(ButtonBar.none_active_mode);
				}

				getDetailPanel().show(false);

			}

			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}
		});
	}

	public void setBuddies(List buddies) {
		selectedBuddies.clear();
		buddyList.setText("");
		for (Iterator iterator = buddies.iterator(); iterator.hasNext();) {
			Object vuzeBuddy = iterator.next();
			if (vuzeBuddy instanceof VuzeBuddySWT) {
				selectedBuddies.add(vuzeBuddy);
				buddyList.append(((VuzeBuddySWT) vuzeBuddy).getDisplayName() + "\n");
			} else {
				System.err.println("Bogus buddy: " + vuzeBuddy);//KN: sysout
			}
		}
	}

	public void addBuddy(VuzeBuddySWT vuzeBuddy) {
		if (false == selectedBuddies.contains(vuzeBuddy)) {
			selectedBuddies.add(vuzeBuddy);
			buddyList.append(vuzeBuddy.getDisplayName() + "\n");
			buddyList.layout();
		}
	}

	public Control getControl() {
		return content;
	}

	public ClientMessageContext getMessageContext() {
		if (null == context) {
			context = new BrowserContext("buddy-page-listener" + Math.random(),
					browser, null, true);
		}
		return context;
	}

	public void setDownloadManager(DownloadManager dm) {
		this.dm = dm;

		if (null != dm) {
			BuddiesViewer viewer = (BuddiesViewer) SkinViewManager.get(BuddiesViewer.class);
			if (null != viewer) {
				viewer.setShareMode(true);
			}
			getDetailPanel().show(true, PAGE_ID);
		}
	}

	public DownloadManager getDownloadManager() {
		return dm;
	}

	public void refresh() {
		BuddiesViewer viewer = (BuddiesViewer) SkinViewManager.get(BuddiesViewer.class);
		if (null != viewer) {
			setBuddies(viewer.getSelection());
		}

		if (null != dm && null != dm.getTorrent()) {
			Image img = null;

			byte[] imageBytes = PlatformTorrentUtils.getContentThumbnail(dm.getTorrent());
			if (null != imageBytes && imageBytes.length > 0) {
				ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
				img = new Image(Display.getDefault(), bis);

				/*
				 * Dispose this mage when the canvas is disposed
				 */
				final Image img_final = img;
				contentDetail.addDisposeListener(new DisposeListener() {

					public void widgetDisposed(DisposeEvent e) {
						if (null != img_final && false == img_final.isDisposed()) {
							img_final.dispose();
						}
					}
				});

			} else {
				String path = dm == null ? null
						: dm.getDownloadState().getPrimaryFile();
				if (path != null) {
					img = ImageRepository.getPathIcon(path, true, dm.getTorrent() != null
							&& !dm.getTorrent().isSimpleTorrent());
					/*
					 * DO NOT dispose the image from .getPathIcon()!!!!
					 */
				}
			}

			if (null != img) {
				Rectangle bounds = img.getBounds();

				if (null != buddyImage.getImage()
						&& false == buddyImage.getImage().isDisposed()) {
					Image image = buddyImage.getImage();
					buddyImage.setImage(null);
					image.dispose();
				}
				
				buddyImage.setImage(img);
				FormData fData = (FormData) buddyImage.getLayoutData();
				fData.height = bounds.height;
				fData.width = bounds.width;
				contentDetail.layout(true);
			} else {
				Debug.out("Problem getting image for torrent in SharePage.refresh()");
			}

		}
	}
}

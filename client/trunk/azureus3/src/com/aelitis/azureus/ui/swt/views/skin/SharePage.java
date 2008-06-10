package com.aelitis.azureus.ui.swt.views.skin;

import java.io.ByteArrayInputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.ui.swt.ImageRepository;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.progress.ProgressReportMessage;

import com.aelitis.azureus.buddy.VuzeBuddy;
import com.aelitis.azureus.buddy.impl.VuzeBuddyManager;
import com.aelitis.azureus.core.messenger.ClientMessageContext;
import com.aelitis.azureus.core.messenger.config.PlatformBuddyMessenger;
import com.aelitis.azureus.core.messenger.config.PlatformConfigMessenger;
import com.aelitis.azureus.core.torrent.PlatformTorrentUtils;
import com.aelitis.azureus.login.NotLoggedInException;
import com.aelitis.azureus.ui.selectedcontent.SelectedContent;
import com.aelitis.azureus.ui.swt.browser.BrowserContext;
import com.aelitis.azureus.ui.swt.browser.listener.AbstractBuddyPageListener;
import com.aelitis.azureus.ui.swt.browser.listener.AbstractStatusListener;
import com.aelitis.azureus.ui.swt.browser.listener.DisplayListener;
import com.aelitis.azureus.ui.swt.buddy.VuzeBuddySWT;
import com.aelitis.azureus.ui.swt.shells.StyledMessageWindow;
import com.aelitis.azureus.ui.swt.skin.SWTSkinFactory;
import com.aelitis.azureus.ui.swt.utils.ColorCache;
import com.aelitis.azureus.ui.swt.utils.ImageLoader;
import com.aelitis.azureus.ui.swt.utils.ImageLoaderFactory;
import com.aelitis.azureus.ui.swt.utils.SWTLoginUtils;
import com.aelitis.azureus.ui.swt.views.skin.widgets.BubbleButton;
import com.aelitis.azureus.ui.swt.views.skin.widgets.FlatButton;
import com.aelitis.azureus.ui.swt.views.skin.widgets.FriendsList;
import com.aelitis.azureus.ui.swt.views.skin.widgets.MiniCloseButton;
import com.aelitis.azureus.ui.swt.views.skin.widgets.SkinLinkLabel;
import com.aelitis.azureus.util.Constants;
import com.aelitis.azureus.util.FAQTopics;
import com.aelitis.azureus.util.ImageDownloader;
import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.ImageDownloader.ImageDownloaderListener;

public class SharePage
	extends AbstractDetailPage
{

	public static final String PAGE_ID = "share.page";

	private Composite content;

	private StackLayout stackLayout;

	private Composite firstPanel = null;

	private Composite browserPanel = null;

	private Label shareHeaderLabel;

	private Label shareHeaderMessageLabel;

	private Label buddyListDescription;

	private Label inviteeListDescription;

	private Label addBuddyPromptLabel;

	private FriendsList buddyList;

	private Composite inviteePanel;

	private FriendsList inviteeList;

	private Composite contentDetail;

	private StyledText contentStats;

	private FlatButton addBuddyButton;

	private BubbleButton sendNowButton;

	//	private BubbleButton previewButton;

	private BubbleButton cancelButton;

	private Label contentThumbnail;

	private Label optionalMessageLabel;

	private Label optionalMessageDisclaimerLabel;

	private SkinLinkLabel optionalMessageDisclaimerLinkLabel;

	private Text commentText;

	private Browser browser = null;

	private ClientMessageContext context = null;

	private Map confirmationResponse = null;

	private Color textColor = null;

	private Color textDarkerColor = null;

	private Color widgetBackgroundColor = null;

	private SelectedContent shareItem = null;

	private DownloadManager dm = null;

	private BuddiesViewer buddiesViewer;

	private MiniCloseButton miniCloseButton;

	private ButtonBar buttonBar;

	private String referer;

	private RefreshListener refreshListener;

	private AbstractBuddyPageListener buddyPageListener;

	public SharePage(DetailPanel detailPanel) {
		super(detailPanel, PAGE_ID);
	}

	public void createControls(Composite parent) {
		content = new Composite(parent, SWT.NONE);

		textColor = ColorCache.getColor(content.getDisplay(), 206, 206, 206);
		textDarkerColor = SWTSkinFactory.getInstance().getSkinProperties().getColor(
				"color.widget.heading");
		widgetBackgroundColor = SWTSkinFactory.getInstance().getSkinProperties().getColor(
				"color.widget.container.bg");

		buddiesViewer = (BuddiesViewer) SkinViewManager.get(BuddiesViewer.class);
		buttonBar = (ButtonBar) SkinViewManager.get(ButtonBar.class);

		createFirstPanel();
		createBrowserPanel();
	}

	private void createFirstPanel() {
		createControls();
		formatControls();
		layoutControls();
		hookListeners();
	}

	private void createControls() {

		firstPanel = new Composite(content, SWT.NONE);

		miniCloseButton = new MiniCloseButton(firstPanel);

		shareHeaderLabel = new Label(firstPanel, SWT.READ_ONLY);
		shareHeaderMessageLabel = new Label(firstPanel, SWT.READ_ONLY);
		buddyListDescription = new Label(firstPanel, SWT.READ_ONLY);
		inviteeListDescription = new Label(firstPanel, SWT.READ_ONLY);

		buddyList = new FriendsList(firstPanel);
		inviteePanel = new Composite(firstPanel, SWT.NONE);
		inviteeList = new FriendsList(inviteePanel);
		addBuddyPromptLabel = new Label(inviteePanel, SWT.NONE | SWT.WRAP
				| SWT.RIGHT);
		addBuddyButton = new FlatButton(inviteePanel);
		contentDetail = new Composite(firstPanel, SWT.NONE);
		sendNowButton = new BubbleButton(firstPanel);
		cancelButton = new BubbleButton(firstPanel);
		//		previewButton = new BubbleButton(firstPanel);
		contentThumbnail = new Label(contentDetail, SWT.NONE);
		contentStats = new StyledText(contentDetail, SWT.NONE);
		optionalMessageLabel = new Label(contentDetail, SWT.WRAP);
		optionalMessageDisclaimerLabel = new Label(firstPanel, SWT.NONE);
		commentText = new Text(contentDetail, SWT.WRAP);

		String url = Constants.URL_FAQ_BY_TOPIC_ENTRY
				+ FAQTopics.FAQ_TOPIC_WHAT_IS_SECURE_SHARING;
		optionalMessageDisclaimerLinkLabel = new SkinLinkLabel(firstPanel, url);

	}

	private void layoutControls() {

		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;
		content.setLayout(stackLayout);
		stackLayout.topControl = firstPanel;

		firstPanel.setLayout(new FormLayout());

		FormData miniCloseButtonData = new FormData();
		miniCloseButtonData.top = new FormAttachment(0, 10);
		miniCloseButtonData.right = new FormAttachment(100, -18);
		miniCloseButton.setLayoutData(miniCloseButtonData);

		FormData shareHeaderData = new FormData();
		shareHeaderData.top = new FormAttachment(0, 18);
		shareHeaderData.left = new FormAttachment(0, 28);
		shareHeaderLabel.setLayoutData(shareHeaderData);

		FormData shareHeaderMessageData = new FormData();
		shareHeaderMessageData.top = new FormAttachment(shareHeaderLabel, 8);
		shareHeaderMessageData.left = new FormAttachment(shareHeaderLabel, 0,
				SWT.LEFT);
		shareHeaderMessageData.right = new FormAttachment(100, -8);
		shareHeaderMessageLabel.setLayoutData(shareHeaderMessageData);

		FormData buddyListDescriptionData = new FormData();
		buddyListDescriptionData.top = new FormAttachment(shareHeaderMessageLabel,
				24);
		buddyListDescriptionData.left = new FormAttachment(shareHeaderLabel, 35,
				SWT.LEFT);
		buddyListDescription.setLayoutData(buddyListDescriptionData);

		FormData buddyListData = new FormData();
		buddyListData.top = new FormAttachment(buddyListDescription, 6);
		buddyListData.left = new FormAttachment(buddyListDescription, -2, SWT.LEFT);
		buddyListData.width = 300;
		buddyListData.height = 120;
		buddyList.getControl().setLayoutData(buddyListData);

		FormData inviteeListDescriptionData = new FormData();
		inviteeListDescriptionData.top = new FormAttachment(buddyList.getControl(),
				8);
		inviteeListDescriptionData.left = new FormAttachment(shareHeaderLabel, 35,
				SWT.LEFT);
		inviteeListDescription.setLayoutData(inviteeListDescriptionData);

		//============		
		FormLayout fLayout = new FormLayout();
		fLayout.marginTop = 0;
		fLayout.marginBottom = 0;
		fLayout.marginLeft = 0;
		fLayout.marginRight = 0;
		inviteePanel.setLayout(fLayout);

		FormData inviteePanelData = new FormData();
		inviteePanelData.top = new FormAttachment(inviteeListDescription, 6);
		inviteePanelData.left = new FormAttachment(buddyList.getControl(), 0,
				SWT.LEFT);
		inviteePanelData.right = new FormAttachment(buddyList.getControl(), 0,
				SWT.RIGHT);
		inviteePanel.setLayoutData(inviteePanelData);

		FormData inviteeListData = new FormData();
		inviteeListData.top = new FormAttachment(0, 0);
		inviteeListData.left = new FormAttachment(0, 0);
		inviteeListData.right = new FormAttachment(100, 0);
		inviteeListData.height = 0;
		inviteeList.getControl().setLayoutData(inviteeListData);

		FormData addBuddyButtonData = new FormData();
		addBuddyButtonData.top = new FormAttachment(inviteeList.getControl(), 8);
		addBuddyButtonData.bottom = new FormAttachment(100, -8);
		addBuddyButtonData.right = new FormAttachment(inviteeList.getControl(), -8,
				SWT.RIGHT);
		addBuddyButton.setLayoutData(addBuddyButtonData);

		FormData addBuddyLabelData = new FormData();
		addBuddyLabelData.top = new FormAttachment(inviteeList.getControl(), 8);
		addBuddyLabelData.bottom = new FormAttachment(100, -8);
		addBuddyLabelData.right = new FormAttachment(addBuddyButton, -8);
		addBuddyLabelData.left = new FormAttachment(0, 8);
		addBuddyPromptLabel.setLayoutData(addBuddyLabelData);

		//==============

		FormData contentDetailData = new FormData();
		contentDetailData.top = new FormAttachment(buddyList.getControl(), 0,
				SWT.TOP);
		contentDetailData.left = new FormAttachment(buddyList.getControl(), 16);
		contentDetailData.right = new FormAttachment(100, -58);
		contentDetailData.height = 259;
		contentDetail.setLayoutData(contentDetailData);

		FormLayout detailLayout = new FormLayout();
		detailLayout.marginWidth = 8;
		detailLayout.marginHeight = 8;
		contentDetail.setLayout(detailLayout);

		FormData buddyImageData = new FormData();
		buddyImageData.top = new FormAttachment(0, 8);
		buddyImageData.left = new FormAttachment(0, 8);
		buddyImageData.width = 142;
		buddyImageData.height = 82;
		contentThumbnail.setLayoutData(buddyImageData);

		FormData contentStatsData = new FormData();
		contentStatsData.top = new FormAttachment(0, 8);
		contentStatsData.left = new FormAttachment(contentThumbnail, 8);
		contentStatsData.right = new FormAttachment(100, -8);
		contentStatsData.bottom = new FormAttachment(contentThumbnail, 0,
				SWT.BOTTOM);
		contentStats.setLayoutData(contentStatsData);

		FormData commentLabelData = new FormData();
		commentLabelData.top = new FormAttachment(contentThumbnail, 16);
		commentLabelData.left = new FormAttachment(0, 8);
		optionalMessageLabel.setLayoutData(commentLabelData);

		FormData commentTextData = new FormData();
		commentTextData.top = new FormAttachment(optionalMessageLabel, 8);
		commentTextData.left = new FormAttachment(0, 8);
		commentTextData.right = new FormAttachment(100, -8);
		commentTextData.bottom = new FormAttachment(100, -8);
		commentText.setLayoutData(commentTextData);

		FormData disclaimerLabelData = new FormData();
		disclaimerLabelData.top = new FormAttachment(contentDetail, 6);
		disclaimerLabelData.left = new FormAttachment(contentDetail, 0, SWT.LEFT);
		optionalMessageDisclaimerLabel.setLayoutData(disclaimerLabelData);

		FormData disclaimerLinkLabelData = new FormData();
		disclaimerLinkLabelData.top = new FormAttachment(contentDetail, 6);
		disclaimerLinkLabelData.left = new FormAttachment(
				optionalMessageDisclaimerLabel, 6);
		optionalMessageDisclaimerLinkLabel.getControl().setLayoutData(
				disclaimerLinkLabelData);

		FormData sendNowButtonData = new FormData();
		sendNowButtonData.bottom = new FormAttachment(100, -24);
		sendNowButtonData.right = new FormAttachment(contentDetail, 0, SWT.RIGHT);
		sendNowButton.setLayoutData(sendNowButtonData);

		FormData cancelButtonData = new FormData();
		cancelButtonData.right = new FormAttachment(sendNowButton, -8);
		cancelButtonData.bottom = new FormAttachment(100, -24);
		cancelButton.setLayoutData(cancelButtonData);

		//		FormData previewButtonData = new FormData();
		//		previewButtonData.right = new FormAttachment(cancelButton, -8);
		//		previewButtonData.top = new FormAttachment(optionalMessageDisclaimerLabel,
		//				8);
		//		size = previewButton.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		//		previewButtonData.width = size.x;
		//		previewButtonData.height = size.y;
		//		previewButton.setLayoutData(previewButtonData);

		content.layout();
	}

	private void formatControls() {
		buddyListDescription.setForeground(textColor);
		Utils.setFontHeight(buddyListDescription, 10, SWT.BOLD);

		inviteeListDescription.setForeground(textColor);
		Utils.setFontHeight(inviteeListDescription, 10, SWT.BOLD);

		optionalMessageLabel.setForeground(textDarkerColor);
		//		Utils.setFontHeight(optionalMessageLabel, 8,SWT.NORMAL);

		optionalMessageDisclaimerLabel.setForeground(textDarkerColor);

		addBuddyPromptLabel.setForeground(textDarkerColor);

		contentStats.setForeground(textColor);

		commentText.setTextLimit(140);
		commentText.setBackground(ColorCache.getColor(content.getDisplay(), 153,
				153, 153));

		contentStats.getCaret().setVisible(false);
		contentStats.setEditable(false);

		inviteeList.setEmailDisplayOnly(true);

		shareHeaderMessageLabel.setForeground(textDarkerColor);

		shareHeaderLabel.setForeground(textColor);
		Utils.setFontHeight(shareHeaderLabel, 16, SWT.NORMAL);

		contentDetail.setBackground(widgetBackgroundColor);

		buddyList.getControl().setBackground(widgetBackgroundColor);
		buddyList.setBuddiesViewer(buddiesViewer);
		buddyList.setDefault_prompt_text(MessageText.getString("message.prompt.add.friends"));
		if (null == ImageRepository.getImage("buddy_prompt_image")) {
			ImageRepository.addPath(
					"com/aelitis/azureus/ui/images/buddy_prompt_image.png",
					"buddy_prompt_image");
		}
		buddyList.setDefault_prompt_image(ImageRepository.getImage("buddy_prompt_image"));

		inviteePanel.setBackground(widgetBackgroundColor);

		Messages.setLanguageText(addBuddyPromptLabel,
				"v3.Share.invite.buddies.prompt");

		Messages.setLanguageText(optionalMessageLabel, "v3.Share.optional.message");
		Messages.setLanguageText(optionalMessageDisclaimerLabel,
				"v3.Share.disclaimer");

		Messages.setLanguageText(optionalMessageDisclaimerLinkLabel.getControl(),
				"v3.Share.disclaimer.link");

		Messages.setLanguageText(shareHeaderLabel, "v3.Share.header");
		Messages.setLanguageText(shareHeaderMessageLabel, "v3.Share.header.message");
		Messages.setLanguageText(buddyListDescription,
				"v3.Share.add.buddy.existing");

		Messages.setLanguageText(inviteeListDescription, "v3.Share.add.buddy.new");

		addBuddyButton.setText(MessageText.getString("v3.Share.add.buddy"));

		ImageLoader imageLoader = ImageLoaderFactory.getInstance();
		addBuddyButton.setImage(imageLoader.getImage("image.buddy.add"));

		cancelButton.setText(MessageText.getString("v3.MainWindow.button.cancel"));

		//		previewButton.setText(MessageText.getString("v3.MainWindow.button.preview"));
		//		previewButton.setVisible(false);

		sendNowButton.setText(MessageText.getString("v3.Share.send.now"));
		sendNowButton.setEnabled(false);

	}

	private void createBrowserPanel() {
		browserPanel = new Composite(content, SWT.NONE);
		FillLayout fLayout = new FillLayout();
		browserPanel.setLayout(fLayout);

	}

	private void activateFirstPanel() {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				stackLayout.topControl = firstPanel;
				adjustLayout();
				buttonBar.enableShare(true);
			}
		});

	}

	private void hookListeners() {

		addBuddyButton.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(Event event) {
				stackLayout.topControl = browserPanel;
				content.layout(true, true);
				buttonBar.enableShare(false);
			}
		});

		cancelButton.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(Event event) {
				resetControls();
				getDetailPanel().show(false);
			}
		});

		miniCloseButton.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(Event event) {
				resetControls();
				getDetailPanel().show(false);
			}
		});

		//		previewButton.addListener(SWT.MouseDown, new Listener() {
		//			public void handleEvent(Event event) {
		//				getMessageContext().executeInBrowser(
		//						"sendSharingBuddies('" + getCommitJSONMessage() + "')");
		//
		//				getMessageContext().executeInBrowser("preview()");
		//
		//				stackLayout.topControl = browserPanel;
		//				content.layout();
		//			}
		//		});

		sendNowButton.addListener(SWT.MouseDown, new Listener() {
			public void handleEvent(Event event) {
				getMessageContext().executeInBrowser(
						"sendSharingBuddies('" + getCommitJSONMessage() + "')");

				getMessageContext().executeInBrowser(
						"setShareReferer('" + referer + "')");

				getMessageContext().executeInBrowser("shareSubmit()");

				// We'll get a buddy-page.invite-confirm message from the webpage,
				// even if it's just a share with no invites
			}
		});

	}

	private void showConfirmationDialog(List buddiesToShareWith) {

		if (null != buddyPageListener) {

			final String[] message = new String[1];
			final List messages = new ArrayList();

			if (null == buddiesToShareWith) {
				buddiesToShareWith = Collections.EMPTY_LIST;
			}

			/*
			 * Share only
			 */
			if (buddyPageListener.getInvitationsSent() == 0) {
				if (true == buddiesToShareWith.isEmpty()) {
					//Do nothing since there should at least be 1 friend to share with
				} else {
					/*
					 * The main message to display
					 */
					if (buddiesToShareWith.size() == 1) {
						message[0] = MessageText.getString("message.confirm.share.singular");
					} else {
						message[0] = MessageText.getString("message.confirm.share.plural");
					}

					/*
					 * The header in the detail section for existing friends
					 */
					String shareHeader = getShareItem().getDisplayName()
							+ " has been shared with:";
					messages.add(new ProgressReportMessage(shareHeader,
							ProgressReportMessage.MSG_TYPE_INFO));

					/*
					 * The existing friends
					 */
					for (Iterator iterator = buddiesToShareWith.iterator(); iterator.hasNext();) {
						VuzeBuddy buddy = (VuzeBuddy) iterator.next();
						messages.add(new ProgressReportMessage("\t"
								+ buddy.getDisplayName() + " (" + buddy.getLoginID() + ")",
								ProgressReportMessage.MSG_TYPE_INFO));
					}
				}
			}

			/*
			 * Share with invitations
			 */
			else {

				/*
				 * The main message to display
				 */
				if (buddiesToShareWith.size() + buddyPageListener.getInvitationsSent() == 1) {
					message[0] = MessageText.getString("message.confirm.share.invite.singular");
				} else {
					message[0] = MessageText.getString("message.confirm.share.invite.plural");
				}

				/*
				 * Existing friends
				 */
				if (true == buddiesToShareWith.isEmpty()) {
					//Do nothing for existing friends if there are none
				} else {

					/*
					 * The header in the detail section for existing friends
					 */
					String shareHeader = getShareItem().getDisplayName()
							+ " has been shared with:";
					messages.add(new ProgressReportMessage(shareHeader,
							ProgressReportMessage.MSG_TYPE_INFO));

					/*
					 * The existing friends
					 */
					for (Iterator iterator = buddiesToShareWith.iterator(); iterator.hasNext();) {
						VuzeBuddy buddy = (VuzeBuddy) iterator.next();
						messages.add(new ProgressReportMessage("\t"
								+ buddy.getDisplayName() + " (" + buddy.getLoginID() + ")",
								ProgressReportMessage.MSG_TYPE_INFO));
					}

					messages.add(new ProgressReportMessage("\n",
							ProgressReportMessage.MSG_TYPE_INFO));
				}

				/*
				 * New friends
				 */
				String invitationHeader = getShareItem().getDisplayName()
						+ " has been shared through invitations to the following:";
				messages.add(new ProgressReportMessage(invitationHeader,
						ProgressReportMessage.MSG_TYPE_INFO));

				messages.addAll(buddyPageListener.getConfirmationMessages());

			}

			Utils.execSWTThread(new AERunnable() {

				public void runSupport() {
					StyledMessageWindow messageWindow = new StyledMessageWindow(
							content.getShell(), 6, true);

					messageWindow.setDetailMessages(messages);
					messageWindow.setMessage(message[0]);

					messageWindow.setTitle("Share confirmation");
					messageWindow.setSize(400, 300);
					messageWindow.open();
				}
			});
		}
	}

	private void resetControls() {
		buddyList.clear();
		inviteeList.clear();
		if (null != buttonBar) {
			buttonBar.setActiveMode(BuddiesViewer.none_active_mode);
		}
		commentText.setText("");
	}

	private String getCommitJSONMessage() {
		if (null == shareItem || null == shareItem.getHash()) {
			return null;
		}
		List buddieloginIDsAndContentHash = new ArrayList();
		List loginIDs = new ArrayList();
		for (Iterator iterator = buddyList.getFriends().iterator(); iterator.hasNext();) {
			VuzeBuddySWT vuzeBuddy = (VuzeBuddySWT) iterator.next();
			loginIDs.add(vuzeBuddy.getLoginID());
		}
		buddieloginIDsAndContentHash.add(loginIDs);
		buddieloginIDsAndContentHash.add(shareItem.getHash());

		return JSONUtils.encodeToJSON(buddieloginIDsAndContentHash);
	}

	public void setBuddies(List buddies) {
		buddyList.clear();
		for (Iterator iterator = buddies.iterator(); iterator.hasNext();) {
			Object vuzeBuddy = iterator.next();
			if (vuzeBuddy instanceof VuzeBuddy) {
				buddyList.addFriend((VuzeBuddy) vuzeBuddy);
				adjustLayout();
			}
		}
	}

	private void adjustLayout() {

		if (buddyList.getContentCount() > 0 || inviteeList.getContentCount() > 0) {
			//			previewButton.setVisible(true);
			sendNowButton.setEnabled(true);
		} else {
			//			previewButton.setVisible(false);
			sendNowButton.setEnabled(false);
		}
		if (inviteeList.getContentCount() > 0) {
			showInviteeList(true);
			addBuddyButton.setText(MessageText.getString("v3.Share.add.edit.buddy"));
			Point size = addBuddyButton.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			FormData addBuddyButtonData = (FormData) addBuddyButton.getLayoutData();
			addBuddyButtonData.width = size.x;
			addBuddyButtonData.height = size.y;
		} else {
			showInviteeList(false);
			addBuddyButton.setText(MessageText.getString("v3.Share.add.buddy"));
			Point size = addBuddyButton.computeSize(SWT.DEFAULT, SWT.DEFAULT);
			FormData addBuddyButtonData = (FormData) addBuddyButton.getLayoutData();
			addBuddyButtonData.width = size.x;
			addBuddyButtonData.height = size.y;
		}

		content.layout(true, true);
	}

	public void addBuddies(List buddies) {
		for (Iterator iterator = buddies.iterator(); iterator.hasNext();) {
			Object object = (Object) iterator.next();
			if (object instanceof VuzeBuddySWT) {
				addBuddy((VuzeBuddySWT) object);
			}
		}
	}

	public void addBuddy(VuzeBuddySWT vuzeBuddy) {
		if (null == buddyList.findWidget(vuzeBuddy)) {
			buddyList.addFriend((VuzeBuddy) vuzeBuddy);
			adjustLayout();
		}
	}

	public void removeBuddy(VuzeBuddySWT vuzeBuddy) {
		if (null != buddyList.findWidget(vuzeBuddy)) {
			buddyList.removeFriend((VuzeBuddy) vuzeBuddy);
			adjustLayout();
		}
	}

	public Control getControl() {
		return content;
	}

	public ClientMessageContext getMessageContext() {
		if (null == context) {
			context = new BrowserContext("buddy-page-listener-share" + Math.random(),
					getBrowser(), null, true);

			context.addMessageListener(new DisplayListener(getBrowser()));

			/*
			 * Add listener to call the 'inviteFromShare' script; this listener is only called
			 * once whenever a web page is loaded the first time or when it's refreshed
			 */
			context.addMessageListener(new AbstractStatusListener("status") {
				public void handlePageLoadCompleted() {
					/*
					 * Setting inviteFromShare to true in the browser
					 */
					context.executeInBrowser("inviteFromShare(" + true + ")");

					if (null != refreshListener) {
						refreshListener.refreshCompleted();
					}
				}
			});

			/*
			 * Add the appropriate messaging listeners
			 */

			buddyPageListener = new AbstractBuddyPageListener(getBrowser()) {

				public void handleCancel() {
					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							activateFirstPanel();
						}
					});
				}

				public void handleClose() {
					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							activateFirstPanel();
						}
					});

				}

				public void handleBuddyInvites() {

					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							inviteeList.clear();
							for (Iterator iterator = getInvitedBuddies().iterator(); iterator.hasNext();) {
								VuzeBuddy buddy = (VuzeBuddy) iterator.next();
								inviteeList.addFriend(buddy);
							}
						}
					});

				}

				public void handleEmailInvites() {
					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							for (Iterator iterator = getInvitedEmails().iterator(); iterator.hasNext();) {
								VuzeBuddy buddy = VuzeBuddyManager.createPotentialBuddy();
								buddy.setLoginID((iterator.next()).toString());
								inviteeList.addFriend(buddy);
							}
						}
					});

				}

				public void handleInviteConfirm() {
					confirmationResponse = getConfirmationResponse();

					if (null != confirmationResponse) {
						final List buddiesToShareWith = buddyList.getFriends();
						final VuzeBuddy[] buddies = (VuzeBuddy[]) buddiesToShareWith.toArray(new VuzeBuddy[buddiesToShareWith.size()]);
						SWTLoginUtils.waitForLogin(new SWTLoginUtils.loginWaitListener() {
							public void loginComplete() {
								try {
									VuzeBuddyManager.inviteWithShare(confirmationResponse,
											getShareItem(), commentText.getText(), buddies);
									getDetailPanel().show(false);
									showConfirmationDialog(buddiesToShareWith);
									resetControls();

								} catch (NotLoggedInException e) {
									//Do nothing if login failed; leaves the Share page open... the user can then click cancel to dismiss or 
									// try again
								}
							}
						});
					}
				}

				public void handleResize() {
					if (null != getWindowState()) {
						System.out.println("Resizing embedded Add Friends: "
								+ getWindowState());//KN: sysout
					}
				}

			};
			context.addMessageListener(buddyPageListener);
		}
		return context;
	}

	private Browser getBrowser() {
		if (null == browser) {
			browser = new Browser(browserPanel, SWT.NONE);
			String url = Constants.URL_PREFIX + "share.start?ts=" + Math.random();
			browser.setUrl(url);

			/*
			 * Calling to initialize the listeners
			 */
			getMessageContext();

		}

		return browser;
	}

	public void setShareItem(SelectedContent content, String referer) {
		this.shareItem = content;
		this.referer = referer;
		this.dm = shareItem.getDM();

		if (SystemTime.getCurrentTime() - PlatformBuddyMessenger.getLastSyncCheck() > PlatformConfigMessenger.getBuddySyncOnShareMinTimeSecs() * 1000) {
			try {
				PlatformBuddyMessenger.sync(null);
			} catch (NotLoggedInException e) {
			}
		}

		if (content != null && content.getThumbURL() != null) {
			ImageDownloader.loadImage(content.getThumbURL(),
					new ImageDownloaderListener() {
						public void imageDownloaded(final byte[] image) {
							Utils.execSWTThread(new AERunnable() {
								public void runSupport() {
									if (contentThumbnail != null
											&& !contentThumbnail.isDisposed()) {
										ByteArrayInputStream bis = new ByteArrayInputStream(image);
										final Image img = new Image(Display.getDefault(), bis);
										if (img != null) {
											contentThumbnail.addDisposeListener(new DisposeListener() {
												public void widgetDisposed(DisposeEvent e) {
													if (img != null && !img.isDisposed()) {
														img.dispose();
													}
												}
											});
											contentThumbnail.setImage(img);
										}
									}
								}
							});
						}
					});
		}

		if (null != shareItem) {
			if (null != buttonBar) {
				buttonBar.setActiveMode(BuddiesViewer.share_mode);
			}
			getDetailPanel().show(true, PAGE_ID);
		}
	}

	public SelectedContent getShareItem() {
		return shareItem;
	}

	public void refresh(RefreshListener refreshListener) {

		this.refreshListener = refreshListener;

		/*
		 * Init the browser if it was not done already
		 */
		if (null == browser) {
			getBrowser();
		}
		browser.refresh();

		/*
		 * Setting the button bar to Share mode
		 */
		ButtonBar buttonBar = (ButtonBar) SkinViewManager.get(ButtonBar.class);
		if (null != buttonBar) {
			buttonBar.setActiveMode(BuddiesViewer.share_mode);
		}

		if (null != buddiesViewer) {
			setBuddies(buddiesViewer.getSelection());
			buddiesViewer.addSelectionToShare();
		}

		if (null != dm && null != dm.getTorrent()) {
			Image img = null;

			byte[] imageBytes = PlatformTorrentUtils.getContentThumbnail(dm.getTorrent());
			if (null != imageBytes && imageBytes.length > 0) {
				ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
				img = new Image(Display.getDefault(), bis);

				/*
				 * Dispose this image when the canvas is disposed
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
				contentThumbnail.setImage(img);
			} else {
				Debug.out("Problem getting image for torrent in SharePage.refresh()");
			}

		} else {
			contentThumbnail.setImage(null);
		}
		updateContentStats();

		adjustLayout();

	}

	private void updateContentStats() {
		contentStats.setText("");

		if (shareItem == null) {
			return;
		}

		contentStats.append(shareItem.getDisplayName() + "\n");

		if (null == dm) {
			return;
		}

		String publisher = PlatformTorrentUtils.getContentPublisher(dm.getTorrent());

		if (null != publisher && publisher.length() > 0) {
			if (publisher.startsWith("az")) {
				publisher = publisher.substring(2);
			}
			contentStats.append("From: " + publisher + "\n");
		}

		contentStats.append("Published: "
				+ DateFormat.getDateInstance().format(
						new Date(
								PlatformTorrentUtils.getContentLastUpdated(dm.getTorrent())))
				+ "\n");
		//		contentStats.append("Published: " + PlatformTorrentUtils.getContentLastUpdated(dm.getTorrent())   + "\n");
		contentStats.append("File size: " + dm.getSize() / 1000000 + " MB");
	}

	private void showInviteeList(boolean value) {
		int listHeight = 0;
		if (true == value) {
			listHeight = 76;
		}

		Object layoutData = inviteeList.getControl().getLayoutData();
		if (layoutData instanceof FormData) {
			FormData fData = (FormData) layoutData;
			if (fData.height != listHeight) {
				fData.height = listHeight;
			}
		}

	}
}

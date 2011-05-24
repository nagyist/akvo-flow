package org.waterforpeople.mapping.portal.client.widgets;

import org.waterforpeople.mapping.app.gwt.client.util.TextConstants;
import org.waterforpeople.mapping.portal.client.widgets.component.MessageViewWidget;

import com.gallatinsystems.framework.gwt.util.client.MessageDialog;
import com.gallatinsystems.user.app.gwt.client.PermissionConstants;
import com.gallatinsystems.user.app.gwt.client.UserDto;
import com.google.gwt.core.client.GWT;

public class MessageViewPortlet extends UserAwarePortlet {
	
	private static TextConstants TEXT_CONSTANTS = GWT
	.create(TextConstants.class);

	public static final String NAME = TEXT_CONSTANTS.messageViewPortletTitle();
	private static final int WIDTH = 800;
	private static final int HEIGHT = 800;

	public MessageViewPortlet(UserDto user) {
		super(NAME, true, false, false, WIDTH, HEIGHT, user);

		if (user.hasPermission(PermissionConstants.VIEW_MESSAGES)) {
			MessageViewWidget managerWidget = new MessageViewWidget();
			setContent(managerWidget);
		} else {
			MessageDialog errDia = new MessageDialog(TEXT_CONSTANTS.adminOnly(),TEXT_CONSTANTS.adminOnlyMessage());					
			errDia.show();
		}
	}

	@Override
	public String getName() {
		return NAME;
	}

}

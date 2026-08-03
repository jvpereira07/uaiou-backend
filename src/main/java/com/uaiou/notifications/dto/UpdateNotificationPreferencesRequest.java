package com.uaiou.notifications.dto;

/** {@code PUT /me/notification-preferences}. Canal ausente permanece como estava. */
public record UpdateNotificationPreferencesRequest(Channels channels) {

  public record Channels(Boolean push, Boolean email, Boolean sms) {}
}

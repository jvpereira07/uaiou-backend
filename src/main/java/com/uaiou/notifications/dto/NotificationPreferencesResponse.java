package com.uaiou.notifications.dto;

import java.util.List;
import java.util.Map;

/**
 * RF-08.9 — {@code mandatory} lista os tipos que o usuário NÃO consegue desligar, para a interface
 * mostrar o cadeado em vez de oferecer um botão que não funciona.
 */
public record NotificationPreferencesResponse(
    Map<String, Boolean> channels, List<String> mandatory) {}

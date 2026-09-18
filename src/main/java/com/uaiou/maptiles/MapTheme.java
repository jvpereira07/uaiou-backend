package com.uaiou.maptiles;

import java.util.Locale;
import java.util.Optional;

/**
 * Os dois temas do mapa de navegação. O cliente pede pelo nome do tema, nunca pelo id do estilo no
 * provedor: trocar o estilo escolhido é mudança de configuração, e não pode quebrar app já
 * instalado.
 */
public enum MapTheme {
  LIGHT,
  DARK;

  public String slug() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Optional<MapTheme> fromSlug(String slug) {
    for (MapTheme theme : values()) {
      if (theme.slug().equals(slug)) {
        return Optional.of(theme);
      }
    }
    return Optional.empty();
  }
}

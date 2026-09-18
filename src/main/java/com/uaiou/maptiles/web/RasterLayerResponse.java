package com.uaiou.maptiles.web;

/**
 * Camada raster pronta para o Leaflet e o {@code flutter_map}.
 *
 * @param urlTemplate absoluto, com {@code {z}/{x}/{y}{r}} e o token do dia.
 * @param attribution exigência de licença: vai junto do mapa.
 */
public record RasterLayerResponse(String urlTemplate, int maxZoom, String attribution) {}

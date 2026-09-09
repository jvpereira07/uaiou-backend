package com.uaiou.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do administrador criado no primeiro boot de um banco vazio. Existem porque nenhuma
 * migration insere dados (o seed de {@code db/seed} só roda no perfil {@code dev}) e o
 * autorregistro recusa {@code ADMIN} — sem isto, um ambiente novo sobe sem nenhuma forma de entrar
 * no painel.
 *
 * <p>Os defaults são deliberadamente triviais para que um ambiente local suba sem configuração. Em
 * produção eles são credenciais conhecidas publicamente: trocar a senha no primeiro acesso é parte
 * de subir o serviço, não uma recomendação opcional.
 */
@ConfigurationProperties(prefix = "app.admin-bootstrap")
public record AdminBootstrapProperties(String email, String password, String login, String nivel) {}

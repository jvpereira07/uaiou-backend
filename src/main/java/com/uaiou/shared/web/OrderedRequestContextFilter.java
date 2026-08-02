package com.uaiou.shared.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.RequestContextFilter;

/**
 * Sem isto, um bean de escopo de requisição (ex.: {@code CurrentUserHolder}) não é utilizável de
 * dentro de um {@code Filter}: {@code DispatcherServlet} só vincula o request ao {@code
 * RequestContextHolder} na própria invocação do servlet — que roda DEPOIS de toda a cadeia de
 * filtros. {@code JwtAuthenticationFilter} precisa desse vínculo antes disso, para popular o
 * usuário autenticado.
 *
 * <p>Subclasse só para poder anotar {@code @Order} na classe, no mesmo padrão de {@link
 * CorrelationIdFilter}/{@link RequestLoggingFilter} — mais confiável do que depender de
 * {@code @Order} num método {@code @Bean} ser propagado corretamente pela coleta de filtros do
 * Spring Boot.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OrderedRequestContextFilter extends RequestContextFilter {}

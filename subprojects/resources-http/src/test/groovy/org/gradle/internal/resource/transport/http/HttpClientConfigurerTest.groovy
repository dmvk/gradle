/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.resource.transport.http

import org.apache.http.auth.AuthScope
import org.apache.http.impl.client.HttpClientBuilder
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.internal.Factory
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.resource.UriResource
import spock.lang.Specification

import javax.net.ssl.SSLContext

public class HttpClientConfigurerTest extends Specification {
    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
    PasswordCredentials credentials = Mock()
    AllSchemesAuthentication authentication = Mock() {
        getCredentials() >> credentials
    }
    HttpSettings httpSettings = Mock()
    HttpProxySettings proxySettings = Mock()
    Factory<SSLContext> sslContextFactory = Mock()
    HttpClientConfigurer configurer = new HttpClientConfigurer(httpSettings)

    def "configures http client with no credentials or proxy"() {
        httpSettings.authenticationSettings >> []
        httpSettings.proxySettings >> proxySettings
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)

        then:
        !httpClientBuilder.retryHandler.retryRequest(new IOException(), 1, null)
    }

    def "configures http client with proxy credentials"() {
        httpSettings.authenticationSettings >> []
        httpSettings.proxySettings >> proxySettings
        httpSettings.sslContextFactory >> sslContextFactory
        proxySettings.proxy >> new HttpProxySettings.HttpProxy("host", 1111, "domain/proxyUser", "proxyPass")

        when:
        configurer.configure(httpClientBuilder)

        then:
        def proxyCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope("host", 1111))
        proxyCredentials.userPrincipal.name == "domain/proxyUser"
        proxyCredentials.password == "proxyPass"

        and:
        def ntlmCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope("host", 1111, AuthScope.ANY_REALM, "ntlm"))
        ntlmCredentials.userPrincipal.name == 'DOMAIN\\proxyUser'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'proxyUser'
        ntlmCredentials.password == 'proxyPass'
        ntlmCredentials.workstation != ''
    }

    def "configures http client with credentials"() {
        httpSettings.authenticationSettings >> [authentication]
        credentials.username >> "domain/user"
        credentials.password >> "pass"
        httpSettings.proxySettings >> proxySettings
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)

        then:
        def basicCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT))
        basicCredentials.userPrincipal.name == "domain/user"
        basicCredentials.password == "pass"

        and:
        def ntlmCredentials = httpClientBuilder.credentialsProvider.getCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "ntlm"))
        ntlmCredentials.userPrincipal.name == 'DOMAIN\\user'
        ntlmCredentials.domain == 'DOMAIN'
        ntlmCredentials.userName == 'user'
        ntlmCredentials.password == 'pass'
        ntlmCredentials.workstation != ''

        and:
        httpClientBuilder.requestFirst[0] instanceof HttpClientConfigurer.PreemptiveAuth
    }

    def "configures http client with user agent"() {
        httpSettings.authenticationSettings >> []
        httpSettings.proxySettings >> proxySettings
        httpSettings.sslContextFactory >> sslContextFactory

        when:
        configurer.configure(httpClientBuilder)

        then:
        httpClientBuilder.userAgent == UriResource.userAgentString
    }

    def "configures http client with custom ssl context"() {
        def sslContext = Mock(SSLContext)
        httpSettings.authenticationSettings >> []
        httpSettings.proxySettings >> proxySettings
        httpSettings.sslContextFactory >> sslContextFactory
        sslContextFactory.create() >> sslContext

        when:
        configurer.configure(httpClientBuilder)

        then:
        httpClientBuilder.sslcontext == sslContext
    }
}

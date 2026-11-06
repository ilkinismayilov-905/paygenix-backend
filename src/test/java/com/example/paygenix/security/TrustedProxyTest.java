package com.example.paygenix.security;

import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// T-076: X-Forwarded-* is believed only from the proxies this pattern matches (Tomcat RemoteIpValve).
class TrustedProxyTest extends IntegrationTest {

    @Value("${server.forward-headers-strategy}") String strategy;
    @Value("${server.tomcat.remoteip.internal-proxies}") String trusted;

    @Test
    void onlyLoopbackAndPrivateNetworksAreTrustedByDefault() {
        assertThat(strategy).isEqualTo("native");
        Pattern p = Pattern.compile(trusted);
        assertThat(p.matcher("127.0.0.1").matches()).isTrue();
        assertThat(p.matcher("172.18.0.5").matches()).isTrue();   // docker compose network
        assertThat(p.matcher("10.1.2.3").matches()).isTrue();
        assertThat(p.matcher("192.168.1.10").matches()).isTrue();
        assertThat(p.matcher("8.8.8.8").matches()).isFalse();
        assertThat(p.matcher("172.32.0.1").matches()).isFalse();
        assertThat(p.matcher("1.10.0.0.1").matches()).isFalse();
    }
}

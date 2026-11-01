package com.example.paygenix.entity;

import com.example.paygenix.support.IntegrationTest;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// T-094: a lazy Hibernate proxy and a loaded copy of the same row are the same user.
class UserProxyEqualityTest extends IntegrationTest {

    @Test
    void proxyEqualsLoadedUser() {
        User loaded = userRepository.findById(createUser().getId()).orElseThrow();

        transactionTemplate.executeWithoutResult(tx -> {
            User proxy = userRepository.getReferenceById(loaded.getId());

            assertThat(proxy).isInstanceOf(HibernateProxy.class);
            assertThat(proxy.equals(loaded)).isTrue();
            assertThat(loaded.equals(proxy)).isTrue();
            assertThat(proxy.hashCode()).isEqualTo(loaded.hashCode());
        });
    }
}

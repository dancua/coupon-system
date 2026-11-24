package io.github.dancua.coupon.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;

/**
 * 통합 테스트용 실제 MySQL / Redis 컨테이너.
 * {@code @ServiceConnection}이 컨테이너의 접속 정보를 datasource / redis 설정에 자동으로 연결한다.
 * 직접 @Import하지 말고 {@link IntegrationTestSupport}를 상속해서 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(MySqlContainerConfig.class)
public class TestcontainersConfig {

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>("redis:7.4").withExposedPorts(6379);
	}
}

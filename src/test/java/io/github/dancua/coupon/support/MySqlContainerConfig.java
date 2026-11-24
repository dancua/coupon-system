package io.github.dancua.coupon.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 실제 MySQL 컨테이너. JPA만 검증하는 @DataJpaTest는 이것만 가져다 쓴다 (Redis 컨테이너를 띄우지 않기 위해).
 * <p>
 * H2는 행 락, 트랜잭션 격리, unique 제약 위반 시 동작이 MySQL과 달라서, 결과를 믿으려면 운영과 같은 DB에서 검증해야 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlContainerConfig {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer("mysql:8.4");
	}
}

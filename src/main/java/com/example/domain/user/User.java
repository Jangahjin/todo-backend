package com.example.domain.user;

import com.example.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * PRD 8.2 users 테이블. password는 소셜 전용 가입자의 경우 NULL이다.
 * provider는 최초 가입 수단을 뜻하며 이후 다른 방식으로 로그인해도 덮어쓰지 않는다 (PRD 13.1).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE users SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column
	private String password;

	@Column(length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "provider_id")
	private String providerId;

	public User(String email, String password, String name, AuthProvider provider, String providerId) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.provider = provider;
		this.providerId = providerId;
	}
}

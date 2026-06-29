package com.email.declutter_ai.settings;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppParameterRepository extends JpaRepository<AppParameter, Long> {
	Optional<AppParameter> findByAccountEmailAndKey(String accountEmail, String key);
}

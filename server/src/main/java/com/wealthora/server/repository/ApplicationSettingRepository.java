package com.wealthora.server.repository;

import com.wealthora.server.domain.ApplicationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSettingRepository
        extends JpaRepository<ApplicationSetting, String> {
}

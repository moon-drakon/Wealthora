package com.wealthora.server.service;

import com.wealthora.server.config.RegistrationProperties;
import com.wealthora.server.domain.ApplicationSetting;
import com.wealthora.server.repository.ApplicationSettingRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationSettingsService {

    public static final String REGISTRATION_APPROVAL =
            "REGISTRATION_REQUIRES_ADMIN_APPROVAL";
    private final ApplicationSettingRepository settings;
    private final RegistrationProperties registrationProperties;

    public ApplicationSettingsService(
            ApplicationSettingRepository settings,
            RegistrationProperties registrationProperties) {
        this.settings = settings;
        this.registrationProperties = registrationProperties;
    }

    @Transactional(readOnly = true)
    public boolean requiresAdminApproval() {
        return settings.findById(REGISTRATION_APPROVAL)
                .map(ApplicationSetting::getValue)
                .map(Boolean::parseBoolean)
                .orElse(registrationProperties.requiresAdminApproval());
    }

    @Transactional
    public void setRequiresAdminApproval(
            boolean required, UUID actor, Instant now) {
        ApplicationSetting setting = settings.findById(REGISTRATION_APPROVAL)
                .orElseGet(() -> new ApplicationSetting(
                        REGISTRATION_APPROVAL, Boolean.toString(required),
                        now, actor));
        setting.update(Boolean.toString(required), now, actor);
        settings.save(setting);
    }
}

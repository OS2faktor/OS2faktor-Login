#ifndef DIGITAL_IDENTITY_PASSWORD_FILTER_SERVICE_HPP
#define DIGITAL_IDENTITY_PASSWORD_FILTER_SERVICE_HPP

#include "./export.hpp"

#include <memory>

#include <httplib.h>
#include <optional>
#include <spdlog/logger.h>

#include <expected>
#include <string>

#include <windows.h>
#include <winternl.h>


namespace pf {
struct PASSWORDVALIDATIONFILTER_API PasswordFilterService {
public:
    PasswordFilterService() = delete;
    PasswordFilterService(PasswordFilterService &&) = default;
    PasswordFilterService(const PasswordFilterService &) = delete;
    PasswordFilterService &operator=(const PasswordFilterService &) = delete;
    PasswordFilterService &operator=(PasswordFilterService &&) = delete;

    ~PasswordFilterService();

    [[nodiscard]]
    static auto create(std::shared_ptr<spdlog::logger> logger) noexcept 
        -> std::expected<PasswordFilterService, std::string>;

    auto validate_password(PUNICODE_STRING account_name, 
                           PUNICODE_STRING password, 
                           bool user_self_changed_password) const -> bool;

private:
    PasswordFilterService(const std::string& os2faktor_base_url,
                          const std::string& api_key,
                          std::shared_ptr<spdlog::logger> logger,
                          std::optional<std::string> version)
    : os2faktor_base_url_(os2faktor_base_url), 
      api_key_(api_key),
      logger_(logger),
      version_(version) {}

    auto handle_response_(const httplib::Response& response) const noexcept -> bool;

private:
    std::shared_ptr<spdlog::logger> logger_;
    std::string os2faktor_base_url_;
    std::string api_key_;
    std::optional<std::string> version_;
};
}

#endif

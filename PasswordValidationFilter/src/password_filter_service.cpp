#include "./password_filter_service.hpp"

#include "./registry.hpp"
#include "./request.hpp"

#include <spdlog/spdlog.h>

#include <format>
#include <chrono>
#include <windows.h>

using namespace std::chrono_literals;

namespace pf {

PasswordFilterService::~PasswordFilterService() {
    SecureZeroMemory(api_key_.data(), api_key_.size()); 
}

auto PasswordFilterService::create(std::shared_ptr<spdlog::logger> logger) noexcept 
    -> std::expected<PasswordFilterService, std::string> {
    auto base_hkey_result = registry::open_hkey(registry::BASE_REGISTRY_PATH);
    if(!base_hkey_result) {
        return std::unexpected(std::format("Failed to open registry key, error code: {}", 
                                           base_hkey_result.error()));
    }

    const auto base_hkey = std::move(base_hkey_result.value());

    auto os2faktor_base_url_result = registry::get_ansi_string(base_hkey, 
                                                               L"os2faktorBaseUrl");
    if(!os2faktor_base_url_result) {
        return std::unexpected(std::format("Failed to get os2faktor base url, error code: {}",
                    os2faktor_base_url_result.error()));
    }

    SPDLOG_DEBUG("OS2faktor base url \"{}\"", os2faktor_base_url_result.value());

    auto api_key_result = registry::get_ansi_string(base_hkey, L"clientApiKey");
    if(!api_key_result) {
        return std::unexpected(std::format("Failed to get client api key, error code: {}",
                    api_key_result.error()));
    }

    // We dont care about the error code but we need it optionally
    auto version = registry::get_ansi_string(base_hkey, L"version")
        .transform([](std::string value) -> std::optional<std::string> {
            return value;
        })
        .value_or(std::nullopt);

    SPDLOG_DEBUG("client api key \"{}\"", api_key_result.value());

    SPDLOG_DEBUG("PasswordFilterService initialized successfully");
    return PasswordFilterService{os2faktor_base_url_result.value(), api_key_result.value(), logger, version};
}

auto PasswordFilterService::validate_password(const PUNICODE_STRING account_name, 
                                              const PUNICODE_STRING password, 
                                              const bool user_self_changed_password) const -> bool {
    auto client = httplib::Client(os2faktor_base_url_);
#ifndef _DEBUG
    client.enable_server_certificate_verification(true);
#endif
    client.set_default_headers({{"apiKey", api_key_}});
    client.set_max_timeout(3s);

    const httplib::Params params {
        {"userSelfChangedPassword", user_self_changed_password ? "true" : "false" }
    };

    const auto path = httplib::append_query_params("/api/password/filter/v1/validate",
                                                   params);
    const auto body_result = pf::create_request(account_name, password, version_);
    if(!body_result) {
        logger_->error("Failed to create json body");
        return TRUE;
    }

    auto body = std::move(body_result.value());

    try {
        SPDLOG_DEBUG("Sending request to \"{}\"", os2faktor_base_url_ + path);
        if(auto resp = client.Post(path, body, "application/json")) {
            SecureZeroMemory(body.data(), body.size());
            return handle_response_(resp.value());
        } else {
            logger_->error("Call to API failed with error: {}", 
                          httplib::to_string(resp.error()));
        }
    } catch(...) {
        SecureZeroMemory(body.data(), body.size());
        throw;
    }

    SecureZeroMemory(body.data(), body.size());

    return TRUE;
}

auto PasswordFilterService::handle_response_(const httplib::Response& response)
    const noexcept -> bool {
    switch(response.status) {
        case httplib::StatusCode::OK_200:
            return TRUE;
        case httplib::StatusCode::Conflict_409:
            return FALSE;
        default:
            logger_->error("API returned unexpected HTTP status: {}", 
                          response.status);
    }

    return TRUE;
}
}

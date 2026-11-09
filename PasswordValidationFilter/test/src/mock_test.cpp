#include <windows.h>

#include "../../src/password_filter_service.hpp"

#include <spdlog/spdlog.h>

#include <catch2/catch_test_macros.hpp>

TEST_CASE("Mock Password filter") {
    auto result = pf::PasswordFilterService::create(spdlog::default_logger());
    if(!result) {
        spdlog::error("{}", result.error());
    }

    constexpr WCHAR account_name_text[] = L"Test mand";
    UNICODE_STRING account_name = {
        .Length = sizeof(account_name_text),
        .MaximumLength = sizeof(account_name_text),
        .Buffer = const_cast<PWSTR>(account_name_text),
    };

    constexpr WCHAR password_text[] = L"Test1234";
    UNICODE_STRING password = {
        .Length = sizeof(password_text),
        .MaximumLength = sizeof(password_text),
        .Buffer = const_cast<PWSTR>(password_text),
    };

    result->validate_password(&account_name, &password, true);
}

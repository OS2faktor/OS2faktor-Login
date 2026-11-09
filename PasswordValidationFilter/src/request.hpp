#ifndef DIGITAL_IDENTITY_PASSWORD_FILTER_JSON_HPP
#define DIGITAL_IDENTITY_PASSWORD_FILTER_JSON_HPP

#include <expected>
#include <string>
#include <windows.h>
#include <winternl.h>

#include <optional>

namespace pf {
auto create_request(PUNICODE_STRING account_name, 
                    PUNICODE_STRING password,
                    std::optional<std::string> version) 
    -> std::expected<std::string, NTSTATUS>; 
}

#endif

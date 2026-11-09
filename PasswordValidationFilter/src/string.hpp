#ifndef DIGITAL_IDENTITY_PASSWORD_FILTER_STRING_HPP
#define DIGITAL_IDENTITY_PASSWORD_FILTER_STRING_HPP

#include "./export.hpp"

#include <memory_resource>
#include <string>
#include <expected>

#include <windows.h>
#include <winternl.h>

namespace pf::string {
auto to_wide(const std::string& ansi) -> std::expected<std::wstring, LSTATUS>;

auto to_ansi(const std::wstring& str) -> std::expected<std::string, LSTATUS>;

/**
 * Convert a PUNICODE_STRING to an ANSI_STRING.
 *
 * Allocates ANSI_STRING::Buffer with new[].
 * The caller must free the buffer with delete[] when done.
 *
 * @param unicode The input unicode string to convert.
 * @return ANSI_STRING on success, std::nullopt on conversion failure.
 */
PASSWORDVALIDATIONFILTER_API
auto to_ansi(PCUNICODE_STRING unicode) -> std::expected<std::string, NTSTATUS>;
}

#endif

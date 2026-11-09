#ifndef DIGITAL_IDENTITY_PASSWORD_FILTER_REGISTRY_HPP
#define DIGITAL_IDENTITY_PASSWORD_FILTER_REGISTRY_HPP

#include "./string.hpp"
#include <expected>
#include <memory>
#include <string>

#include <vector>
#include <windows.h>

namespace pf::registry {
enum class OpenMode {
    READ = KEY_READ,
    WRITE = KEY_WRITE
};

enum class RootHKey {
    LOCAL_MACHINE
};

struct HKeyDeleter {
    auto operator()(HKEY ptr) const noexcept -> void {
        if (ptr) RegCloseKey(ptr);
    }
};

using HKey = std::unique_ptr<std::remove_pointer_t<HKEY>, HKeyDeleter>;

constexpr wchar_t BASE_REGISTRY_PATH[] = L"SOFTWARE\\DigitalIdentity\\OS2faktorPasswordFilter";

[[nodiscard]] 
auto open_hkey(const std::wstring& sub_key,
               const RootHKey root_hkey = RootHKey::LOCAL_MACHINE,
               const OpenMode open_mode = OpenMode::READ) noexcept 
    -> std::expected<HKey, LSTATUS>; 

[[nodiscard]]
auto get_string(const HKey& hkey, 
                const std::wstring& name) noexcept 
    -> std::expected<std::wstring, LSTATUS>;

[[nodiscard]]
auto get_ansi_string(const HKey& hkey, 
                     const std::wstring& name) noexcept 
    -> std::expected<std::string, LSTATUS>; 

[[nodiscard]]
 auto get_bool(const HKey& hkey, 
               const std::wstring& name) noexcept 
     -> std::expected<bool, LSTATUS>;
}

#endif

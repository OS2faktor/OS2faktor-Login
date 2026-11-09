#include "./registry.hpp"

#include <optional>

namespace {
[[nodiscard]]
auto convert_root_hkey_to_hkey(const pf::registry::RootHKey root_hkey) noexcept
    -> std::optional<HKEY> {
    switch(root_hkey) {
        case pf::registry::RootHKey::LOCAL_MACHINE:
            return HKEY_LOCAL_MACHINE;
        default:
            return std::nullopt;
    }
}
}

namespace pf::registry {
auto open_hkey(const std::wstring& sub_key,
               const RootHKey root_hkey,
               const OpenMode open_mode) noexcept 
    -> std::expected<HKey, LSTATUS> {
    const auto hkey = convert_root_hkey_to_hkey(root_hkey);
    if(!hkey.has_value()) {
        return std::unexpected(ERROR_INVALID_PARAMETER);
    }

    HKEY out_hkey = nullptr;
    if(const auto error = RegOpenKeyExW(hkey.value(),
                                        sub_key.c_str(),
                                        0,
                                        static_cast<long>(open_mode),
                                        &out_hkey);
        error != ERROR_SUCCESS) {
        return std::unexpected(error);
    }

    if(out_hkey == nullptr) {
        return std::unexpected(ERROR_NO_DATA);
    }

    return HKey(out_hkey);
}

auto get_string(const HKey& hkey, const std::wstring& name) noexcept 
    -> std::expected<std::wstring, LSTATUS> {
    DWORD data_size = 0;
    if(const auto err = RegQueryValueExW(hkey.get(),
                                  name.c_str(),
                                  0,
                                  nullptr,
                                  nullptr,
                                  &data_size);
        err != ERROR_SUCCESS) {
        return std::unexpected(err);
    }
    
    if(data_size == 0) {
        return std::unexpected(ERROR_NO_DATA);
    }

    std::vector<wchar_t> data_out(data_size / sizeof(wchar_t));
    DWORD type = 0;
    if(const auto err = RegQueryValueExW(hkey.get(),
            name.c_str(),
            0,
            &type,
            reinterpret_cast<LPBYTE>(data_out.data()),
            &data_size);
        err != ERROR_SUCCESS) {
        return std::unexpected(err);
    }

    if(type != REG_SZ && type != REG_EXPAND_SZ) {
        return std::unexpected(ERROR_INVALID_DATA);
    }

    return std::wstring{data_out.data(), data_out.size() - 1};
}

auto get_ansi_string(const HKey& hkey, const std::wstring& name) noexcept 
    -> std::expected<std::string, LSTATUS> {
    return get_string(hkey, name)
        .and_then([](auto&& value) { return string::to_ansi(value); });
}

auto get_bool(const HKey& hkey, 
              const std::wstring& name) noexcept 
    -> std::expected<bool, LSTATUS> {
    DWORD dw_value = 0;
    DWORD dw_size = sizeof(DWORD);
    DWORD type = 0;
    if(const auto error = RegQueryValueExW(hkey.get(),
                                   name.c_str(), 
                                   0, 
                                   &type, 
                                   reinterpret_cast<LPBYTE>(&dw_value),
                                   &dw_size);
        error != ERROR_SUCCESS) {
        return std::unexpected(error);
    }

    if((dw_value != 1 && dw_value != 0) || type != REG_DWORD) {
        return std::unexpected(ERROR_INVALID_DATA);
    }

    return static_cast<bool>(dw_value);
}
}

#include "./string.hpp"

#include <expected>
#include <vector>
#include <windows.h>

namespace pf::string {
auto to_wide(const std::string& ansi) -> std::expected<std::wstring, LSTATUS> {
    if(ansi.empty()) {
        return {};
    }

    const auto wide_size = MultiByteToWideChar(CP_UTF8,
                                               0,
                                               ansi.c_str(),
                                               ansi.size(),
                                               nullptr,
                                               0); 

    if(wide_size == 0) {
        return std::unexpected(GetLastError());
    }

    std::vector<wchar_t> wide_string(wide_size);
    if(const auto err = MultiByteToWideChar(CP_UTF8, 
                                      0, 
                                      ansi.c_str(), 
                                      ansi.size(), 
                                      &wide_string[0], 
                                      wide_size); 
            err == 0) {
        return std::unexpected(GetLastError());
    }

    auto result = std::wstring(wide_string.data(), wide_string.size());

    SecureZeroMemory(wide_string.data(), wide_string.size() * sizeof(wchar_t));

    return result;
}

auto to_ansi(const std::wstring& str) -> std::expected<std::string, LSTATUS> {
    if(str.empty()) {
        return {};
    }

    const auto ansi_size = WideCharToMultiByte(CP_UTF8,
                                               0,
                                               str.data(),
                                               str.size(),
                                               nullptr,
                                               0,
                                               nullptr,
                                               nullptr);

     if(ansi_size == 0) {
        return std::unexpected(GetLastError());
    }

    std::vector<char> ansi_string(ansi_size);
    if(const auto err = WideCharToMultiByte(CP_UTF8, 
                                            0, 
                                            str.data(), 
                                            str.size(), 
                                            ansi_string.data(), 
                                            ansi_string.size(), 
                                            nullptr, 
                                            nullptr); 
            err == 0) {
        return std::unexpected(GetLastError());
    }

    auto result = std::string{ansi_string.data(), ansi_string.size()};

    SecureZeroMemory(ansi_string.data(), ansi_string.size());

    return result;
}

auto to_ansi(PCUNICODE_STRING unicode) -> std::expected<std::string, NTSTATUS> {
    if(unicode == nullptr || (unicode->Length != 0 && unicode->Buffer == nullptr)) {
        return std::unexpected(STATUS_INVALID_PARAMETER);
    }

    if(unicode->Length == 0) {
        return {};
    }

    const auto ansi_size = WideCharToMultiByte(CP_UTF8,
                                               0,
                                               unicode->Buffer,
                                               unicode->Length / sizeof(wchar_t),
                                               nullptr,
                                               0,
                                               nullptr,
                                               nullptr);

     if(ansi_size == 0) {
        return std::unexpected(GetLastError());
    }

    std::vector<char> ansi_string(ansi_size);
    if(const auto err = WideCharToMultiByte(CP_UTF8, 
                                            0, 
                                            unicode->Buffer, 
                                            unicode->Length / sizeof(wchar_t), 
                                            ansi_string.data(), 
                                            ansi_string.size(), 
                                            nullptr, 
                                            nullptr); 
        err == 0) {
        return std::unexpected(GetLastError());
    }

    auto result = std::string{ansi_string.data(), ansi_string.size()};
    
    SecureZeroMemory(ansi_string.data(), ansi_string.size());

    return result;
}
}

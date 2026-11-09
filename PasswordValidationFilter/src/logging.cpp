#include "./logging.hpp"

#include "./registry.hpp"
#include "./event_viewer_log_sink.hpp"

#include <memory>
#include <spdlog/sinks/basic_file_sink.h>
#include <spdlog/sinks/stdout_color_sinks.h>

namespace pf {
auto setup_logger() -> std::expected<std::shared_ptr<spdlog::logger>, std::string> {
    auto base_hkey_result = registry::open_hkey(registry::BASE_REGISTRY_PATH);
    if(!base_hkey_result) {
        return std::unexpected(std::format("Failed to open registry key, error code: {}", 
                                           base_hkey_result.error()));
    }

    const auto base_hkey = std::move(base_hkey_result.value());

    std::vector<spdlog::sink_ptr> sinks {};
    sinks.reserve(2);

    if(auto log_path_result = registry::get_ansi_string(base_hkey, L"CallbackLogPath")) {
        if(!log_path_result->empty()) {
            SPDLOG_DEBUG("Enabled logging to file \"{}\"", log_path_result.value());
            sinks.push_back(std::make_shared<spdlog::sinks::basic_file_sink_mt>(log_path_result.value()));
        }
    }

    if(registry::get_bool(base_hkey, L"EnableEventViewerLogging").value_or(false)) {
        SPDLOG_DEBUG("Enabled event viewer logging \"{}\"", string::to_ansi(std::wstring{EVENT_VIEWER_PASSWORD_FILTER_LOG_NAME})
                .value_or(""));
        sinks.push_back(std::make_shared<event_viewer_sink_mt>());
    }

    return std::make_shared<spdlog::logger>("PasswordFilterLogger", sinks.begin(), sinks.end());
}
}
